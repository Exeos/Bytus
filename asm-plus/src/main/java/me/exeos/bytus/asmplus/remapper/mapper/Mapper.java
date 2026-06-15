package me.exeos.bytus.asmplus.remapper.mapper;

import me.exeos.bytus.asmplus.analysis.hierarchy.HierarchyAnalyzer;
import me.exeos.bytus.asmplus.analysis.hierarchy.edge.ClassEdge;
import me.exeos.bytus.asmplus.jar.JarArchive;
import me.exeos.bytus.asmplus.utils.MethodUtil;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.*;
import java.util.function.Function;

public class Mapper {

    public static Map<String, String> mapClasses(JarArchive jar, Function<Integer, String> nameGen, Function<ClassNode, Boolean> shouldExclude) {
        Map<String, String> mapping = new HashMap<>();
        Set<String> usedNames = new HashSet<>();

        for (ClassNode classNode : jar.getClasses().values()) {
            if (shouldExclude.apply(classNode)) {
                continue;
            }

            String name = genName(nameGen, usedNames);
            usedNames.add(name);

            mapping.put(classNode.name, name);
        }

        return mapping;
    }

    public static Map<String, String> mapFields(JarArchive jar, Function<Integer, String> nameGen, Function<MappingContext, Boolean> shouldExclude) {
        Map<String, String> mapping = new HashMap<>();

        Map<ClassNode, ClassEdge> hierarchy = HierarchyAnalyzer.analyze(jar);
        Map<ClassEdge, Set<String>> usedNamesByClass = new HashMap<>();

        for (ClassNode classNode : jar.getClasses().values()) {
            if (!hierarchy.containsKey(classNode) || shouldExclude.apply(new MappingContext(classNode, Optional.empty(), Optional.empty()))) {
                continue;
            }

            Set<String> usedNames = mergeUsedFromHierarchy(hierarchy.get(classNode), usedNamesByClass);
            for (FieldNode fieldNode : classNode.fields) {
                if (shouldExclude.apply(new MappingContext(classNode, Optional.of(fieldNode), Optional.empty()))) {
                    continue;
                }

                String name = genName(nameGen, usedNames);
                usedNames.add(name);
                usedNamesByClass.computeIfAbsent(hierarchy.get(classNode), _ -> new HashSet<>()).add(name);

                mapping.put(classNode.name + fieldNode.name + fieldNode.desc, name);
            }
        }

        return mapping;
    }

    public static Map<String, String> mapMethods(JarArchive jar, Function<Integer, String> nameGen, Function<MappingContext, Boolean> shouldExclude) {
        Map<String, String> mapping = new HashMap<>();

        Map<ClassNode, ClassEdge> hierarchy = HierarchyAnalyzer.analyze(jar);
        Map<ClassEdge, Set<String>> usedNamesByClass = new HashMap<>();

        for (ClassNode classNode : jar.getClasses().values()) {
            if (!hierarchy.containsKey(classNode) || shouldExclude.apply(new MappingContext(classNode, Optional.empty(), Optional.empty()))) {
                continue;
            }

            Set<String> usedNames = mergeUsedFromHierarchy(hierarchy.get(classNode), usedNamesByClass);
            for (MethodNode methodNode : classNode.methods) {
                if (MethodUtil.isSpecial(methodNode) || shouldExclude.apply(new MappingContext(classNode, Optional.empty(), Optional.of(methodNode)))) {
                    continue;
                }

                String name = genName(nameGen, usedNames);
                usedNames.add(name);
                usedNamesByClass.computeIfAbsent(hierarchy.get(classNode), _ -> new HashSet<>()).add(name);

                mapping.put(classNode.name + methodNode.name + methodNode.desc, name);
            }
        }

        return mapping;
    }

    private static String genName(Function<Integer, String> nameGen, Set<String> excluded) {
        String name;
        int tries = 1;
        do {
            name = nameGen.apply(tries++);
        } while (excluded.contains(name));

        return name;
    }

    private static Set<String> mergeUsedFromHierarchy(ClassEdge edge, Map<ClassEdge, Set<String>> used) {
        Set<String> merged = new HashSet<>();
        if (used.containsKey(edge)) {
            merged.addAll(used.get(edge));
        }

        HierarchyAnalyzer.recurseParents(edge.parents, parent -> {
            if (used.containsKey(parent)) {
                merged.addAll(used.get(parent));
            }
        });

        HierarchyAnalyzer.recurseChildren(edge.children, child -> {
            if (used.containsKey(child)) {
                merged.addAll(used.get(child));
            }
        });

        return merged;
    }
}
