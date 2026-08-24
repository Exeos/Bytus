package me.exeos.bytus.core.transformer.impl.flow.control;

import me.exeos.asmplus.analysis.stack.StackAnalyzer;
import me.exeos.asmplus.utils.InsnUtil;
import me.exeos.bytus.core.config.BytusConfig;
import me.exeos.bytus.core.transformer.AbstractTransformer;
import me.exeos.bytus.core.transformer.Priority;
import me.exeos.bytus.core.transformer.context.MethodContext;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.analysis.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class FlowTestTransformer extends AbstractTransformer {

    public FlowTestTransformer(BytusConfig config) {
        super(config);
    }


    @Override
    public boolean applies() {
        return config.flow.controlFlow().enable();
    }

    @Override
    public int priority() {
        return Priority.FLOW_TEST;
    }

    @Override
    public void transform(MethodContext context) {
        Map<String, List<StackAnalyzer.InsnFrame>> grouped = StackAnalyzer.groupFrames(context.ownerCtx().classNode().name, context.methodNode());
        Analyzer<BasicValue> analyzer = new Analyzer<>(new BasicInterpreter());
        Frame<BasicValue>[] frames;
        try {
            frames = analyzer.analyze(context.ownerCtx().classNode().name, context.methodNode());
        } catch (AnalyzerException _) {
            return;
        }

        AtomicInteger insertionCount = new AtomicInteger();
        final int insertionLimit = 15;
        InsnUtil.loopIndexed(context.methodNode().instructions, (insnNode, index) -> {
            if (insertionCount.get() > insertionLimit || index >= frames.length) {
                return;
            }

            Frame<BasicValue> frame = frames[index];
            if (frame == null) {
                return;
            }

            var options = grouped.get(StackAnalyzer.frameToString(frame));
            if (options.isEmpty()) {
                return;
            }
            Collections.shuffle(options);

            AbstractInsnNode pick = null;
            for (StackAnalyzer.InsnFrame option : options) {
                AbstractInsnNode curr = option.insnNode();
                if (context.methodNode().instructions.indexOf(curr) < index) {
                    pick = curr;
                }
            }

            if (pick == null) {
                return;
            }

            LabelNode targetLabel = new LabelNode();
            context.methodNode().instructions.insertBefore(insnNode, context.getExtension().getObfuscatedJump(targetLabel, false));
            context.methodNode().instructions.insertBefore(pick, targetLabel);
            insertionCount.getAndIncrement();
        });
    }
}
