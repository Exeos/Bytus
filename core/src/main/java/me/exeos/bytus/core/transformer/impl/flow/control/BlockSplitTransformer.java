package me.exeos.bytus.core.transformer.impl.flow.control;

import me.exeos.asmplus.analysis.flow.FlowAnalyzer;
import me.exeos.asmplus.analysis.flow.block.BasicBlock;
import me.exeos.bytus.core.config.BytusConfig;
import me.exeos.bytus.core.transformer.AbstractTransformer;
import me.exeos.bytus.core.transformer.Priority;
import me.exeos.bytus.core.transformer.context.MethodContext;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.List;

public class BlockSplitTransformer extends AbstractTransformer {

    public BlockSplitTransformer(BytusConfig config) {
        super(config);
    }

    @Override
    public boolean applies() {
        return config.flow.controlFlow().enable() || true;
    }

    @Override
    public int priority() {
        return Priority.FLOW_BLOCK_SPLIT;
    }

    @Override
    public void transform(MethodContext context) {
        MethodNode methodNode = context.methodNode();
        if (!methodNode.tryCatchBlocks.isEmpty()) {
            return;
        }

        List<BasicBlock> basicBlocks = FlowAnalyzer.getBasicBlocks(methodNode);
        if (basicBlocks.isEmpty() || basicBlocks.size() > 10) {
            return;
        }

        for (BasicBlock basicBlock : basicBlocks) {
            if (basicBlock.instructions.size() < 3) {
                continue;
            }

            InsnList fakeJump = new InsnList();
            LabelNode fakeJumpTarget = new LabelNode();

            fakeJump.add(new JumpInsnNode(GOTO, fakeJumpTarget));
            fakeJump.add(fakeJumpTarget);

            methodNode.instructions.insert(basicBlock.instructions.get(basicBlock.instructions.size() / 2), fakeJump);
        }
    }
}
