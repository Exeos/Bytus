package pipeline.context;

import org.objectweb.asm.tree.ClassNode;

public record ClassContext(JarContext jar, ClassNode classNode) {}
