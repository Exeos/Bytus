package me.exeos.bytus.asmplus.analysis.hierarchy.edge;

import me.exeos.bytus.asmplus.analysis.hierarchy.HierarchyAnalyzer;
import me.exeos.bytus.asmplus.utils.MethodUtil;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.MethodNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public record MethodEdge(ClassEdge owner, MethodNode methodNode) {

    /**
     * Return a list of MethodEdges that override this MethodEdge
     * @return
     */
    public List<MethodEdge> getOverrides() {
        List<MethodEdge> found = new ArrayList<>();

        if (MethodUtil.hasAccess(methodNode, Opcodes.ACC_FINAL)
                || MethodUtil.hasAccess(methodNode, Opcodes.ACC_STATIC)
                || MethodUtil.hasAccess(methodNode, Opcodes.ACC_PRIVATE)
        ) {
            return found;
        }

        HierarchyAnalyzer.recurseChildren(owner.children, classEdge -> {
            classEdge
                    .getMethod(methodNode.name, methodNode.desc)
                    .filter(methodEdge -> !MethodUtil.hasAccess(methodEdge.methodNode, Opcodes.ACC_STATIC))
                    .ifPresent(found::add);
        });

        return found;
    }

    public MethodEdge getRoot() {
        return getRoot(this);
    }

    private MethodEdge getRoot(MethodEdge start) {
        for (ClassEdge parent : start.owner.parents) {
            Optional<MethodEdge> maybe = parent.getMethod(start.methodNode);
            if (maybe.isPresent()) {
                return getRoot(maybe.get());
            }
        }

        return start;
    }

    public boolean overrides(MethodEdge other) {
        if (other == null) {
            return false;
        }

        return other.getOverrides().contains(this);
    }
}
