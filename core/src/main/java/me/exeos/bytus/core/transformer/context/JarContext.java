package me.exeos.bytus.core.transformer.context;

import me.exeos.asmplus.jar.JarArchive;
import me.exeos.bytus.core.transformer.Pipeline;
import me.exeos.bytus.core.transformer.extensions.JarExtension;

public record JarContext(JarArchive jar, Pipeline pipeline) {

    public JarExtension getExtension() {
        return pipeline().getExtension(this);
    }
}
