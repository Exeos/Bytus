package me.exeos.bytus.core.transformer.context;

import me.exeos.bytus.asmplus.jar.JarArchive;
import me.exeos.bytus.core.transformer.Pipeline;

public record JarContext(JarArchive jar, Pipeline pipeline) {
}
