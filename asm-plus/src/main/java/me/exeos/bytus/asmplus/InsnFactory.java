package me.exeos.bytus.asmplus;

import org.objectweb.asm.tree.InsnList;

@FunctionalInterface
public interface InsnFactory {
    InsnList make();
}
