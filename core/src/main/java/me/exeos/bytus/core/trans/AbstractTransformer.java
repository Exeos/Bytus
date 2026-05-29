package me.exeos.bytus.core.trans;

import me.exeos.bytus.core.config.BytusConfig;
import me.exeos.bytus.core.trans.context.ClassContext;
import me.exeos.bytus.core.trans.context.InsnListContext;
import me.exeos.bytus.core.trans.context.JarContext;
import me.exeos.bytus.core.trans.context.MethodContext;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

public abstract class AbstractTransformer implements Opcodes {

    protected final Pipeline pipeline;
    protected final BytusConfig config;

    public AbstractTransformer(Pipeline pipeline, BytusConfig config) {
        this.pipeline = pipeline;
        this.config = config;
    }

    public void transform(JarContext context) {
        for (ClassNode classNode : context.jar().classes().values()) {
            transform(new ClassContext(context, classNode));
        }
    }

    public void transform(ClassContext context) {
        for (MethodNode methodNode : context.classNode().methods) {
            transform(new MethodContext(context, methodNode));
        }
    }

    public void transform(MethodContext context) {
        transform(new InsnListContext(context, context.methodNode().instructions));
    }

    public void transform(InsnListContext context) {
    }

    public abstract boolean applies();

    public abstract int priority();
}
