package me.exeos.bytus.asmplus.remapper.mapper.impl;

import me.exeos.bytus.asmplus.analysis.hierarchy.HierarchyAnalyzer;
import me.exeos.bytus.asmplus.analysis.hierarchy.edge.ClassEdge;
import me.exeos.bytus.asmplus.jar.JarArchive;
import me.exeos.bytus.asmplus.utils.MapperUtil;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;

import java.util.*;
import java.util.function.Function;

public class FieldMapper {

    public static Map<String, String> map(JarArchive jar, Function<Integer, String> nameGen) {
        Map<String, String> mapping = new HashMap<>();

        Map<ClassNode, ClassEdge> hierarchy = HierarchyAnalyzer.analyze(jar);
        Map<ClassEdge, Set<String>> usedNamesByClass = new HashMap<>();

        for (ClassNode classNode : jar.getClasses().values()) {
            if (!hierarchy.containsKey(classNode)) {
                continue;
            }

            Set<String> usedNames = MapperUtil.mergeUsedFromHierarchy(hierarchy.get(classNode), usedNamesByClass);
            for (FieldNode fieldNode : classNode.fields) {

                String name = MapperUtil.genName(nameGen, usedNames);
                usedNames.add(name);
                usedNamesByClass.computeIfAbsent(hierarchy.get(classNode), _ -> new HashSet<>()).add(name);

                mapping.put(classNode.name + fieldNode.name + fieldNode.desc, name);
            }
        }

        return mapping;
    }
}
