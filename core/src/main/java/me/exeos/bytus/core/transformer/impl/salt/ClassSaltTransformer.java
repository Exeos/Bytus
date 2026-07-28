package me.exeos.bytus.core.transformer.impl.salt;

import me.exeos.asmplus.analysis.hierarchy.edge.ClassEdge;
import me.exeos.asmplus.analysis.init.ClassInitAnalyzer;
import me.exeos.asmplus.codegen.value.impl.ConstantPusher;
import me.exeos.asmplus.jar.JarArchive;
import me.exeos.asmplus.matcher.method.MethodMatchEntry;
import me.exeos.asmplus.utils.AsmUtil;
import me.exeos.asmplus.utils.ClassUtil;
import me.exeos.asmplus.utils.HierarchyUtil;
import me.exeos.bytus.core.asm.ObfCodenGen;
import me.exeos.bytus.core.config.BytusConfig;
import me.exeos.bytus.core.transformer.AbstractTransformer;
import me.exeos.bytus.core.transformer.Priority;
import me.exeos.bytus.core.transformer.context.JarContext;
import me.exeos.bytus.core.transformer.extensions.ClassExtension;
import me.exeos.bytus.core.utils.RandomUtil;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class ClassSaltTransformer extends AbstractTransformer {

    private static final String SALT_FIELD_DESC = "I";

    public ClassSaltTransformer(BytusConfig config) {
        super(config);
    }

    @Override
    public boolean applies() {
        return config.salt;
    }

    @Override
    public int priority() {
        return Priority.SALT_CLASS;
    }

    @Override
    public void transform(JarContext context) {
        JarArchive jar = context.jar();

        // build map mapping class name to classes that initialize it
        Map<String, Set<String>> initMap = buildInitMap(context.jar());
        Map<String, Integer> classSaltMap = new HashMap<>();
        Map<String, String> saltFieldNameMap = new HashMap<>();

        mapSaltAndCreateField(context, context.getExtension().getHierarchy(), initMap, classSaltMap, saltFieldNameMap);
        initClassSaltFields(jar, initMap, classSaltMap, saltFieldNameMap);
    }

    private Map<String, Set<String>> buildInitMap(JarArchive jar) {
        Map<String, Set<String>> initMap = new HashMap<>(config.classInitOrder);
        Set<MethodMatchEntry> entries = config.getEntryPoints(jar);

        if (entries.size() != 1) {
            System.out.println("Can't analyze InitMap with more than one entry point");
            return initMap;
        }

        entries.stream().findFirst().ifPresent(entry -> {
            jar.getClassNode(entry.owner(), false).ifPresent(entryClass -> {
                ClassUtil.findMethod(entryClass, entry.name(), entry.desc()).ifPresent(entryMethod -> {
                    initMap.putAll(ClassInitAnalyzer.analyzeInitOrder(jar, entryClass, entryMethod));
                });
            });
        });

        return initMap;
    }

    private void mapSaltAndCreateField(JarContext context, Map<ClassNode, ClassEdge> hierarchy, Map<String, Set<String>> initMap, Map<String, Integer> classSaltMap, Map<String, String> saltFieldNameMap) {
        for (ClassNode classNode : context.jar().getClasses().values()) {
            int access = ACC_PUBLIC | ACC_STATIC;
            if (AsmUtil.hasAccess(classNode.access, ACC_INTERFACE)) {
                access = access | ACC_FINAL;
            }

            FieldNode saltField = new FieldNode(
                    access,
                    HierarchyUtil.genNoneCollidingFieldName(
                            hierarchy.get(classNode),
                            SALT_FIELD_DESC,
                            RandomUtil::getString,
                            false
                    ),
                    SALT_FIELD_DESC,
                    null,
                    null
            );
            int salt = RandomUtil.getInt(ObfCodenGen.SAFE_MIN, ObfCodenGen.SAFE_MAX);

            classNode.fields.add(saltField);

            classSaltMap.put(classNode.name, salt);
            saltFieldNameMap.put(classNode.name, saltField.name);
            context.pipeline().getExtension(classNode).saltInfo().setSalt(salt, classNode.name, saltField.name, SALT_FIELD_DESC);
            context.pipeline().getExtension(classNode, saltField).isSaltField = true;
        }

        for (ClassNode classNode : context.jar().getClasses().values()) {
            Set<String> predecessors = initMap.get(classNode.name);
            if (predecessors == null || predecessors.isEmpty()) {
                continue;
            }

            Set<ClassExtension.ClassSaltInfo> preInitSalts = predecessors.stream()
                    .map(context.jar()::getClassNode)
                    .flatMap(Optional::stream)
                    .map(node -> context.pipeline().getExtension(node).saltInfo())
                    .collect(Collectors.toSet());

            context.pipeline().getExtension(classNode).saltInfo().setPreInitingSalts(preInitSalts);
        }
    }

    private void initClassSaltFields(
            JarArchive jar,
            Map<String, Set<String>> initMap,
            Map<String, Integer> classSaltMap,
            Map<String, String> saltFieldNameMap
    ) {
        for (ClassNode classNode : jar.getClasses().values()) {
            MethodNode clinit = ClassUtil.getOrCreateStaticInitializer(classNode);
            String initPick = RandomUtil.getRandomEntry(initMap.get(classNode.name), classNode.name);
            int currentSalt = classSaltMap.get(classNode.name);

            InsnList saltStoreInsn = new InsnList();

            if (initPick != null && classSaltMap.containsKey(initPick)) {
                // calc diff from initializing class picked salt and current class salt
                int diff = classSaltMap.get(initPick) - currentSalt;

                // get salt field from initializing class picked
                saltStoreInsn.add(new FieldInsnNode(Opcodes.GETSTATIC, initPick, saltFieldNameMap.get(initPick), SALT_FIELD_DESC));
                // use diff to create salt for current class
                if (diff != 0) {
                    saltStoreInsn.add(ConstantPusher.getIntPush(Math.abs(diff)));
                    saltStoreInsn.add(new InsnNode(diff < 0 ? Opcodes.IADD : Opcodes.ISUB));
                }
            } else {
                saltStoreInsn.add(ConstantPusher.getIntPush(currentSalt));
            }
            saltStoreInsn.add(new FieldInsnNode(Opcodes.PUTSTATIC, classNode.name, saltFieldNameMap.get(classNode.name), SALT_FIELD_DESC));

            clinit.instructions.insertBefore(clinit.instructions.getFirst(), saltStoreInsn);
        }
    }
}