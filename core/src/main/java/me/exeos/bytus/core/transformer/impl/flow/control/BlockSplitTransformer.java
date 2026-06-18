package me.exeos.bytus.core.transformer.impl.flow.control;

import me.exeos.bytus.asmplus.analysis.flow.FlowAnalyzer;
import me.exeos.bytus.asmplus.analysis.flow.block.BasicBlock;
import me.exeos.bytus.asmplus.utils.InsnUtil;
import me.exeos.bytus.core.config.BytusConfig;
import me.exeos.bytus.core.transformer.AbstractTransformer;
import me.exeos.bytus.core.transformer.Priority;
import me.exeos.bytus.core.transformer.context.JarContext;
import me.exeos.bytus.core.transformer.context.MethodContext;
import me.exeos.bytus.core.utils.RandomUtil;
import org.objectweb.asm.tree.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class BlockSplitTransformer extends AbstractTransformer {

    public BlockSplitTransformer(BytusConfig config) {
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
        if (!methodNode.tryCatchBlocks.isEmpty() || methodNode.instructions.size() > 4000) {
            return;
        }

        InsnList newInsn = new InsnList();
        List<BasicBlock> basicBlocks = FlowAnalyzer.getBasicBlocks(methodNode);
        for (AbstractInsnNode insn : methodNode.instructions.toArray()) {
            methodNode.instructions.remove(insn);
        }

        int splitAmount = 10;

        for (BasicBlock block : basicBlocks) {
            int size = block.instructions.size();
            AtomicInteger threshold = new AtomicInteger(getThreshold(size, splitAmount));
            AtomicInteger count = new AtomicInteger(0);
            AtomicReference<LabelNode> label = new AtomicReference<>();
            int i = 0;
            for (AbstractInsnNode abstractInsnNode : block.instructions) {
                if (i == size - 1) {
                    newInsn.add(abstractInsnNode);
                    break;
                }

                if (count.get() == 0) {
                    label.set(new LabelNode());
                    newInsn.add(label.get());
                }

                newInsn.add(abstractInsnNode);

                if (count.incrementAndGet() > threshold.get()) {
                    threshold.set(getThreshold(size, splitAmount));
                    newInsn.add(context.getExtension().getObfuscatedIntPush(1));
                    newInsn.add(new JumpInsnNode(IFEQ, label.get()));
                    count.set(0);
                }
                i++;
            }
        }

        methodNode.instructions = newInsn;
    }

    private int getThreshold(int size, int splitAmount) {
        int splitAt = size / splitAmount;
        return RandomUtil.getInt(
                Math.max(0, splitAt - 2),
                Math.min(size, splitAt + 2)
        );
    }
}
