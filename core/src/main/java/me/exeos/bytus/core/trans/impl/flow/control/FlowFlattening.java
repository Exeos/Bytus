package me.exeos.bytus.core.trans.impl.flow.control;

import me.exeos.bytus.asmplus.analysis.flow.FlowAnalyzer;
import me.exeos.bytus.asmplus.analysis.flow.block.BasicBlock;
import me.exeos.bytus.asmplus.analysis.flow.block.impl.FallTroughBlock;
import me.exeos.bytus.asmplus.analysis.flow.block.impl.JumpBlock;
import me.exeos.bytus.asmplus.analysis.flow.block.impl.SwitchBlock;
import me.exeos.bytus.asmplus.analysis.flow.block.impl.TerminalBlock;
import me.exeos.bytus.asmplus.codegen.lookupswitch.LookupSwitchGenerator;
import me.exeos.bytus.asmplus.codegen.lookupswitch.SwitchCase;
import me.exeos.bytus.asmplus.utils.InsnUtil;
import me.exeos.bytus.asmplus.utils.MethodUtil;
import me.exeos.bytus.core.config.BytusConfig;
import me.exeos.bytus.core.trans.AbstractTransformer;
import me.exeos.bytus.core.trans.Pipeline;
import me.exeos.bytus.core.trans.Priority;
import me.exeos.bytus.core.trans.context.MethodContext;
import me.exeos.bytus.core.utils.RandomUtil;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.util.*;

/**
 * Control-flow flattening transformer that rewrites a method into a dispatcher loop
 *
 * <p>Overview:
 * <ol>
 *   <li>Analyze method into {@link BasicBlock}s.</li>
 *   <li>Remove all original instructions.</li>
 *   <li>Create a new instruction list that:
 *     <ol>
 *       <li>initializes a state local to the entry state's value</li>
 *       <li>runs an infinite dispatch loop:
 *         {@code while(true) switch(state) { ... }}</li>
 *     </ol>
 *   </li>
 * </ol>
 *
 * <p>Implementation details:
 * <ul>
 *   <li>Each block is assigned a chain of unique "path states": the first element is the
 *       path entry for that block, and the last element is the block’s handler state.</li>
 *   <li>Block transitions are implemented by storing the next block’s path entry state into
 *       {@code state} and jumping back to the dispatcher entry.</li>
 * </ul>
 */
public class FlowFlattening extends AbstractTransformer {

    public FlowFlattening(Pipeline pipeline, BytusConfig config) {
        super(pipeline, config);
    }

    @Override
    public boolean applies() {
        return config.flow.controlFlow().enable();
    }

    @Override
    public int priority() {
        return Priority.FLOW_CTRL_FLATTENING;
    }

    @Override
    public void transform(MethodContext context) {
        MethodNode methodNode = context.methodNode();

        if (methodNode.instructions.size() == 0 || !methodNode.tryCatchBlocks.isEmpty()) {
            return;
        }

        List<BasicBlock> blocks = FlowAnalyzer.getBasicBlocks(methodNode);
        if (blocks.isEmpty()) {
            return;
        }

        // shuffle blocks, keep first at same pos
        BasicBlock first = blocks.getFirst();
        Collections.shuffle(blocks);
        blocks.remove(first);
        blocks.addFirst(first);

        // locals mapping needs to be removed as it will be invalid
        methodNode.localVariables = null;

        // Remove all original instructions. We rebuild method from scratch.
        for (AbstractInsnNode insn : methodNode.instructions.toArray()) {
            methodNode.instructions.remove(insn);
        }

        Map<BasicBlock, int[]> blockPathMap = genBlockKeys(blocks);
        int stateVarIndex = methodNode.maxLocals++;

        InsnList flattened = new InsnList();

        // Initialize state to the first block's path entry
        flattened.add(InsnUtil.getIntPush(blockPathMap.get(blocks.getFirst())[0]));
        flattened.add(new VarInsnNode(Opcodes.ISTORE, stateVarIndex));

        LabelNode dispatcherEntry = new LabelNode();
        flattened.add(dispatcherEntry);
        flattened.add(new VarInsnNode(Opcodes.ILOAD, stateVarIndex));
        flattened.add(createDispatcher(blocks, blockPathMap, stateVarIndex, dispatcherEntry));

        // replace method instructions with flattened instructions
        methodNode.instructions = flattened;
    }

    /**
     * Creates dispatcher {@code lookupswitch} that maps any valid state to:
     * <ul>
     *   <li>either a "path step" (advance state along a chain)</li>
     *   <li>or the final handler for a given basic block</li>
     * </ul>
     *
     * <p>Default case ends method by throwing (see {@link MethodUtil#endMethodByThrow()}). This should never be reached.</p>
     */
    private InsnList createDispatcher(List<BasicBlock> blocks, Map<BasicBlock, int[]> blockPathMap, int stateVarIndex, LabelNode dispatcherEntry) {
        List<SwitchCase> cases = new ArrayList<>();

        for (BasicBlock block : blocks) {
            InsnList handlerInsns = new InsnList();
            switch (block) {
                // updates
                case JumpBlock jumpBlock -> {
                    // Copy original block instructions, excluding the block's own dispatcher.
                    InsnUtil.addToInsnList(jumpBlock.instructions, handlerInsns);
                    handlerInsns.remove(jumpBlock.dispatcher);

                    if (jumpBlock.falseBranchBlock.isPresent()) {
                        LabelNode newTrueBranch = new LabelNode();

                        handlerInsns.add(new JumpInsnNode(jumpBlock.dispatcher.getOpcode(), newTrueBranch));
                        handlerInsns.add(updateStateMachine(blockPathMap.get(jumpBlock.falseBranchBlock.get())[0], stateVarIndex, dispatcherEntry));
                        handlerInsns.add(newTrueBranch);
                    }

                    handlerInsns.add(updateStateMachine(blockPathMap.get(jumpBlock.trueBranchBlock)[0], stateVarIndex, dispatcherEntry));
                }
                case SwitchBlock switchBlock -> {
                    List<SwitchCase> innerHandlers = new ArrayList<>();

                    // Convert original cases to cases only updating state machine to their target block's path entry
                    switch (switchBlock.dispatcher) {
                        case LookupSwitchInsnNode ls -> {
                            for (int i = 0; i < ls.labels.size(); i++) {
                                int originalCaseKey = ls.keys.get(i);
                                BasicBlock targetBlock = switchBlock.keyCaseMap.get(originalCaseKey);

                                InsnList innerHandler = new InsnList();
                                innerHandler.add(updateStateMachine(blockPathMap.get(targetBlock)[0], stateVarIndex, dispatcherEntry));
                                innerHandlers.add(new SwitchCase(originalCaseKey, innerHandler));
                            }
                        }
                        case TableSwitchInsnNode ts -> {
                            for (int i = 0; i < ts.labels.size(); i++) {
                                int originalCaseKey = ts.min + i;
                                BasicBlock targetBlock = switchBlock.keyCaseMap.get(originalCaseKey);

                                InsnList innerHandler = new InsnList();
                                innerHandler.add(updateStateMachine(blockPathMap.get(targetBlock)[0], stateVarIndex, dispatcherEntry));
                                innerHandlers.add(new SwitchCase(originalCaseKey, innerHandler));
                            }
                        }
                        default -> throw new IllegalStateException("Invalid dispatcher instruction for SwitchBlock");
                    }

                    // Create default case, updating the state machine to the actual default case block.
                    InsnList defaultCaseInsn = new InsnList();
                    defaultCaseInsn.add(updateStateMachine(blockPathMap.get(switchBlock.defaultBlock)[0], stateVarIndex, dispatcherEntry));
                    SwitchCase defaultCase = new SwitchCase(0, defaultCaseInsn);

                    // original instructions up to the switch
                    InsnUtil.addToInsnList(switchBlock.instructions, handlerInsns);
                    // remove original switch
                    handlerInsns.remove(switchBlock.dispatcher);
                    // replace original switch
                    handlerInsns.add(LookupSwitchGenerator.gen(innerHandlers, defaultCase, false));
                }
                case FallTroughBlock fallTroughBlock -> {
                    InsnUtil.addToInsnList(block.instructions, handlerInsns);
                    handlerInsns.add(updateStateMachine(blockPathMap.get(fallTroughBlock.fallTroughBlock)[0], stateVarIndex, dispatcherEntry));
                }
                case TerminalBlock terminalBlock -> {
                    // Ends method. Just copy insn.
                    InsnUtil.addToInsnList(block.instructions, handlerInsns);
                }
                default -> {
                    throw new IllegalStateException("Invalid block at index: " + blocks.indexOf(block));
                }
            }

            // Add path chain of this block
            // entry -> chain -> chain -> .. -> handler
            int[] blockPath = blockPathMap.get(block);
            for (int i = 0; i < blockPath.length - 1; i++) {
                InsnList pathInsn = new InsnList();
                pathInsn.add(updateStateMachine(blockPath[i + 1], stateVarIndex, dispatcherEntry));
                cases.add(new SwitchCase(blockPath[i], pathInsn));
            }

            // Final case in the chain (the actual block handler)
            cases.add(new SwitchCase(blockPath[blockPath.length - 1], handlerInsns));
        }

        return LookupSwitchGenerator.gen(cases, new SwitchCase(0, MethodUtil.endMethodByThrow()), false);
    }

    /**
     * Emits instructions that:
     * <ol>
     *   <li>store {@code state} into {@code stateLocalIndex}</li>
     *   <li>jump to {@code dispatcherEntry}</li>
     * </ol>
     */
    private InsnList updateStateMachine(int state, int stateVar, LabelNode dispatcherEntry) {
        InsnList instructions = new InsnList();

        instructions.add(InsnUtil.getIntPush(state));
        instructions.add(new VarInsnNode(Opcodes.ISTORE, stateVar));
        instructions.add(new JumpInsnNode(Opcodes.GOTO, dispatcherEntry));

        return instructions;
    }

    /**
     * Assigns each {@link BasicBlock} an array of unique, random "path states".
     *
     * <p>The intent is to avoid a simple 1-to-1:
     * the dispatcher will have to traverse multiple states before reaching the final block handler.</p>
     *
     * @param blocks blocks to assign paths to
     * @return mapping block -> array of path states (first is entry state for that block)
     */
    private Map<BasicBlock, int[]> genBlockKeys(List<BasicBlock> blocks) {
        Map<BasicBlock, int[]> result = new HashMap<>();
        Set<Integer> usedKeys = new HashSet<>();

        for (BasicBlock block : blocks) {
            int[] pathKeys = new int[RandomUtil.getInt(Math.max(1, config.flow.controlFlow().minDispatcherChainLength()), Math.max(1, config.flow.controlFlow().maxDispatcherChainLength() + 1))];

            for (int i = 0; i < pathKeys.length; i++) {
                do {
                    pathKeys[i] = RandomUtil.nextInt();
                } while (usedKeys.contains(pathKeys[i]));
                usedKeys.add(pathKeys[i]);
            }

            result.put(block, pathKeys);
        }

        return result;
    }
}
