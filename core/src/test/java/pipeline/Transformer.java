package pipeline;

import me.exeos.bytus.core.config.BytusConfig;
import pipeline.context.ClassContext;
import pipeline.context.InsnListContext;
import pipeline.context.JarContext;
import pipeline.context.MethodContext;

public abstract class Transformer {

    public abstract void transform(JarContext context);
    public abstract void transform(ClassContext context);
    public abstract void transform(MethodContext context);
    public abstract void transform(InsnListContext context);

    public abstract boolean applies(BytusConfig config);
    public abstract int priority();
}
