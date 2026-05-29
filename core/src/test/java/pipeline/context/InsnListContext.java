package pipeline.context;

import org.objectweb.asm.tree.InsnList;

public record InsnListContext(MethodContext owner, InsnList insnList) {}
