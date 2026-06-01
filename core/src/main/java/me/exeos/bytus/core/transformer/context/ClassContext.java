package me.exeos.bytus.core.transformer.context;

import me.exeos.bytus.core.transformer.Pipeline;
import org.objectweb.asm.tree.ClassNode;

public record ClassContext(JarContext jarCtx, ClassNode classNode) {

    public Pipeline pipeline() {
        return jarCtx.pipeline();
    }
}
