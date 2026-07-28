package me.exeos.bytus.core.transformer.impl;

import me.exeos.bytus.core.config.BytusConfig;
import me.exeos.bytus.core.transformer.AbstractTransformer;
import me.exeos.bytus.core.transformer.Priority;
import me.exeos.bytus.core.transformer.context.ClassContext;
import me.exeos.bytus.core.transformer.context.MethodContext;
import org.objectweb.asm.tree.ClassNode;

public class PreProcessor extends AbstractTransformer {

    public PreProcessor(BytusConfig config) {
        super(config);
    }

    @Override
    public boolean applies() {
        return true;
    }

    @Override
    public int priority() {
        return Priority.PRE_PROCESSOR;
    }

    @Override
    public void transform(ClassContext context) {
        ClassNode classNode = context.classNode();
        classNode.sourceDebug = null;
        classNode.sourceFile = null;
        classNode.signature = null;
        classNode.nestHostClass = null;
        classNode.outerMethod = null;
        classNode.outerMethodDesc = null;
        classNode.innerClasses.clear();
        classNode.nestMembers = null;
        classNode.permittedSubclasses = null;
        classNode.fields.forEach(fieldNode -> fieldNode.signature = null);

        super.transform(context);
    }

    @Override
    public void transform(MethodContext context) {
        context.methodNode().localVariables = null;
        context.methodNode().parameters = null;
        context.methodNode().signature = null;
    }
}
