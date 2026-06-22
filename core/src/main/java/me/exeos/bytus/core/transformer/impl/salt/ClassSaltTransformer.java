package me.exeos.bytus.core.transformer.impl.salt;

import me.exeos.bytus.asmplus.analysis.hierarchy.edge.ClassEdge;
import me.exeos.bytus.asmplus.analysis.init.ClassInitAnalyzer;
import me.exeos.bytus.asmplus.jar.JarArchive;
import me.exeos.bytus.asmplus.utils.*;
import me.exeos.bytus.core.config.BytusConfig;
import me.exeos.bytus.core.transformer.AbstractTransformer;
import me.exeos.bytus.core.transformer.Priority;
import me.exeos.bytus.core.transformer.context.JarContext;
import me.exeos.bytus.core.utils.RandomUtil;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

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
        if (initMap == null) {
            return;
        }

        Map<String, Integer> classSaltMap = new HashMap<>();
        Map<String, String> saltFieldNameMap = new HashMap<>();
        mapSaltAndCreateField(context, context.getExtension().getHierarchy(), classSaltMap, saltFieldNameMap);

        initClassSaltFields(jar, initMap, classSaltMap, saltFieldNameMap);
    }

    private Map<String, Set<String>> buildInitMap(JarArchive jar) {
        AtomicReference<Map<String, Set<String>>> initMap = new AtomicReference<>(null);

        config.getEntryPoints(jar).stream().findFirst().ifPresent(entry -> {
            JarUtil.findClass(jar, entry.owner()).ifPresent(entryClass -> {
                ClassUtil.findMethod(entryClass, entry.name(), entry.desc()).ifPresent(entryMethod -> {
                    initMap.set(ClassInitAnalyzer.analyzeInitOrder(jar, entryClass, entryMethod));
                });
            });
        });

        return initMap.get();
    }

    private void mapSaltAndCreateField(JarContext context, Map<ClassNode, ClassEdge> hierarchy, Map<String, Integer> classSaltMap, Map<String, String> saltFieldNameMap) {
        for (ClassNode classNode : context.jar().getClasses().values()) {
            FieldNode saltField = new FieldNode(
                    Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                    HierarchyUtil.genNoneCollidingFieldName(
                            hierarchy.get(classNode),
                            SALT_FIELD_DESC,
                            RandomUtil::getString
                    ),
                    SALT_FIELD_DESC,
                    null,
                    null
            );
            int salt = RandomUtil.getInt();

            classNode.fields.add(saltField);

            classSaltMap.put(classNode.name, salt);
            saltFieldNameMap.put(classNode.name, saltField.name);
            context.pipeline().getExtension(classNode).saltInfo().setSalt(salt, classNode.name, saltField.name, SALT_FIELD_DESC);
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

            String initPick = RandomUtil.getRandomEntry(initMap.get(classNode.name));

            int currentSalt = classSaltMap.get(classNode.name);

            InsnList saltStoreInsn = new InsnList();

            if (initPick != null && classSaltMap.containsKey(initPick)) {
                // calc diff from initializing class picked salt and current class salt
                int diff = classSaltMap.get(initPick) - currentSalt;

                // get salt field from initializing class picked
                saltStoreInsn.add(new FieldInsnNode(Opcodes.GETSTATIC, initPick, saltFieldNameMap.get(initPick), SALT_FIELD_DESC));
                // use diff to create salt for current class
                if (diff != 0) {
                    saltStoreInsn.add(InsnUtil.getIntPush(Math.abs(diff)));
                    saltStoreInsn.add(new InsnNode(diff < 0 ? Opcodes.IADD : Opcodes.ISUB));
                }
            } else {
                saltStoreInsn.add(InsnUtil.getIntPush(currentSalt));
            }
            saltStoreInsn.add(new FieldInsnNode(Opcodes.PUTSTATIC, classNode.name, saltFieldNameMap.get(classNode.name), SALT_FIELD_DESC));

            clinit.instructions.insertBefore(clinit.instructions.getFirst(), saltStoreInsn);
        }
    }
}