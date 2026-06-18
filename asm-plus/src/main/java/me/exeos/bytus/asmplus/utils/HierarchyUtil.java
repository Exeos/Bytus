package me.exeos.bytus.asmplus.utils;

import me.exeos.bytus.asmplus.analysis.hierarchy.edge.ClassEdge;
import me.exeos.bytus.asmplus.jar.JarArchive;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;

public class HierarchyUtil {

    public static String genNoneCollidingFieldName(ClassEdge owner, String fieldDesc, Function<Integer, String> nameGen) {
        String name;
        int tryCount = 1;
        do {
            name = nameGen.apply(tryCount);
            tryCount++;
        } while (owner.findNearestField(name, fieldDesc).isPresent());

        return name;
    }

    public static String genNoneCollidingMethodName(ClassEdge owner, String methodDesc, Function<Integer, String> nameGen) {
        return genNoneCollidingMethodName(owner, methodDesc, Set.of(), nameGen);
    }

    public static String genNoneCollidingMethodName(ClassEdge owner, String methodDesc, Set<String> excludedNames, Function<Integer, String> nameGen) {
        String name;
        int tryCount = 1;
        do {
            name = nameGen.apply(tryCount);
            tryCount++;
        } while (excludedNames.contains(name) || owner.findNearestMethod(name, methodDesc).isPresent());

        return name;
    }

    public static void forEachAncestorClass(JarArchive jar, ClassNode start, Consumer<ClassNode> visitor) {
        forEachAncestorClass(jar, start, true, false, visitor);
    }

    public static void forEachAncestorClass(JarArchive jar, ClassNode start, boolean includeInterfaces, boolean includeStart, Consumer<ClassNode> visitor) {
        forEachAncestorClass(jar, start, includeInterfaces, includeStart, visitor, _ -> {});
    }

    public static void forEachAncestorClass(JarArchive jar, ClassNode start, boolean includeInterfaces, boolean includeStart, Consumer<ClassNode> visitor, Consumer<String> notFoundVisitor) {
        Deque<String> work = new ArrayDeque<>();
        Set<String> seen = new HashSet<>();

        if (start == null) {
            return;
        }

        if (includeStart) {
            work.add(start.name);
        } else {
            if (start.superName != null) {
                work.add(start.superName);
            }
            if (includeInterfaces) {
                work.addAll(start.interfaces);
            }
        }

        while (!work.isEmpty()) {
            String name = work.removeFirst();
            if (!seen.add(name)) {
                continue;
            }

            jar.getClassNode(name).ifPresentOrElse(cn -> {
                visitor.accept(cn);

                if (cn.superName != null) {
                    work.add(cn.superName);
                }

                if (includeInterfaces) {
                    work.addAll(cn.interfaces);
                }
            }, () -> notFoundVisitor.accept(name));
        }
    }

    // todo
    public static void expandExclusions(JarArchive jar, Set<String> exclusionsByDesc, Set<String> exclusionsByName) {
        for (ClassNode classNode : jar.getClasses().values()) {
            // Collect all ancestor methods that are excluded, so we can exclude overrides and calls in this class.
            Set<String> excludedAncestorByDesc = new HashSet<>();
            Set<String> excludedAncestorByName = new HashSet<>();

            HierarchyUtil.forEachAncestorClass(jar, classNode, ancestor -> {
                for (MethodNode m : ancestor.methods) {
                    String keyByDesc = ancestor.name + m.name + m.desc;
                    if (exclusionsByDesc.contains(keyByDesc)) {
                        excludedAncestorByDesc.add(m.name + m.desc);
                    }

                    String keyByName = ancestor.name + m.name;
                    if (exclusionsByName.contains(keyByName)) {
                        excludedAncestorByName.add(m.name);
                    }
                }
            });

            // Apply exclusions to this class if it overrides or calls an excluded ancestor method.
            for (MethodNode m : classNode.methods) {
                // overrides
                if (excludedAncestorByDesc.contains(m.name + m.desc)) {
                    exclusionsByDesc.add(classNode.name + m.name + m.desc);
                }
                if (excludedAncestorByName.contains(m.name)) {
                    exclusionsByName.add(classNode.name + m.name);
                }

                // calls to method in super class
                for (AbstractInsnNode insnNode : m.instructions) {
                    if (insnNode instanceof MethodInsnNode methodInsnNode) {
                        if (excludedAncestorByDesc.contains(methodInsnNode.name + methodInsnNode.desc)) {
                            exclusionsByDesc.add(classNode.name + methodInsnNode.name + methodInsnNode.desc);
                        }
                        if (excludedAncestorByName.contains(methodInsnNode.name)) {
                            exclusionsByName.add(classNode.name + methodInsnNode.name);
                        }
                    }
                }
            }
        }
    }
}
