package me.exeos.bytus.core.trans.context;

import me.exeos.bytus.core.trans.Pipeline;
import org.objectweb.asm.tree.ClassNode;

import java.util.Map;

public record JarContext(Pipeline pipeline, Map<String, ClassNode> classes, Map<String, byte[]> resources) {}
