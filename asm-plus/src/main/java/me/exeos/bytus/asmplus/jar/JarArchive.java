package me.exeos.bytus.asmplus.jar;

import org.objectweb.asm.tree.ClassNode;

import java.util.Map;
import java.util.jar.Manifest;

public class JarArchive {

    private Map<String, ClassNode> classes;
    private Map<String, ClassNode> dependencies;
    private Map<String, byte[]> resources;
    private Manifest manifest;

    public JarArchive(Map<String, ClassNode> classes, Map<String, ClassNode> dependencies, Map<String, byte[]> resources, Manifest manifest) {
        this.classes = classes;
        this.dependencies = dependencies;
        this.resources = resources;
        this.manifest = manifest;
    }

    public ClassNode getClassNode(String className) {
        if (classes.containsKey(className)) {
            return classes.get(className);
        }
        if (dependencies.containsKey(className)) {
            return dependencies.get(className);
        }

        System.out.println("Class " + className + " not found in archive. Missing some dependencies?");
        return null;
    }

    public Map<String, ClassNode> getClasses() {
        return classes;
    }

    public void setClasses(Map<String, ClassNode> classes) {
        this.classes = classes;
    }

    public Map<String, ClassNode> getDependencies() {
        return dependencies;
    }

    public Map<String, byte[]> getResources() {
        return resources;
    }

    public Manifest getManifest() {
        return manifest;
    }
}
