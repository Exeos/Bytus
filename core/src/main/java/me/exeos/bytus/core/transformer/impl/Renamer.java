package me.exeos.bytus.core.transformer.impl;

import me.exeos.bytus.asmplus.jar.JarArchive;
import me.exeos.bytus.asmplus.remapper.ClassRemapper;
import me.exeos.bytus.asmplus.remapper.FieldRemapper;
import me.exeos.bytus.asmplus.remapper.MethodRemapper;
import me.exeos.bytus.asmplus.remapper.mapper.impl.ClassMapper;
import me.exeos.bytus.asmplus.remapper.mapper.impl.FieldMapper;
import me.exeos.bytus.asmplus.remapper.mapper.impl.MethodMapper;
import me.exeos.bytus.asmplus.utils.AsmUtil;
import me.exeos.bytus.asmplus.utils.ClassUtil;
import me.exeos.bytus.asmplus.utils.JarUtil;
import me.exeos.bytus.core.config.BytusConfig;
import me.exeos.bytus.core.transformer.AbstractTransformer;
import me.exeos.bytus.core.transformer.Priority;
import me.exeos.bytus.core.transformer.context.JarContext;
import me.exeos.bytus.core.utils.RandomUtil;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.List;
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
        renameMethods(context);
        renameFields(context);
        renameClasses(context);

        // needs to be invalidated because classnames changed
        context.getExtension().invalidateHierarchyNameMap();
    }

    private void renameClasses(JarContext context) {
        new ClassRemapper(
                ClassMapper.map(
                        context.jar(),
                        RandomUtil::getString,
                        classNode -> isEntrypoint(context.jar(), classNode.name)
                )
        ).remap(context.jar());
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
