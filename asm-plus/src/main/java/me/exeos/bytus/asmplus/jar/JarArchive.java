package me.exeos.bytus.asmplus.jar;

import org.objectweb.asm.tree.ClassNode;

import java.util.HashMap;
import java.util.jar.Manifest;

public record JarArchive(HashMap<String, ClassNode> classes, HashMap<String, ClassNode> dependencies, HashMap<String, byte[]> resources, Manifest manifest) {
    public ClassNode getClassNode(String className) {
        if (classes.containsKey(className)) {
            return classes.get(className);
        }
        if (dependencies.containsKey(className)) {
            return dependencies.get(className);
        }

        System.out.println("Class " + className + " not found in archive. Missing some dependencies?");
        return null;
        // throw new RuntimeException("Class " + className + " not found in archive");
    }
}
