package me.exeos.bytus.asmplus.remapper;

import me.exeos.bytus.asmplus.analysis.hierarchy.HierarchyAnalyzer;
import me.exeos.bytus.asmplus.analysis.hierarchy.edge.ClassEdge;
import me.exeos.bytus.asmplus.jar.JarArchive;
import org.objectweb.asm.Handle;
import org.objectweb.asm.tree.*;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public class MethodRemapper {

    private final Map<String, String> mapping;

    public MethodRemapper(Map<String, String> mapping) {
        this.mapping = mapping;
    }

    public void remap(JarArchive jar) {
        Map<String, ClassEdge> hierarchy = HierarchyAnalyzer.analyzeNameMapped(jar);
        jar.getClasses().values().forEach(classNode -> remap(hierarchy, classNode));
    }

    private void remap(Map<String, ClassEdge> hierarchy, ClassNode classNode) {
        for (MethodNode methodNode : classNode.methods) {
            methodNode.name = getMapped(classNode.name, methodNode);

            for (AbstractInsnNode insnNode : methodNode.instructions) {
                switch (insnNode) {
                    case MethodInsnNode methodInsnNode -> {
                        methodInsnNode.name = getMapped(
                                findRoot(hierarchy, methodInsnNode.owner, methodInsnNode.name, methodInsnNode.desc),
                                methodInsnNode.name,
                                methodInsnNode.desc);
                    }
                    case InvokeDynamicInsnNode indy -> {
                        indy.bsm = remapHandle(indy.bsm);

                        for (int i = 0; i < indy.bsmArgs.length; i++) {
                            Object bsmArg = indy.bsmArgs[i];
                            if (bsmArg instanceof Handle handle) {
                                indy.bsmArgs[i] = remapHandle(handle);
                            }
                        }
                    }
                    default -> {}
                }
            }
        }
    }

    private String findRoot(Map<String, ClassEdge> hierarchy, String owner, String name, String desc) {
        if (!hierarchy.containsKey(owner)) {
            return owner;
        }

        AtomicReference<String> rootOwner = new AtomicReference<>(owner);
        hierarchy.get(owner)
                .findNearestMethod(name, desc)
                .ifPresent(methodEdge -> {
                    rootOwner.set(methodEdge.getRoot().owner().classNode.name);
                });

        return rootOwner.get();
    }

    private Handle remapHandle(Handle handle) {
        return new Handle(
                handle.getTag(),
                handle.getOwner(),
                getMapped(handle.getOwner(), handle.getName(), handle.getDesc()),
                handle.getDesc(),
                handle.isInterface()
        );
    }

    private String getMapped(String owner, MethodNode methodNode) {
        return getMapped(owner, methodNode.name, methodNode.desc);
    }

    private String getMapped(MethodInsnNode methodInsnNode) {
        return getMapped(methodInsnNode.owner, methodInsnNode.name, methodInsnNode.desc);
    }

    private String getMapped(String owner, String name, String desc) {
        return mapping.getOrDefault(owner + name + desc, name);
    }
}
