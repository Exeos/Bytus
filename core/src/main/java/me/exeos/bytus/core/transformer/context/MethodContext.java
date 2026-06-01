package me.exeos.bytus.core.transformer.context;

import me.exeos.bytus.core.transformer.Pipeline;
import org.objectweb.asm.tree.MethodNode;

public record MethodContext(ClassContext ownerCtx, MethodNode methodNode) {

    public JarContext jarCtx() {
        return ownerCtx.jarCtx();
    }

    public Pipeline pipeline() {
        return ownerCtx.pipeline();
    }
}
