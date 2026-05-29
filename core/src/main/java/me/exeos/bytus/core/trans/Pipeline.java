package me.exeos.bytus.core.trans;


import me.exeos.bytus.core.trans.context.ClassContext;
import me.exeos.bytus.core.trans.context.InsnListContext;
import me.exeos.bytus.core.trans.context.JarContext;
import me.exeos.bytus.core.trans.context.MethodContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class Pipeline {

    private final List<AbstractTransformer> transformers;

    public Pipeline() {
        this(new ArrayList<>());
    }

    public Pipeline(List<AbstractTransformer> transformers) {
        this.transformers = transformers;
    }

    public Pipeline append(AbstractTransformer transformer) {
        transformers.add(transformer);

        return this;
    }

    public void transform(JarContext context) {
        transformers.forEach(transformer -> transformer.transform(context));
    }

    public void transform(ClassContext context) {
        transformers.forEach(transformer -> transformer.transform(context));
    }

    public void transform(MethodContext context) {
        transformers.forEach(transformer -> transformer.transform(context));
    }

    public void transform(InsnListContext context) {
        transformers.forEach(transformer -> transformer.transform(context));
    }

    public void transform(JarContext context, Set<Class<? extends AbstractTransformer>> exclusions) {
        for (AbstractTransformer transformer : transformers) {
            if (!exclusions.contains(transformer.getClass())) {
                transformer.transform(context);
            }
        }
    }

    public void transform(ClassContext context, Set<Class<? extends AbstractTransformer>> exclusions) {
        for (AbstractTransformer transformer : transformers) {
            if (!exclusions.contains(transformer.getClass())) {
                transformer.transform(context);
            }
        }
    }

    public void transform(MethodContext context, Set<Class<? extends AbstractTransformer>> exclusions) {
        for (AbstractTransformer transformer : transformers) {
            if (!exclusions.contains(transformer.getClass())) {
                transformer.transform(context);
            }
        }
    }

    public void transform(InsnListContext context, Set<Class<? extends AbstractTransformer>> exclusions) {
        for (AbstractTransformer transformer : transformers) {
            if (!exclusions.contains(transformer.getClass())) {
                transformer.transform(context);
            }
        }
    }
}
