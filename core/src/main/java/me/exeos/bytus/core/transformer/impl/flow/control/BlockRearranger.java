package me.exeos.bytus.core.transformer.impl.flow.control;

import me.exeos.bytus.asmplus.analysis.flow.FlowAnalyzer;
import me.exeos.bytus.asmplus.analysis.flow.block.BasicBlock;
import me.exeos.bytus.asmplus.analysis.flow.block.impl.FallTroughBlock;
import me.exeos.bytus.asmplus.analysis.flow.block.impl.JumpBlock;
import me.exeos.bytus.asmplus.analysis.flow.block.impl.SwitchBlock;
import me.exeos.bytus.asmplus.analysis.flow.block.impl.TerminalBlock;
import me.exeos.bytus.asmplus.utils.InsnUtil;
import me.exeos.bytus.asmplus.utils.MethodUtil;
import me.exeos.bytus.core.config.BytusConfig;
import me.exeos.bytus.core.transformer.AbstractTransformer;
import me.exeos.bytus.core.transformer.Priority;
import me.exeos.bytus.core.transformer.context.MethodContext;
import me.exeos.bytus.core.transformer.extensions.MethodExtension;
import org.objectweb.asm.tree.*;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BlockRearranger extends AbstractTransformer {

    public BlockRearranger(BytusConfig config) {
        super(config);
    }

    @Override
    public boolean applies() {
//        return config.flow.controlFlow().enable();
        return true;
    }

    @Override
    public int priority() {
        return Priority.FLOW_BLOCK_REARRANGE;
    }

    @Override
    public void transform(MethodContext context) {
        MethodNode methodNode = context.methodNode();
        MethodExtension extension = context.getExtension();

        if (!methodNode.tryCatchBlocks.isEmpty()) {
            return;
        }

        List<BasicBlock> basicBlocks = FlowAnalyzer.getBasicBlocks(methodNode);
        Map<BasicBlock, LabelNode> labelByBlock = labelMap(basicBlocks);
        if (basicBlocks.isEmpty()) {
            return;
        }

        MethodUtil.removeAllInsn(methodNode);

        BasicBlock first = basicBlocks.getFirst();
        Collections.shuffle(basicBlocks);
        basicBlocks.remove(first);
        basicBlocks.addFirst(first);

        InsnList rearranged = new InsnList();
        for (BasicBlock basicBlock : basicBlocks) {
            InsnUtil.addToInsnList(basicBlock.instructions, rearranged);
            switch (basicBlock) {
                case JumpBlock jumpBlock -> {
                    jumpBlock.falseBranchBlock.ifPresent(falseBlock -> {
                        rearranged.add(new JumpInsnNode(GOTO, labelByBlock.get(falseBlock)));
                    });
                }
                case SwitchBlock switchBlock -> {}
                case FallTroughBlock fallTroughBlock -> {
                    rearranged.add(new JumpInsnNode(GOTO, labelByBlock.get(fallTroughBlock.fallTroughBlock)));
                }
                case TerminalBlock terminalBlock -> {}
                default -> {
                    throw new IllegalStateException("Invalid block type: " + basicBlock.getClass().getName());
                }
            }
        }

        methodNode.instructions = rearranged;
    }

    private Map<BasicBlock, LabelNode> labelMap(List<BasicBlock> blocks) {
        Map<BasicBlock, LabelNode> mapped = new HashMap<>();
        for (BasicBlock block : blocks) {
            if (block.instructions.getFirst() instanceof LabelNode labelNode) {
                mapped.put(block, labelNode);
            } else {
                LabelNode newLabel = new LabelNode();
                block.instructions.addFirst(newLabel);
                mapped.put(block, newLabel);
            }
        }

        return mapped;
    }
}
