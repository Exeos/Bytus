package me.exeos.bytus.core.trans.impl;

import me.exeos.bytus.core.config.BytusConfig;
import me.exeos.bytus.core.trans.AbstractTransformer;
import me.exeos.bytus.core.trans.context.ClassContext;
import me.exeos.bytus.core.trans.context.MethodContext;

public class PreProcessor implements AbstractTransformer {

    @Override
    public void transform(ClassContext context) {
        context.classNode().sourceDebug = null;
        context.classNode().sourceFile = null;

        AbstractTransformer.super.transform(context);
    }

    @Override
    public void transform(MethodContext context) {
        context.methodNode().localVariables = null;
        context.methodNode().parameters = null;
    }

    @Override
    public boolean applies(BytusConfig config) {
        return true;
    }

    @Override
    public int priority() {
        return 0;
    }
}
