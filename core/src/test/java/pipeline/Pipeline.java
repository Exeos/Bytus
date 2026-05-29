package pipeline;

import pipeline.context.ClassContext;
import pipeline.context.InsnListContext;
import pipeline.context.JarContext;
import pipeline.context.MethodContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class Pipeline {

    private final List<Transformer> transformers;

    public Pipeline() {
        this(new ArrayList<>());
    }

    public Pipeline(List<Transformer> transformers) {
        this.transformers = transformers;
    }

    public Pipeline append(Transformer transformer) {
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

    public void transform(JarContext context, Set<Class<? extends Transformer>> exclusions) {
        for (Transformer transformer : transformers) {
            if (!exclusions.contains(transformer.getClass())) {
                transformer.transform(context);
            }
        }
    }

    public void transform(ClassContext context, Set<Class<? extends Transformer>> exclusions) {
        for (Transformer transformer : transformers) {
            if (!exclusions.contains(transformer.getClass())) {
                transformer.transform(context);
            }
        }
    }

    public void transform(MethodContext context, Set<Class<? extends Transformer>> exclusions) {
        for (Transformer transformer : transformers) {
            if (!exclusions.contains(transformer.getClass())) {
                transformer.transform(context);
            }
        }
    }

    public void transform(InsnListContext context, Set<Class<? extends Transformer>> exclusions) {
        for (Transformer transformer : transformers) {
            if (!exclusions.contains(transformer.getClass())) {
                transformer.transform(context);
            }
        }
    }
}
