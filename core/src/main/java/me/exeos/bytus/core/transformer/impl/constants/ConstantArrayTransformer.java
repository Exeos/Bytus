package me.exeos.bytus.core.transformer.impl.constants;

import me.exeos.bytus.asmplus.analysis.hierarchy.edge.ClassEdge;
import me.exeos.bytus.asmplus.jar.JarArchive;
import me.exeos.bytus.asmplus.utils.ClassUtil;
import me.exeos.bytus.asmplus.utils.HierarchyUtil;
import me.exeos.bytus.asmplus.utils.InsnUtil;
import me.exeos.bytus.core.config.BytusConfig;
import me.exeos.bytus.core.transformer.AbstractTransformer;
import me.exeos.bytus.core.transformer.Priority;
import me.exeos.bytus.core.transformer.context.ClassContext;
import me.exeos.bytus.core.utils.RandomUtil;
import org.objectweb.asm.tree.*;

import java.util.*;

/**
 * Idea: every constant ie. numbers, strings in the whole class
 * get put into a field which is an object array and the ldc gets
 * replaced with a getstatic and an array load.
 * the key would be somehow randomized, and could be further encrypted
 * <p>
 * Step 1. collect all constants in a list
 * Step 2. shuffle the list
 * Step 3. initialize the array field with the constants in the static block
 * Step 4. replace all ldc instructions with getstatic and array load
 */
public final class ConstantArrayTransformer extends AbstractTransformer {

    private final static String CONST_FIELD_DESC = "[Ljava/lang/Object;";
    
    public ConstantArrayTransformer(BytusConfig config) {
        super(config);
    }

    @Override
    public boolean applies() {
        return config.constants.enable() && config.constants.constantArray();
    }

    @Override
    public int priority() {
        return Priority.CONST_ARRAY;
    }

    @Override
    public void transform(ClassContext context) {
        Set<Object> constants = this.collectConstants(context.classNode());
        ClassEdge owner = context.jarCtx().getExtension().getHierarchy().get(context.classNode());
        if (constants.isEmpty() || owner == null) return;

        Map<Object, Integer> constantIndexMap = this.assignIndices(constants);
        
        String constantFieldName = HierarchyUtil.genNoneCollidingFieldName(owner, CONST_FIELD_DESC, RandomUtil::getString);

        replaceConstants(context.classNode(), constantIndexMap, constantFieldName);
        context.classNode().fields.add(new FieldNode(ACC_PRIVATE | ACC_STATIC, constantFieldName, CONST_FIELD_DESC, null, null));

        MethodNode clinit = ClassUtil.getOrCreateStaticInitializer(context.classNode());
        InsnList initializer = this.buildFieldInitializer(context.jarCtx().jar(), context.classNode(), owner, constantIndexMap, constantFieldName);
        clinit.instructions.insertBefore(clinit.instructions.getFirst(), initializer);
    }

    private void replaceConstants(ClassNode classNode, Map<Object, Integer> constants, String constantFieldName) {
        classNode.methods.forEach(methodNode -> {
            InsnUtil.loop(methodNode.instructions, insnNode -> {
                Object cst = null;

                if (insnNode instanceof LdcInsnNode ldcNode && constants.containsKey(ldcNode.cst)) {
                    cst = ldcNode.cst;
                } else {
                    Optional<Integer> intVal = InsnUtil.getIntValue(insnNode);
                    if (intVal.isPresent() && constants.containsKey(intVal.get())) {
                        cst = intVal.get();
                    }
                }

                if (cst == null) {
                    return;
                }

                int index = constants.get(cst);
                InsnList replacement = new InsnList();
                replacement.add(new FieldInsnNode(GETSTATIC, classNode.name, constantFieldName, CONST_FIELD_DESC));
                replacement.add(InsnUtil.getIntPush(index));
                replacement.add(new InsnNode(AALOAD));

                switch (cst) {
                    case Integer i -> {
                        replacement.add(new TypeInsnNode(CHECKCAST, "java/lang/Integer"));
                        replacement.add(new MethodInsnNode(INVOKEVIRTUAL, "java/lang/Integer", "intValue", "()I", false));
                    }
                    case Long l -> {
                        replacement.add(new TypeInsnNode(CHECKCAST, "java/lang/Long"));
                        replacement.add(new MethodInsnNode(INVOKEVIRTUAL, "java/lang/Long", "longValue", "()J", false));
                    }
                    case Float v -> {
                        replacement.add(new TypeInsnNode(CHECKCAST, "java/lang/Float"));
                        replacement.add(new MethodInsnNode(INVOKEVIRTUAL, "java/lang/Float", "floatValue", "()F", false));
                    }
                    case Double v -> {
                        replacement.add(new TypeInsnNode(CHECKCAST, "java/lang/Double"));
                        replacement.add(new MethodInsnNode(INVOKEVIRTUAL, "java/lang/Double", "doubleValue", "()D", false));
                    }
                    case String s -> replacement.add(new TypeInsnNode(CHECKCAST, "java/lang/String"));
                    default ->
                        // For any other reference type (e.g. Type/MethodHandle constants)
                            replacement.add(new TypeInsnNode(CHECKCAST, cst.getClass().getName().replace('.', '/')));
                }

                methodNode.instructions.insertBefore(insnNode, replacement);
                methodNode.instructions.remove(insnNode);
            });
        });
    }

    private InsnList buildFieldInitializer(JarArchive jar, ClassNode classNode, ClassEdge ownerEdge, Map<Object, Integer> constants, String constantFieldName) {
        InsnList list = new InsnList();

        list.add(new LdcInsnNode(constants.size()));
        list.add(new TypeInsnNode(ANEWARRAY, "java/lang/Object"));
        list.add(new FieldInsnNode(PUTSTATIC, classNode.name, constantFieldName, CONST_FIELD_DESC));

        int maxPerMethod = 500;
        int current = 0;
        MethodNode currentMethod = null;

        for (Map.Entry<Object, Integer> entry : constants.entrySet()) {
            if (currentMethod == null || current > maxPerMethod) {
                if (currentMethod != null) {
                    // insert return at the end
                    currentMethod.instructions.add(new InsnNode(RETURN));
                }
                currentMethod = new MethodNode(
                        ACC_STATIC | ACC_PRIVATE,
                        HierarchyUtil.genNoneCollidingMethodName(ownerEdge, "()V", RandomUtil::getString),
                        "()V",
                        null,
                        null
                );
                classNode.methods.add(currentMethod);
                current = 0;
                list.add(new MethodInsnNode(INVOKESTATIC, classNode.name, currentMethod.name, currentMethod.desc, false));
            }

            currentMethod.instructions.add(new FieldInsnNode(GETSTATIC, classNode.name, constantFieldName, CONST_FIELD_DESC));
            currentMethod.instructions.add(new LdcInsnNode(entry.getValue()));
            currentMethod.instructions.add(new LdcInsnNode(entry.getKey()));

            // check if its a primitive, if so we need to call valueOf method
            if (entry.getKey() instanceof Integer) {
                currentMethod.instructions.add(new MethodInsnNode(INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false));
            } else if (entry.getKey() instanceof Long) {
                currentMethod.instructions.add(new MethodInsnNode(INVOKESTATIC, "java/lang/Long", "valueOf", "(J)Ljava/lang/Long;", false));
            } else if (entry.getKey() instanceof Float) {
                currentMethod.instructions.add(new MethodInsnNode(INVOKESTATIC, "java/lang/Float", "valueOf", "(F)Ljava/lang/Float;", false));
            } else if (entry.getKey() instanceof Double) {
                currentMethod.instructions.add(new MethodInsnNode(INVOKESTATIC, "java/lang/Double", "valueOf", "(D)Ljava/lang/Double;", false));
            } else if (entry.getKey() instanceof Boolean) {
                currentMethod.instructions.add(new MethodInsnNode(INVOKESTATIC, "java/lang/Boolean", "valueOf", "(Z)Ljava/lang/Boolean;", false));
            } else if (entry.getKey() instanceof Character) {
                currentMethod.instructions.add(new MethodInsnNode(INVOKESTATIC, "java/lang/Character", "valueOf", "(C)Ljava/lang/Character;", false));
            }

            currentMethod.instructions.add(new InsnNode(AASTORE));

            current++;
        }

        if (currentMethod != null && currentMethod.instructions.getLast().getOpcode() != RETURN) {
            currentMethod.instructions.add(new InsnNode(RETURN));
//            classNode.methods.add(currentMethod);
            list.add(new MethodInsnNode(INVOKESTATIC, classNode.name, currentMethod.name, currentMethod.desc, false));
        }

        return list;
    }

    private Set<Object> collectConstants(ClassNode classNode) {
        Set<Object> constants = new HashSet<>();
        classNode.methods.forEach(methodNode -> methodNode.instructions.forEach(instruction -> {
            if (instruction instanceof LdcInsnNode ldc) {
                // Only handle primitives and strings; skip Type/Handle/etc.
                if (!(ldc.cst instanceof String || ldc.cst instanceof Integer ||
                        ldc.cst instanceof Long || ldc.cst instanceof Float ||
                        ldc.cst instanceof Double)) return;
                constants.add(ldc.cst);
                return;
            }
            Optional<Integer> intValue = InsnUtil.getIntValue(instruction);
            if (intValue.isEmpty()) return;
            constants.add(intValue.get());

            // System.out.println("found int constant " + intValue.get());

        }));
        return constants;
    }

    private Map<Object, Integer> assignIndices(Set<Object> constants) {
        List<Object> list = new ArrayList<>(constants);
        Collections.shuffle(list);
        Map<Object, Integer> indices = new HashMap<>();
        for (int i = 0; i < list.size(); i++) {
            indices.put(list.get(i), i);
        }
        return indices;
    }
}
