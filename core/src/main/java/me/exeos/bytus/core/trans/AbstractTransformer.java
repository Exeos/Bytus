package me.exeos.bytus.core.trans;

import me.exeos.bytus.core.config.BytusConfig;
import me.exeos.bytus.core.trans.context.ClassContext;
import me.exeos.bytus.core.trans.context.InsnListContext;
import me.exeos.bytus.core.trans.context.JarContext;
import me.exeos.bytus.core.trans.context.MethodContext;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

public interface AbstractTransformer {

    default void transform(JarContext context) {
        for (ClassNode classNode : context.classes().values()) {
            transform(new ClassContext(context, classNode));
        }
    }

    default void transform(ClassContext context) {
        for (MethodNode methodNode : context.classNode().methods) {
            transform(new MethodContext(context, methodNode));
        }
    }

    default void transform(MethodContext context) {
        transform(new InsnListContext(context, context.methodNode().instructions));
    }

    default void transform(InsnListContext context) {};

    boolean applies(BytusConfig config);
    int priority();
}
