package me.exeos.bytus.core.transformer.impl.flow.control;

import me.exeos.bytus.asmplus.analysis.flow.FlowAnalyzer;
import me.exeos.bytus.asmplus.analysis.flow.block.BasicBlock;
import me.exeos.bytus.asmplus.analysis.flow.block.impl.FallTroughBlock;
import me.exeos.bytus.asmplus.analysis.flow.block.impl.JumpBlock;
import me.exeos.bytus.asmplus.analysis.flow.block.impl.SwitchBlock;
import me.exeos.bytus.asmplus.analysis.flow.block.impl.TerminalBlock;
import me.exeos.bytus.asmplus.codegen.xswitch.LookupSwitchGenerator;
import me.exeos.bytus.asmplus.codegen.xswitch.SwitchCase;
import me.exeos.bytus.asmplus.utils.InsnUtil;
import me.exeos.bytus.asmplus.utils.MethodUtil;
import me.exeos.bytus.core.config.BytusConfig;
import me.exeos.bytus.core.transformer.AbstractTransformer;
import me.exeos.bytus.core.transformer.Priority;
import me.exeos.bytus.core.transformer.context.MethodContext;
import me.exeos.bytus.core.transformer.extensions.MethodExtension;
import me.exeos.bytus.core.utils.RandomUtil;
import org.objectweb.asm.tree.*;

import java.util.*;

public class BlockEntryDispatchFlowTransformer extends AbstractTransformer {

    public BlockEntryDispatchFlowTransformer(BytusConfig config) {
        super(config);
    }

    @Override
    public boolean applies() {
        return true;
    }

    @Override
    public int priority() {
        return Priority.FLOW_CTRL_FLATTENING - 1;
    }

    @Override
    public void transform(MethodContext context) {
        MethodNode methodNode = context.methodNode();
        if (methodNode.instructions.size() == 0 || !methodNode.tryCatchBlocks.isEmpty()) {
            return;
        }

        int stateVarIndex = methodNode.maxLocals++;
        List<BasicBlock> blocks = FlowAnalyzer.getBasicBlocks(methodNode);

        Map<BasicBlock, Integer> blockKeys = new HashMap<>();
        Set<Integer> usedKeys = new HashSet<>();
        for (BasicBlock block : blocks) {
            blockKeys.put(block, RandomUtil.getIntExcept(usedKeys));
        }
        MethodUtil.removeAllInsn(methodNode);

        LabelNode entry = new LabelNode();
        InsnList handlerInsn = new InsnList();
        handlerInsn.add(context.getExtension().getObfuscatedIntPush(blockKeys.get(blocks.getFirst())));
        handlerInsn.add(new VarInsnNode(ISTORE, stateVarIndex));
        handlerInsn.add(entry);
        for (BasicBlock block : blocks) {
            LabelNode blockExit = new LabelNode();
            boolean isLast = blocks.indexOf(block) == blocks.size() - 1;

            handlerInsn.add(new VarInsnNode(ILOAD, stateVarIndex));
            handlerInsn.add(context.getExtension().getObfuscatedIntPush(blockKeys.get(block)));
            handlerInsn.add(new JumpInsnNode(IF_ICMPNE, blockExit));

            switch (block) {
                case JumpBlock jumpBlock -> {
                    InsnUtil.addToInsnList(jumpBlock.instructions, handlerInsn);
                    handlerInsn.remove(jumpBlock.dispatcher);

                    jumpBlock.falseBranchBlock.ifPresent(falseBlock -> {
                        LabelNode newTrueBranch = new LabelNode();
                        handlerInsn.add(new JumpInsnNode(jumpBlock.dispatcher.getOpcode(), newTrueBranch));
                        handlerInsn.add(updateStateVar(falseBlock, stateVarIndex, blockExit, blockKeys, context.getExtension()));
                        handlerInsn.add(newTrueBranch);
                    });
                    handlerInsn.add(context.getExtension().getObfuscatedIntPush(blockKeys.get(jumpBlock.trueBranchBlock)));
                    handlerInsn.add(new VarInsnNode(ISTORE, stateVarIndex));
                }
                case SwitchBlock switchBlock -> {
                    List<SwitchCase> newSwitch = new ArrayList<>();
                    switch (switchBlock.dispatcher) {
                        case LookupSwitchInsnNode ls -> {
                            for (int i = 0; i < ls.labels.size(); i++) {
                                int originalCaseKey = ls.keys.get(i);
                                BasicBlock targetBlock = switchBlock.keyCaseMap.get(originalCaseKey);

                                newSwitch.add(new SwitchCase(originalCaseKey, updateStateVar(targetBlock, stateVarIndex, blockExit, blockKeys, context.getExtension())));
                            }
                        }
                        case TableSwitchInsnNode ts -> {
                            for (int i = 0; i < ts.labels.size(); i++) {
                                int originalCaseKey = ts.min + i;
                                BasicBlock targetBlock = switchBlock.keyCaseMap.get(originalCaseKey);

                                newSwitch.add(new SwitchCase(originalCaseKey, updateStateVar(targetBlock, stateVarIndex, blockExit, blockKeys, context.getExtension())));
                            }
                        }
                        default ->
                                throw new IllegalStateException("Invalid dispatcher for SwitchBlock: " + switchBlock.dispatcher.getClass().getName());
                    }

                    InsnList defaultCaseInsn = new InsnList();
                    defaultCaseInsn.add(updateStateVar(switchBlock.defaultBlock, stateVarIndex, blockExit, blockKeys, context.getExtension()));

                    InsnUtil.addToInsnList(switchBlock.instructions, handlerInsn);
                    handlerInsn.remove(switchBlock.dispatcher);
                    handlerInsn.add(LookupSwitchGenerator.gen(newSwitch, new SwitchCase(0, defaultCaseInsn), false));
                }
                case FallTroughBlock fallTroughBlock -> {
                    InsnUtil.addToInsnList(fallTroughBlock.instructions, handlerInsn);
                    handlerInsn.add(context.getExtension().getObfuscatedIntPush(blockKeys.get(fallTroughBlock.fallTroughBlock)));
                    handlerInsn.add(new VarInsnNode(ISTORE, stateVarIndex));
                }
                case TerminalBlock terminalBlock -> {
                    InsnUtil.addToInsnList(terminalBlock.instructions, handlerInsn);
                }
                default -> throw new IllegalStateException("Invalid block: " + block.getClass().getName());
            }
            handlerInsn.add(blockExit);

            if (isLast) {
                handlerInsn.add(new JumpInsnNode(GOTO, entry));
            }
        }

        methodNode.instructions = handlerInsn;
    }

    private InsnList updateStateVar(BasicBlock target, int stateVarIndex, LabelNode blockExit, Map<BasicBlock, Integer> blockKeys, MethodExtension extension) {
        InsnList updateInsn = new InsnList();

        updateInsn.add(extension.getObfuscatedIntPush(blockKeys.get(target)));
        updateInsn.add(new VarInsnNode(ISTORE, stateVarIndex));
        updateInsn.add(new JumpInsnNode(GOTO, blockExit));

        return updateInsn;
    }
}
