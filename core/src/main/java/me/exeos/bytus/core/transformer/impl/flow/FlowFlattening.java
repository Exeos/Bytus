package me.exeos.bytus.core.transformer.impl.flow;

import me.exeos.bytus.asmplus.analysis.flow.block.BasicBlock;
import me.exeos.bytus.asmplus.analysis.flow.FlowAnalyzer;
import me.exeos.bytus.asmplus.analysis.flow.block.impl.FallTroughBlock;
import me.exeos.bytus.asmplus.analysis.flow.block.impl.JumpBlock;
import me.exeos.bytus.asmplus.analysis.flow.block.impl.SwitchBlock;
import me.exeos.bytus.asmplus.analysis.flow.block.impl.TerminalBlock;
import me.exeos.bytus.asmplus.codegen.lookupswitch.LookupSwitchGenerator;
import me.exeos.bytus.asmplus.codegen.lookupswitch.SwitchCase;
import me.exeos.bytus.asmplus.jar.JarArchive;
import me.exeos.bytus.asmplus.utils.InsnUtil;
import me.exeos.bytus.asmplus.utils.MethodUtil;
import me.exeos.bytus.core.transformer.Transformer;
import me.exeos.bytus.core.transformer.TransformerPipeline;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.util.*;

// TODO: make verifier shut up about frames
public class FlowFlattening extends Transformer {

    public FlowFlattening(JarArchive jar, List<String> exclusions, List<String> inclusions) {
        super(jar, exclusions, inclusions);
    }

    @Override
    public void transform(TransformerPipeline pipeline) {
        for (ClassNode classNode : getIncludedClasses()) {
            for (MethodNode methodNode : classNode.methods) {
                methodNode.localVariables = null;
                if (methodNode.instructions.size() == 0 || !methodNode.tryCatchBlocks.isEmpty()) {
                    continue;
                }

                List<BasicBlock> basicBlocks = FlowAnalyzer.getBasicBlocks(methodNode);
                if (basicBlocks.isEmpty()) {
                    continue;
                }

                for (AbstractInsnNode insn : methodNode.instructions.toArray()) {
                    methodNode.instructions.remove(insn);
                }

                Map<BasicBlock, int[]> blockKeys = genBlockKeys(basicBlocks);
                int stateVar = methodNode.maxLocals++;

                InsnList obfuscated = new InsnList();

                // load key of first block and store it in the state var
                obfuscated.add(InsnUtil.getIntPush(blockKeys.get(basicBlocks.getFirst())[0]));
                obfuscated.add(new VarInsnNode(Opcodes.ISTORE, stateVar));

                LabelNode loopStart = new LabelNode();
                obfuscated.add(loopStart);
                obfuscated.add(new VarInsnNode(Opcodes.ILOAD, stateVar));
                obfuscated.add(generateStateMachineSwitch(basicBlocks, blockKeys, stateVar, loopStart));

                methodNode.instructions = obfuscated;
            }
        }
    }

    private InsnList generateStateMachineSwitch(List<BasicBlock> basicBlocks, Map<BasicBlock, int[]> blockKeys, int stateVar, LabelNode loopStart) {
        List<SwitchCase> handlers = new ArrayList<>();
        for (BasicBlock block : basicBlocks) {
            InsnList handlerInsns = new InsnList();
            switch (block) {
                case JumpBlock jumpBlock -> {
                    InsnUtil.addToInsnList(jumpBlock.instructions, handlerInsns);
                    handlerInsns.remove(jumpBlock.dispatcher);

                    if (jumpBlock.falseBranchBlock.isPresent()) {
                        LabelNode newTrueBranch = new LabelNode();

                        handlerInsns.add(new JumpInsnNode(jumpBlock.dispatcher.getOpcode(), newTrueBranch));
                        handlerInsns.add(updateStateMachine(blockKeys.get(jumpBlock.falseBranchBlock.get())[0], stateVar, loopStart));
                        handlerInsns.add(newTrueBranch);
                    }

                    handlerInsns.add(updateStateMachine(blockKeys.get(jumpBlock.trueBranchBlock)[0], stateVar, loopStart));
                }
                case SwitchBlock switchBlock -> {
                    List<SwitchCase> innerHandlers = new ArrayList<>();

                    // convert normal cases to cases only updating state machine to their target block
                    switch (switchBlock.dispatcher) {
                        case LookupSwitchInsnNode ls -> {
                            for (int i = 0; i < ls.labels.size(); i++) {
                                int key = ls.keys.get(i);
                                BasicBlock targetBlock = switchBlock.keyCaseMap.get(key);

                                InsnList innerHandler = new InsnList();
                                innerHandler.add(updateStateMachine(blockKeys.get(targetBlock)[0], stateVar, loopStart));
                                innerHandlers.add(new SwitchCase(key, innerHandler));
                            }
                        }
                        case TableSwitchInsnNode ts -> {
                            for (int i = 0; i < ts.labels.size(); i++) {
                                int key = ts.min + i;
                                BasicBlock targetBlock = switchBlock.keyCaseMap.get(key);

                                InsnList innerHandler = new InsnList();
                                innerHandler.add(updateStateMachine(blockKeys.get(targetBlock)[0], stateVar, loopStart));
                                innerHandlers.add(new SwitchCase(key, innerHandler));
                            }
                        }
                        default -> throw new IllegalStateException("Invalid dispatcher instruction for SwitchBlock");
                    }

                    // Default Case
                    InsnList defaultCaseInsn = new InsnList();
                    defaultCaseInsn.add(updateStateMachine(blockKeys.get(switchBlock.defaultBlock)[0], stateVar, loopStart));
                    SwitchCase defaultCase = new SwitchCase(0, defaultCaseInsn);

                    // original instructions up to the switch
                    InsnUtil.addToInsnList(switchBlock.instructions, handlerInsns);
                    // remove original switch
                    handlerInsns.remove(switchBlock.dispatcher);

                    handlerInsns.add(LookupSwitchGenerator.gen(innerHandlers, defaultCase, false));
                }
                case FallTroughBlock fallTroughBlock -> {
                    InsnUtil.addToInsnList(block.instructions, handlerInsns);
                    handlerInsns.add(updateStateMachine(blockKeys.get(fallTroughBlock.fallTroughBlock)[0], stateVar, loopStart));
                }
                case TerminalBlock terminalBlock -> {
                    InsnUtil.addToInsnList(block.instructions, handlerInsns);
                }
                default -> {
                    throw new IllegalStateException("Invalid block at index: " + basicBlocks.indexOf(block));
                }
            }


            int[] pathKeys = blockKeys.get(block);
            for (int i = 0; i < pathKeys.length - 1; i++) {
                InsnList pathInsn = new InsnList();
                pathInsn.add(updateStateMachine(pathKeys[i + 1], stateVar, loopStart));
                handlers.add(new SwitchCase(pathKeys[i], pathInsn));
            }

            handlers.add(new SwitchCase(pathKeys[pathKeys.length - 1], handlerInsns));
        }

        return LookupSwitchGenerator.gen(handlers, new SwitchCase(0, MethodUtil.endMethodByThrow()), false);
    }

    private InsnList updateStateMachine(int key, int stateVar, LabelNode loopStart) {
        InsnList instructions = new InsnList();

        instructions.add(InsnUtil.getIntPush(key));
        instructions.add(new VarInsnNode(Opcodes.ISTORE, stateVar));
        instructions.add(new JumpInsnNode(Opcodes.GOTO, loopStart));

        return instructions;
    }

    /**
     * Assignes an array of unique ints to each block. First item in the array is the entry to the path leading to that block
     * @param basicBlocks
     * @return
     */
    private Map<BasicBlock, int[]> genBlockKeys(List<BasicBlock> basicBlocks) {
       Map<BasicBlock, int[]> result = new HashMap<>();
       Set<Integer> used = new HashSet<>();

        Random random = new Random();
        for (BasicBlock block : basicBlocks) {
            int[] pathKeys = new int[3];
            for (int i = 0; i < pathKeys.length; i++) {
                do {
                    pathKeys[i] = random.nextInt(Integer.MAX_VALUE);
                } while (used.contains(pathKeys[i]));
                used.add(pathKeys[i]);
            }

            result.put(block, pathKeys);
        }

        return result;
    }
}
