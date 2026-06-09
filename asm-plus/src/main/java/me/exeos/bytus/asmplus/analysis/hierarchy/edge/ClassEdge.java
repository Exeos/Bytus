package me.exeos.bytus.asmplus.analysis.hierarchy.edge;

import me.exeos.bytus.asmplus.analysis.hierarchy.HierarchyAnalyzer;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

public class ClassEdge {

    public final ClassNode classNode;

    public final List<ClassEdge> parents = new ArrayList<>();
    public final List<ClassEdge> children = new ArrayList<>();
    public final List<MethodEdge> methods = new ArrayList<>();

    public ClassEdge(ClassNode classNode) {
        this.classNode = classNode;

        for (MethodNode methodNode : classNode.methods) {
            methods.add(new MethodEdge(this, methodNode));
        }
    }

    public void discoverMethod(String name, String desc, Consumer<MethodEdge> consumer) {
        Optional<MethodEdge> nearest = findNearestMethod(name, desc);
        if (nearest.isEmpty()) {
            return;
        }

        MethodEdge start = nearest.get();
        consumer.accept(start);

        HierarchyAnalyzer.recurseParents(start.owner().parents, parentEdge -> {
            parentEdge.getMethod(name, desc).ifPresent(parentMethod -> {
                if (start.overrides(parentMethod)) {
                    consumer.accept(parentMethod);
                }
            });
        });
    }

    public Optional<MethodEdge> findNearestMethod(String name, String desc) {
        Optional<MethodEdge> firstLevel = getMethod(name, desc);
        if (firstLevel.isPresent()) {
            return firstLevel;
        }

        for (ClassEdge parent : parents) {
            Optional<MethodEdge> found = parent.findNearestMethod(name, desc);
            if (found.isPresent()) {
                return found;
            }
        }

        return Optional.empty();
    }

    public Optional<MethodEdge> getMethod(MethodNode methodNode) {
        return getMethod(methodNode.name, methodNode.desc);
    }

    public Optional<MethodEdge> getMethod(String name, String desc) {
        for (MethodEdge methodEdge : methods) {
            if (methodEdge.methodNode().name.equals(name) && methodEdge.methodNode().desc.equals(desc)) {
                return Optional.of(methodEdge);
            }
        }

        return Optional.empty();
    }
}
