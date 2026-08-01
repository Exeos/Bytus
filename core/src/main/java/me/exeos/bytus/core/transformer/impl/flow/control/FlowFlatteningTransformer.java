package me.exeos.bytus.core.transformer.impl.flow.control;

import me.exeos.asmplus.analysis.flow.FlowAnalyzer;
import me.exeos.asmplus.analysis.flow.block.BasicBlock;
import me.exeos.asmplus.analysis.flow.block.impl.FallTroughBlock;
import me.exeos.asmplus.analysis.flow.block.impl.JumpBlock;
import me.exeos.asmplus.analysis.flow.block.impl.SwitchBlock;
import me.exeos.asmplus.analysis.flow.block.impl.TerminalBlock;
import me.exeos.asmplus.codegen.xswitch.SwitchCase;
import me.exeos.asmplus.codegen.xswitch.impl.LookupSwitchGenerator;
import me.exeos.asmplus.utils.InsnUtil;
import me.exeos.asmplus.utils.MethodUtil;
import me.exeos.bytus.core.config.BytusConfig;
import me.exeos.bytus.core.transformer.AbstractTransformer;
import me.exeos.bytus.core.transformer.Priority;
import me.exeos.bytus.core.transformer.context.MethodContext;
import me.exeos.bytus.core.transformer.extensions.MethodExtension;
import me.exeos.bytus.core.utils.RandomUtil;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;

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
public class FlowFlatteningTransformer extends AbstractTransformer {

    public FlowFlatteningTransformer(BytusConfig config) {
        super(config);
    }

    @Override
    public boolean applies() {
        return config.flow.controlFlow().enable() || true;
    }

    @Override
    public int priority() {
        return Priority.FLOW_CTRL_FLATTENING;
    }

    @Override
    public void transform(MethodContext context) {
        MethodNode methodNode = context.methodNode();
        MethodExtension methodExtension = context.getExtension();
        if (!methodNode.tryCatchBlocks.isEmpty()) {
            return;
        }

        Analyzer<BasicValue> analyzer = new Analyzer<>(new BasicInterpreter());
        Frame<BasicValue>[] frames;
        try {
            frames = analyzer.analyze(context.ownerCtx().classNode().name, methodNode);
        } catch (AnalyzerException e) {
            System.out.println("Failed to analyze stack for: " + context.ownerCtx().classNode().name + "." + methodNode.name);
            return;
        }

        List<BasicBlock> blocks = FlowAnalyzer.getBasicBlocks(methodNode, true);
        if (blocks.isEmpty()) {
            return;
        }

        List<AbstractInsnNode> insnList = new ArrayList<>();
        for (BasicBlock block : blocks) {
            insnList.addAll(block.instructions);
        }

        // shuffle blocks, keep first at same pos
        BasicBlock first = blocks.getFirst();
        Collections.shuffle(blocks);
        blocks.remove(first);
        blocks.addFirst(first);

        // locals mapping needs to be removed as it will be invalid
        methodNode.localVariables = null;

        Map<BasicBlock, int[]> blockPathMap = genBlockKeys(blocks);
        int stateVarIndex = methodNode.maxLocals++;

        InsnList flattened = new InsnList();

        // Initialize state to the first block's path entry
        flattened.add(methodExtension.getObfuscatedIntPush(
                blockPathMap.get(blocks.getFirst())[0]
        ));
        flattened.add(new VarInsnNode(Opcodes.ISTORE, stateVarIndex));

        LabelNode dispatcherEntry = new LabelNode();
        flattened.add(dispatcherEntry);
        flattened.add(new VarInsnNode(Opcodes.ILOAD, stateVarIndex));
        flattened.add(createDispatcher(insnList, blocks, blockPathMap, stateVarIndex, dispatcherEntry, methodExtension, frames));

        MethodUtil.removeAllInsn(methodNode);

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
    private InsnList createDispatcher(List<AbstractInsnNode> methodNode, List<BasicBlock> blocks, Map<BasicBlock, int[]> blockPathMap, int stateVarIndex, LabelNode dispatcherEntry, MethodExtension methodExtension, Frame<BasicValue>[] frames) {
        List<SwitchCase> cases = new ArrayList<>();

        Map<BasicBlock, LabelNode> blockLables = new HashMap<>();
        for (BasicBlock block : blocks) {
            blockLables.put(block, new LabelNode());
        }

        for (BasicBlock block : blocks) {
            InsnList handlerInsns = new InsnList();
            handlerInsns.add(blockLables.get(block));

            switch (block) {
                // updates
                case JumpBlock jumpBlock -> {
                    // Copy original block instructions, excluding the block's own dispatcher.
                    InsnUtil.addToInsnList(jumpBlock.instructions, handlerInsns);
                    handlerInsns.remove(jumpBlock.dispatcher);

                    if (jumpBlock.falseBranchBlock.isPresent()) {
                        LabelNode newTrueBranch = new LabelNode();

                        handlerInsns.add(new JumpInsnNode(jumpBlock.dispatcher.getOpcode(), newTrueBranch));
                        handlerInsns.add(updateStateMachine(jumpBlock.falseBranchBlock.get(), blockPathMap.get(jumpBlock.falseBranchBlock.get())[0], methodNode, frames, blockLables, stateVarIndex, dispatcherEntry, methodExtension));
                        handlerInsns.add(newTrueBranch);
                    }

                    handlerInsns.add(updateStateMachine(jumpBlock.trueBranchBlock, blockPathMap.get(jumpBlock.trueBranchBlock)[0], methodNode, frames, blockLables, stateVarIndex, dispatcherEntry, methodExtension));
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
                                innerHandler.add(updateStateMachine(targetBlock, blockPathMap.get(targetBlock)[0], methodNode, frames, blockLables, stateVarIndex, dispatcherEntry, methodExtension));
                                innerHandlers.add(new SwitchCase(originalCaseKey, innerHandler));
                            }
                        }
                        case TableSwitchInsnNode ts -> {
                            for (int i = 0; i < ts.labels.size(); i++) {
                                int originalCaseKey = ts.min + i;
                                BasicBlock targetBlock = switchBlock.keyCaseMap.get(originalCaseKey);

                                InsnList innerHandler = new InsnList();
                                innerHandler.add(updateStateMachine(targetBlock, blockPathMap.get(targetBlock)[0], methodNode, frames, blockLables, stateVarIndex, dispatcherEntry, methodExtension));
                                innerHandlers.add(new SwitchCase(originalCaseKey, innerHandler));
                            }
                        }
                        default -> throw new IllegalStateException("Invalid dispatcher instruction for SwitchBlock");
                    }

                    // Create default case, updating the state machine to the actual default case block.
                    InsnList defaultCaseInsn = new InsnList();
                    defaultCaseInsn.add(updateStateMachine(switchBlock.defaultBlock, blockPathMap.get(switchBlock.defaultBlock)[0], methodNode, frames, blockLables, stateVarIndex, dispatcherEntry, methodExtension));
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
                    handlerInsns.add(updateStateMachine(fallTroughBlock.fallTroughBlock, blockPathMap.get(fallTroughBlock.fallTroughBlock)[0], methodNode, frames, blockLables, stateVarIndex, dispatcherEntry, methodExtension));
                }
                case TerminalBlock _ -> {
                    // Ends method. Just copy insn.
                    InsnUtil.addToInsnList(block.instructions, handlerInsns);
                }
                default -> throw new IllegalStateException("Invalid block at index: " + blocks.indexOf(block));
            }

            // Add path chain of this block
            // entry -> chain -> chain -> .. -> handler
            int[] blockPath = blockPathMap.get(block);
            for (int i = 0; i < blockPath.length - 1; i++) {
                InsnList pathInsn = new InsnList();
                pathInsn.add(updateStateMachine(null, blockPath[i + 1], methodNode, frames, blockLables, stateVarIndex, dispatcherEntry, methodExtension));
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
    private InsnList updateStateMachine(BasicBlock target, int targetKey, List<AbstractInsnNode> insnList, Frame<BasicValue>[] frames, Map<BasicBlock, LabelNode> blockLabelMap, int stateVar, LabelNode dispatcherEntry, MethodExtension methodExtension) {
        InsnList instructions = new InsnList();

        if (target == null || isStackEmpty(insnList, frames, target.instructions.getFirst())) {
            instructions.add(methodExtension.getObfuscatedIntPush(targetKey));
            instructions.add(new VarInsnNode(Opcodes.ISTORE, stateVar));
            instructions.add(new JumpInsnNode(Opcodes.GOTO, dispatcherEntry));
        } else {
            instructions.add(new JumpInsnNode(GOTO, blockLabelMap.get(target)));
        }

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

    private boolean isStackEmpty(List<AbstractInsnNode> insnList, Frame<BasicValue>[] frames, AbstractInsnNode location) {
        if (insnList == null || frames == null || location == null) {
            return true;
        }

        Frame<BasicValue> frame = frames[insnList.indexOf(location)];
        return frame != null && frame.getStackSize() == 0;
    }
}
