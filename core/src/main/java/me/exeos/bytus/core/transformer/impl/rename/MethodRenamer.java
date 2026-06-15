package me.exeos.bytus.core.transformer.impl.rename;

import me.exeos.bytus.asmplus.analysis.hierarchy.HierarchyAnalyzer;
import me.exeos.bytus.asmplus.analysis.hierarchy.edge.ClassEdge;
import me.exeos.bytus.asmplus.analysis.hierarchy.edge.MethodEdge;
import me.exeos.bytus.asmplus.jar.JarArchive;
import me.exeos.bytus.asmplus.remapper.MethodRemapper;
import me.exeos.bytus.asmplus.utils.AsmUtil;
import me.exeos.bytus.asmplus.utils.ClassUtil;
import me.exeos.bytus.asmplus.utils.JarUtil;
import me.exeos.bytus.asmplus.utils.MethodUtil;
import me.exeos.bytus.core.config.BytusConfig;
import me.exeos.bytus.core.transformer.AbstractTransformer;
import me.exeos.bytus.core.transformer.context.JarContext;
import me.exeos.bytus.core.utils.RandomUtil;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.*;

public class MethodRenamer extends AbstractTransformer {

    public MethodRenamer(BytusConfig config) {
        super(config);
    }

    @Override
    public boolean applies() {
        return true;
    }

    @Override
    public int priority() {
        return 1;
    }

    @Override
    public void transform(JarContext context) {
        Map<String, String> mappings = new HashMap<>();
        Map<ClassNode, ClassEdge> hierarchy = HierarchyAnalyzer.analyze(context.jar());
        Map<ClassEdge, Set<String>> usedNames = new HashMap<>();
        for (ClassNode classNode : context.jar().getClasses().values()) {
            if (!hierarchy.containsKey(classNode) || ClassUtil.isEnum(classNode) || AsmUtil.hasAccess(classNode.access, ACC_ANNOTATION)) {
                continue;
            }

            Set<String> used = mergeRelevantUsed(hierarchy.get(classNode), usedNames);
            for (MethodNode methodNode : classNode.methods) {
                String key = classNode.name + methodNode.name + methodNode.desc;
                if (MethodUtil.isSpecial(methodNode) || isEntrypoint(context.jar(), classNode.name, methodNode.name, methodNode.desc)) {
                    mappings.put(key, methodNode.name);
                    continue;
                }

                String name;
                int tries = 1;
                do {
                    name = RandomUtil.getString(tries++);
                } while (used.contains(name));
                used.add(name);
                usedNames.computeIfAbsent(hierarchy.get(classNode), _ -> new HashSet<>()).add(name);

                mappings.put(key, name);
            }
        }

        for (ClassNode classNode : context.jar().getClasses().values()) {
            if (!hierarchy.containsKey(classNode)) {
                continue;
            }

            for (MethodNode methodNode : classNode.methods) {
                hierarchy.get(classNode).getMethod(methodNode).ifPresent(methodEdge -> {
                    MethodEdge root = methodEdge.getRoot();
                    if (mappings.containsKey(root.owner().classNode.name + root.methodNode().name + root.methodNode().desc)) {
                        mappings.put(
                                classNode.name + methodNode.name + methodNode.desc,
                                mappings.get(root.owner().classNode.name + root.methodNode().name + root.methodNode().desc)
                        );
                    }
                });
            }
        }

        new MethodRemapper(mappings).remap(context.jar());
    }

    private Set<String> mergeRelevantUsed(ClassEdge edge, Map<ClassEdge, Set<String>> used) {
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

    private boolean isEntrypoint(JarArchive archive, String className, String methodName, String methodDesc) {
        if (config.entryPoints.fromManifest()) {
            Optional<String> mainMethod = JarUtil.getMainMethodFromManifest(archive);
            if (mainMethod.isPresent()) {
                String sig = className + methodName + methodDesc;
                if (mainMethod.get().equals(sig)) {
                    return true;
                }
            }
        }

        return config.entryPoints.custom().containsKey(className.replaceAll("/", "."))
                && methodName.equals("main")
                && methodDesc.equals("([Ljava/lang/String;)V");
    }
}
