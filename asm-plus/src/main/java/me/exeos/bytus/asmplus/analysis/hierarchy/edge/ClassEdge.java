package me.exeos.bytus.asmplus.analysis.hierarchy.edge;

import me.exeos.bytus.asmplus.analysis.hierarchy.HierarchyAnalyzer;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Represents a class in the analyzed hierarchy.
 * <p>
 * A {@code ClassEdge} wraps an ASM {@link ClassNode} and exposes direct parent/child
 * relationships as well as the fields and methods declared by the class.
 * </p>
 */
public class ClassEdge {

    public final ClassNode classNode;

    public final List<ClassEdge> parents = new ArrayList<>();
    public final List<ClassEdge> children = new ArrayList<>();

    public final List<FieldEdge> fields = new ArrayList<>();
    public final List<MethodEdge> methods = new ArrayList<>();

    public ClassEdge(ClassNode classNode) {
        this.classNode = classNode;

        for (FieldNode fieldNode : classNode.fields) {
            fields.add(new FieldEdge(this, fieldNode));
        }

        for (MethodNode methodNode : classNode.methods) {
            methods.add(new MethodEdge(this, methodNode));
        }
    }

    public Optional<ClassEdge> findFieldsDeclaringClass(FieldNode fieldNode) {
        return findFieldsDeclaringClass(fieldNode.name, fieldNode.desc);
    }

    public Optional<ClassEdge> findFieldsDeclaringClass(String name, String desc) {
        return findNearestField(name, desc).map(FieldEdge::owner);
    }

    public Optional<FieldEdge> findNearestField(FieldNode fieldNode) {
        return findNearestField(fieldNode.name, fieldNode.desc);
    }

    public Optional<FieldEdge> findNearestField(String name, String desc) {
        Optional<FieldEdge> firstLevel = getField(name, desc);
        if (firstLevel.isPresent()) {
            return firstLevel;
        }

        for (ClassEdge parent : parents) {
            Optional<FieldEdge> parentLevel = parent.findNearestField(name, desc);
            if (parentLevel.isPresent()) {
                return parentLevel;
            }
        }

        return Optional.empty();
    }

    /**
     * Discovers the method matching the given name and descriptor on this class or its
     * overridden ancestor methods, and passes each matched edge to the provided consumer.
     *
     * @param name The method name
     * @param desc The method descriptor
     * @param consumer Callback invoked for each discovered method edge
     */
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

    public Optional<ClassEdge> findMethodsDeclaringClass(MethodNode methodNode) {
        return findMethodsDeclaringClass(methodNode.name, methodNode.desc);
    }

    public Optional<ClassEdge> findMethodsDeclaringClass(String name, String desc) {
        return findNearestMethod(name, desc).map(MethodEdge::owner);
    }

    /**
     * Finds the nearest method with the same name and descriptor as the given method node.
     *
     * @param methodNode the method node to match
     * @return the nearest matching method, if one exists
     */
    public Optional<MethodEdge> findNearestMethod(MethodNode methodNode) {
        return findNearestMethod(methodNode.name, methodNode.desc);
    }

    /**
     * Finds the nearest method with the given name and descriptor in this class hierarchy.
     * <p>
     * This first checks the current class, then recursively searches parent classes/interfaces
     * until a matching method is found.
     * </p>
     *
     * @param name The method name
     * @param desc The method descriptor
     * @return The nearest matching method, if one exists
     */
    public Optional<MethodEdge> findNearestMethod(String name, String desc) {
        Optional<MethodEdge> firstLevel = getMethod(name, desc);
        if (firstLevel.isPresent()) {
            return firstLevel;
        }

        for (ClassEdge parent : parents) {
            Optional<MethodEdge> parentLevel = parent.findNearestMethod(name, desc);
            if (parentLevel.isPresent()) {
                return parentLevel;
            }
        }

        return Optional.empty();
    }

    public Optional<FieldEdge> getField(FieldNode fieldNode) {
        return getField(fieldNode.name, fieldNode.desc);
    }

    public Optional<FieldEdge> getField(String name, String desc) {
        for (FieldEdge fieldEdge : fields) {
            if (fieldEdge.fieldNode().name.equals(name) && fieldEdge.fieldNode().desc.equals(desc)) {
                return Optional.of(fieldEdge);
            }
        }

        return Optional.empty();
    }

    /**
     * Returns the method in this class matching the given method node's name and descriptor.
     *
     * @param methodNode the method node to match
     * @return the matching method, if one exists
     */
    public Optional<MethodEdge> getMethod(MethodNode methodNode) {
        return getMethod(methodNode.name, methodNode.desc);
    }

    /**
     * Returns a method declared directly in this class, if one matches the given name and descriptor.
     *
     * @param name The method name
     * @param desc The method descriptor
     * @return The matching method, if one exists
     */
    public Optional<MethodEdge> getMethod(String name, String desc) {
        for (MethodEdge methodEdge : methods) {
            if (methodEdge.methodNode().name.equals(name) && methodEdge.methodNode().desc.equals(desc)) {
                return Optional.of(methodEdge);
            }
        }

        return Optional.empty();
    }
}
