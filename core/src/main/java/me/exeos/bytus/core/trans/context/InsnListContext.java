package me.exeos.bytus.core.trans.context;

import org.objectweb.asm.tree.InsnList;

public record InsnListContext(MethodContext ownerCtx, InsnList insnList) {
}
