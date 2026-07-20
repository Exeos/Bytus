package me.exeos.bytus.core.transformer.extensions;

import me.exeos.asmplus.analysis.hierarchy.HierarchyAnalyzer;
import me.exeos.asmplus.analysis.hierarchy.edge.ClassEdge;
import me.exeos.asmplus.jar.JarArchive;
import org.objectweb.asm.tree.ClassNode;

import java.util.HashMap;
import java.util.Map;

public class JarExtension {

    private Map<String, ClassEdge> hierarchyNameMapped;
    private Map<ClassNode, ClassEdge> hierarchyClassMapped;

    private boolean hierarchyValid = false;
    private boolean hierarchyNameMapValid = false;
    private final JarArchive jar;

    public JarExtension(JarArchive jar) {
        this.jar = jar;
    }

    public Map<String, ClassEdge> getHierarchyNameMapped() {
        ensureHierarchy();
        return hierarchyNameMapped;
    }

    public Map<ClassNode, ClassEdge> getHierarchy() {
        ensureHierarchy();
        return hierarchyClassMapped;
    }

    public void invalidateHierarchy() {
        hierarchyValid = false;
        hierarchyNameMapValid = false;
    }

    public void invalidateHierarchyNameMap() {
        hierarchyNameMapValid = false;
    }

    private void ensureHierarchy() {
        if (!hierarchyValid) {
            hierarchyValid = true;
            hierarchyClassMapped = HierarchyAnalyzer.analyze(jar);
        }

        if (!hierarchyNameMapValid) {
            hierarchyNameMapValid = true;
            hierarchyNameMapped = new HashMap<>();
            for (Map.Entry<ClassNode, ClassEdge> entry : hierarchyClassMapped.entrySet()) {
                hierarchyNameMapped.put(entry.getKey().name, entry.getValue());
            }
        }
    }
}
