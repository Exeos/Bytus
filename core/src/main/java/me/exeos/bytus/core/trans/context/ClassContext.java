package me.exeos.bytus.core.trans.context;

import org.objectweb.asm.tree.ClassNode;

public record ClassContext(JarContext jar, ClassNode classNode) {}
