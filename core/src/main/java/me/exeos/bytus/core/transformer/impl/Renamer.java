package me.exeos.bytus.core.transformer.impl;

import me.exeos.bytus.asmplus.analysis.hierarchy.HierarchyAnalyzer;
import me.exeos.bytus.asmplus.analysis.hierarchy.edge.ClassEdge;
import me.exeos.bytus.asmplus.jar.JarArchive;
import me.exeos.bytus.asmplus.remapper.ClassRemapper;
import me.exeos.bytus.asmplus.remapper.FieldRemapper;
import me.exeos.bytus.asmplus.remapper.MethodRemapper;
import me.exeos.bytus.asmplus.remapper.mapper.impl.ClassMapper;
import me.exeos.bytus.asmplus.remapper.mapper.impl.FieldMapper;
import me.exeos.bytus.asmplus.remapper.mapper.impl.MethodMapper;
import me.exeos.bytus.asmplus.utils.AsmUtil;
import me.exeos.bytus.asmplus.utils.ClassUtil;
import me.exeos.bytus.asmplus.utils.HierarchyUtil;
import me.exeos.bytus.asmplus.utils.JarUtil;
import me.exeos.bytus.core.config.BytusConfig;
import me.exeos.bytus.core.transformer.AbstractTransformer;
import me.exeos.bytus.core.transformer.Priority;
import me.exeos.bytus.core.transformer.context.JarContext;
import me.exeos.bytus.core.utils.RandomUtil;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class Renamer extends AbstractTransformer {

    public Renamer(BytusConfig config) {
        super(config);
    }

    @Override
    public boolean applies() {
        return config.rename;
    }

    @Override
    public int priority() {
        return Priority.RENAME;
    }

    @Override
    public void transform(JarContext context) {
        Map<String, ClassEdge> hierarchy = HierarchyAnalyzer.analyzeNameMapped(context.jar());

        renameMethods(context.jar(), hierarchy);
        renameFields(context.jar(), hierarchy);
        renameClasses(context.jar());
    }

    private void renameClasses(JarArchive jar) {
        new ClassRemapper(
                ClassMapper.map(
                        jar,
                        RandomUtil::getString,
                        classNode -> isEntrypoint(jar, classNode.name)
                )
        ).remap(jar);
    }

    private void renameFields(JarArchive jar, Map<String, ClassEdge> hierarchy) {
        new FieldRemapper(
                FieldMapper.map(
                        jar,
                        RandomUtil::getString
                )
        ).remap(jar, hierarchy);
    }

    private void renameMethods(JarArchive jar, Map<String, ClassEdge> hierarchy) {
        new MethodRemapper(
                MethodMapper.map(
                        jar,
                        RandomUtil::getString,
                        mappingContext -> {
                            ClassNode classNode = mappingContext.classNode();
                            Optional<MethodNode> methodNode = mappingContext.methodNode();

                            return AsmUtil.hasAccess(classNode.access, ACC_ANNOTATION)
                                    || (ClassUtil.isEnum(classNode) && methodNode.isPresent() && (List.of("values", "valueOf").contains(methodNode.get().name)))
                                    || (methodNode.isPresent() && isEntrypoint(jar, classNode.name, methodNode.get().name, methodNode.get().desc));
                        }
                )
        ).remap(jar, hierarchy);
    }

    private boolean isEntrypoint(JarArchive archive, String className) {
        return isEntrypoint(archive, className, "main", "([Ljava/lang/String;)V");
    }

    private boolean isEntrypoint(JarArchive archive, String className, String methodName, String methodDesc) {
        if (config.entryPoints.fromManifest()) {
            Optional<String> mainMethod = JarUtil.getMainMethodFromManifest(archive);
            if (mainMethod.isPresent()) {
                if (mainMethod.get().equals(className + methodName + methodDesc)) {
                    return true;
                }
            }
        }

        return config.entryPoints.custom().keySet().stream()
                .map(entryClass -> entryClass.replace(".", "/"))
                .collect(Collectors.toSet())
                .contains(className)
                && config.entryPoints.custom().containsValue(methodName)
                && methodDesc.equals("([Ljava/lang/String;)V");
    }
}
