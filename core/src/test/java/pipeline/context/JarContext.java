package pipeline.context;

import org.objectweb.asm.tree.ClassNode;

import java.util.Map;

public record JarContext(Map<String, ClassNode> classes, Map<String, byte[]> resources) {}
