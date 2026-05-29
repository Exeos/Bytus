package pipeline.context;

import org.objectweb.asm.tree.MethodNode;

public record MethodContext(ClassContext owner, MethodNode methodNode) {}
