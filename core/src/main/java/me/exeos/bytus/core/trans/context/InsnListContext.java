package me.exeos.bytus.core.trans.context;

import org.objectweb.asm.tree.InsnList;

public record InsnListContext(MethodContext owner, InsnList insnList) {}
