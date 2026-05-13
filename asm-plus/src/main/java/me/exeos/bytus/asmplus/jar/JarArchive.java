package me.exeos.bytus.asmplus.jar;

import org.objectweb.asm.tree.ClassNode;

import java.util.HashMap;

public record JarArchive(HashMap<String, ClassNode> classes, HashMap<String, byte[]> resources) {}
