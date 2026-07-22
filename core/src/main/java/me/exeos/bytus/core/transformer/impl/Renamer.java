package me.exeos.bytus.core.transformer.impl;

import me.exeos.asmplus.jar.JarArchive;
import me.exeos.asmplus.remapper.ClassRemapper;
import me.exeos.asmplus.remapper.FieldRemapper;
import me.exeos.asmplus.remapper.MethodRemapper;
import me.exeos.asmplus.remapper.mapper.impl.ClassMapper;
import me.exeos.asmplus.remapper.mapper.impl.FieldMapper;
import me.exeos.asmplus.remapper.mapper.impl.MethodMapper;
import me.exeos.asmplus.utils.AsmUtil;
import me.exeos.asmplus.utils.ClassUtil;
import me.exeos.asmplus.utils.JarUtil;
import me.exeos.bytus.core.config.BytusConfig;
import me.exeos.bytus.core.transformer.AbstractTransformer;
import me.exeos.bytus.core.transformer.Priority;
import me.exeos.bytus.core.transformer.context.JarContext;
import me.exeos.bytus.core.utils.RandomUtil;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.*;
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
        renameMethods(context);
        renameFields(context);
        renameClasses(context);

        // needs to be invalidated because classnames changed
        context.getExtension().invalidateHierarchy();
    }

    private void renameClasses(JarContext context) {
        var mapping = ClassMapper.map(
                context.jar(),
                RandomUtil::getString,
                classNode -> isEntrypoint(context.jar(), classNode.name)
        );

        new ClassRemapper(mapping).remap(context.jar());

        // remap class init order provided by user
        Map<String, Set<String>> remappedInitOrder = new HashMap<>();
        for (Map.Entry<String, Set<String>> entry : config.classInitOrder.entrySet()) {
            String key = mapping.getOrDefault(entry.getKey(), entry.getKey());
            for (String value : entry.getValue()) {
                remappedInitOrder.computeIfAbsent(
                        key, _ -> new HashSet<>()
                ).add(mapping.getOrDefault(value, value));
            }
        }

        config.classInitOrder.clear();
        config.classInitOrder.putAll(remappedInitOrder);
    }

    private void renameFields(JarContext context) {
        new FieldRemapper(
                FieldMapper.map(
                        context.jar(),
                        context.getExtension().getHierarchy(),
                        RandomUtil::getString
                )
        ).remap(context.jar(), context.getExtension().getHierarchyNameMapped());
    }

    private void renameMethods(JarContext context) {
        new MethodRemapper(
                MethodMapper.map(
                        context.jar(),
                        context.getExtension().getHierarchyNameMapped(),
                        RandomUtil::getString,
                        mappingContext -> {
                            ClassNode classNode = mappingContext.classNode();
                            Optional<MethodNode> methodNode = mappingContext.methodNode();

                            return AsmUtil.hasAccess(classNode.access, ACC_ANNOTATION)
                                    || (ClassUtil.isEnum(classNode) && methodNode.isPresent() && (List.of("values", "valueOf").contains(methodNode.get().name)))
                                    || (methodNode.isPresent() && isEntrypoint(context.jar(), classNode.name, methodNode.get().name, methodNode.get().desc));
                        }
                )
        ).remap(context.jar(), context.getExtension().getHierarchyNameMapped());
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
