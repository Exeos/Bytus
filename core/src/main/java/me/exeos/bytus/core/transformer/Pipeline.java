package me.exeos.bytus.core.transformer;


import me.exeos.bytus.core.transformer.context.ClassContext;
import me.exeos.bytus.core.transformer.context.InsnListContext;
import me.exeos.bytus.core.transformer.context.JarContext;
import me.exeos.bytus.core.transformer.context.MethodContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

public class Pipeline {

    private final List<AbstractTransformer> transformers;

    public Pipeline() {
        this(new ArrayList<>());
    }

    public Pipeline(List<AbstractTransformer> transformers) {
        this.transformers = transformers;
    }

    public void run(JarContext context) {
        transformers.forEach(transformer -> transformer.transform(context));
    }

    public void emit(ClassContext context, Set<Class<? extends AbstractTransformer>> exclusions) {
        context.jarCtx().jar().classes().put(context.classNode().name, context.classNode());
        transform(context, exclusions);
    }

    public void emit(MethodContext context, Set<Class<? extends AbstractTransformer>> exclusions) {
        context.ownerCtx().classNode().methods.add(context.methodNode());
        transform(context, exclusions);
    }

    public void transform(JarContext context, Set<Class<? extends AbstractTransformer>> exclusions) {
        dispatchFiltered(t -> t.transform(context), exclusions);
    }

    public void transform(ClassContext context, Set<Class<? extends AbstractTransformer>> exclusions) {
        dispatchFiltered(t -> t.transform(context), exclusions);
    }

    public void transform(MethodContext context, Set<Class<? extends AbstractTransformer>> exclusions) {
        dispatchFiltered(t -> t.transform(context), exclusions);
    }


    private void dispatchFiltered(Consumer<AbstractTransformer> action, Set<Class<? extends AbstractTransformer>> exclusions) {
        for (AbstractTransformer t : transformers) {
            if (!exclusions.contains(t.getClass())) {
                action.accept(t);
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
