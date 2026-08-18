package me.exeos.bytus.core.transformer.impl.flow.control;

import me.exeos.asmplus.analysis.flow.FlowAnalyzer;
import me.exeos.asmplus.analysis.flow.block.BasicBlock;
import me.exeos.bytus.core.config.BytusConfig;
import me.exeos.bytus.core.transformer.AbstractTransformer;
import me.exeos.bytus.core.transformer.Priority;
import me.exeos.bytus.core.transformer.context.MethodContext;
import me.exeos.bytus.core.utils.MathUtil;
import me.exeos.bytus.core.utils.RandomUtil;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;

import java.util.*;

public class BlockSplitTransformer extends AbstractTransformer {

    public BlockSplitTransformer(BytusConfig config) {
        super(config);
    }

    @Override
    public boolean applies() {
        return config.flow.controlFlow().enable();
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

        Analyzer<BasicValue> analyzer = new Analyzer<>(new BasicInterpreter());
        Frame<BasicValue>[] frames;
        try {
            frames = analyzer.analyze(context.ownerCtx().classNode().name, methodNode);
        } catch (AnalyzerException e) {
            return;
        }

        Map<AbstractInsnNode, LabelNode> zeroStackLabels = new HashMap<>();
        Set<AbstractInsnNode> usedZSLabels = new HashSet<>();

        for (AbstractInsnNode insnNode : methodNode.instructions) {
            int i = methodNode.instructions.indexOf(insnNode);
            if (i < methodNode.instructions.size() - 1 && frames != null && i < frames.length && frames[i] != null && frames[i].getStackSize() == 0) {
                zeroStackLabels.put(insnNode, new LabelNode());
            }
        }

        List<BasicBlock> basicBlocks = FlowAnalyzer.getBasicBlocks(methodNode);
        if (basicBlocks.isEmpty() || basicBlocks.size() > 10) {
            return;
        }

        for (BasicBlock basicBlock : basicBlocks) {
            int middle = basicBlock.instructions.size() / 2;
            int walkLeft = middle;
            int walkRight = middle;

            while (walkLeft >= 0) {
                int index = methodNode.instructions.indexOf(basicBlock.instructions.get(walkLeft));
                if (index >= frames.length) {
                    walkLeft--;
                    continue;
                }

                Frame<BasicValue> frame = frames[index];
                if (frame != null && frame.getStackSize() == 0) {
                    break;
                }
                walkLeft--;
            }

            while (walkRight < basicBlock.instructions.size()) {
                int index = methodNode.instructions.indexOf(basicBlock.instructions.get(walkRight));
                if (index >= frames.length) {
                    walkRight++;
                    continue;
                }

                Frame<BasicValue> frame = frames[index];
                if (frame != null && frame.getStackSize() == 0) {
                    break;
                }
                walkRight++;
            }

            int validInsnIndex = MathUtil.getClosets(walkLeft, walkRight, middle);
            if (validInsnIndex >= 0 && validInsnIndex < basicBlock.instructions.size() - 1) {
                var pick = Objects.requireNonNull(RandomUtil.getRandomMapEntry(zeroStackLabels));
                LabelNode entryLabel = pick.getValue();
                usedZSLabels.add(pick.getKey());

                InsnList fakeJump = new InsnList();
                fakeJump.add(new InsnNode(ICONST_1));
                fakeJump.add(new JumpInsnNode(IFEQ, entryLabel));
                methodNode.instructions.insert(basicBlock.instructions.get(validInsnIndex), fakeJump);
            }
        }

        for (AbstractInsnNode insertPoint : usedZSLabels) {
            methodNode.instructions.insert(insertPoint, zeroStackLabels.get(insertPoint));
        }
    }
}
