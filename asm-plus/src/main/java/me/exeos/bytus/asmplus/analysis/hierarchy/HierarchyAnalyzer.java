package me.exeos.bytus.asmplus.analysis.hierarchy;

import me.exeos.bytus.asmplus.analysis.hierarchy.edge.ClassEdge;
import me.exeos.bytus.asmplus.jar.JarArchive;
import me.exeos.bytus.asmplus.utils.HierarchyUtil;
import org.objectweb.asm.tree.ClassNode;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class HierarchyAnalyzer {

    public static Map<String, ClassEdge> analyzeNameMapped(JarArchive jar) {
        Map<ClassNode, ClassEdge> nodeMapped = analyze(jar);
        Map<String, ClassEdge> nameMapped = new HashMap<>();

        nodeMapped.forEach((classNode, classEdge) -> nameMapped.put(classNode.name, classEdge));

        return nameMapped;
    }

    public static Map<ClassNode, ClassEdge> analyze(JarArchive jar) {
        Map<ClassNode, ClassEdge> edgeMap = new HashMap<>();

        for (ClassNode classNode : jar.getClasses().values()) {
            edgeMap.putIfAbsent(classNode, new ClassEdge(classNode));
            HierarchyUtil.forEachAncestorClass(jar, classNode, parent -> {
                edgeMap.get(classNode).parents.add(edgeMap.computeIfAbsent(parent, ClassEdge::new));
            });
        }

        for (ClassEdge edge : edgeMap.values()) {
            for (ClassEdge parent : edge.parents) {
                parent.children.add(edge);
            }
        }

        return edgeMap;
    }

    public static void recurseParents(List<ClassEdge> edges, Consumer<ClassEdge> edgeConsumer) {
        for (ClassEdge edge : edges) {
            edgeConsumer.accept(edge);
            recurseChildren(edge.parents, edgeConsumer);
        }
    }

    public static void recurseChildren(List<ClassEdge> edges, Consumer<ClassEdge> edgeConsumer) {
        for (ClassEdge edge : edges) {
            edgeConsumer.accept(edge);
            recurseChildren(edge.children, edgeConsumer);
        }
    }
}
