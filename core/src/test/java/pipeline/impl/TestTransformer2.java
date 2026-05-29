package pipeline.impl;

import me.exeos.bytus.core.config.BytusConfig;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import pipeline.Transformer;
import pipeline.TransformerManager;
import pipeline.context.ClassContext;
import pipeline.context.InsnListContext;
import pipeline.context.JarContext;
import pipeline.context.MethodContext;

import java.util.Set;

public class TestTransformer2 extends Transformer {

    @Override
    public void transform(JarContext context) {
        for (ClassNode classNode : context.classes().values()) {
            transform(new ClassContext(context, classNode));
        }
    }

    @Override
    public void transform(ClassContext context) {
        for (MethodNode methodNode : context.classNode().methods) {
            transform(new MethodContext(context, methodNode));
        }
    }

    @Override
    public void transform(MethodContext context) {
        transform(new InsnListContext(context, context.methodNode().instructions));
    }

    @Override
    public void transform(InsnListContext context) {
        // apply modifications to insn list
        TransformerManager.getConfigPipeline().transform(context, Set.of(TestTransformer2.class));
    }

    @Override
    public boolean applies(BytusConfig config) {
        return config.constants.enable();
    }

    @Override
    public int priority() {
        return 1;
    }
}
