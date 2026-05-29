package me.exeos.bytus.core.transformer.context;

import me.exeos.bytus.core.transformer.Pipeline;
import org.objectweb.asm.tree.InsnList;

public record InsnListContext(MethodContext ownerCtx, Pipeline pipeline, InsnList insnList) {

    public InsnListContext(MethodContext ownerCtx, InsnList insnList) {
        this(ownerCtx, ownerCtx.pipeline(), insnList);
    }

    public InsnListContext(Pipeline pipeline, InsnList insnList) {
        this(null, pipeline, insnList);
    }

    public boolean isBound() {
        return ownerCtx != null;
    }

    public ClassContext classCtx() {
        return ownerCtx != null ? ownerCtx.ownerCtx() : null;
    }

    public JarContext jarCtx() {
        return ownerCtx != null ? ownerCtx.jarCtx() : null;
    }
}
