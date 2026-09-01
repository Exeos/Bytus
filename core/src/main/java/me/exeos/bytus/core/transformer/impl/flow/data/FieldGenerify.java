package me.exeos.bytus.core.transformer.impl.flow.data;

import me.exeos.asmplus.analysis.hierarchy.HierarchyAnalyzer;
import me.exeos.asmplus.analysis.hierarchy.edge.ClassEdge;
import me.exeos.asmplus.analysis.hierarchy.edge.FieldEdge;
import me.exeos.asmplus.codegen.value.impl.ConstantPusher;
import me.exeos.asmplus.descriptor.DescriptorMember;
import me.exeos.asmplus.descriptor.DescriptorParser;
import me.exeos.asmplus.remapper.mapper.MemberKey;
import me.exeos.asmplus.utils.AsmUtil;
import me.exeos.asmplus.utils.ClassUtil;
import me.exeos.asmplus.utils.InsnUtil;
import me.exeos.asmplus.utils.MapperUtil;
import me.exeos.bytus.core.config.BytusConfig;
import me.exeos.bytus.core.transformer.AbstractTransformer;
import me.exeos.bytus.core.transformer.context.JarContext;
import me.exeos.bytus.core.utils.NameUtil;
import org.objectweb.asm.tree.*;

import java.util.*;

public class FieldGenerify extends AbstractTransformer {

    public FieldGenerify(BytusConfig config) {
        super(config);
    }

    @Override
    public boolean applies() {
        return true;
    }

    @Override
    public int priority() {
        return 67;
    }

    @Override
    public void transform(JarContext context) {
        Map<MemberKey, GenerifiedField> generifiedFields = collectFields(context);
        remapFieldUsage(context, generifiedFields);
    }

    private Map<MemberKey, GenerifiedField> collectFields(JarContext context) {
        Map<ClassNode, ClassEdge> hierarchy = context.getExtension().getHierarchy();
        Map<MemberKey, GenerifiedField> generifiedFields = new HashMap<>();
        Map<ClassEdge, Set<String>> usedFieldNames = new HashMap<>();

        for (ClassNode classNode : context.jar().getClasses().values()) {
            ClassEdge classEdge = hierarchy.get(classNode);
            if (classEdge == null || classEdge.hasUnresolved()) {
                continue;
            }

            String generifiedName = MapperUtil.genName(NameUtil::getName, usedFieldNames.getOrDefault(classEdge, Set.of()));
            String generifiedNameStatic = MapperUtil.genName(NameUtil::getName, usedFieldNames.getOrDefault(classEdge, Set.of()));

            InsnList initFields = new InsnList();
            InsnList initFieldsStatic = new InsnList();
            for (int i = 0; i < classNode.fields.size(); i++) {
                FieldNode fieldNode = classNode.fields.get(i);
                boolean isFieldStatic = AsmUtil.hasAccess(fieldNode.access, ACC_STATIC);
                String corrGenName = isFieldStatic ? generifiedNameStatic : generifiedName;
                DescriptorMember desc = DescriptorParser.parseFieldDesc(fieldNode.desc);

                generifiedFields.put(
                        new MemberKey(classNode.name, fieldNode.name, fieldNode.desc),
                        new GenerifiedField(classNode.name, corrGenName, isFieldStatic, desc, i)
                );

                if (isFieldStatic) {
                    initFieldsStatic.add(new FieldInsnNode(GETSTATIC, classNode.name, corrGenName, "[Ljava/lang/Object;"));
                    initFieldsStatic.add(ConstantPusher.getIntPush(i));
                    initFieldsStatic.add(defaultValue(desc));
                    initFieldsStatic.add(new InsnNode(AASTORE));
                } else {
                    initFields.add(new VarInsnNode(ALOAD, 0));
                    initFields.add(new FieldInsnNode(GETFIELD, classNode.name, corrGenName, "[Ljava/lang/Object;"));
                    initFields.add(ConstantPusher.getIntPush(i));
                    initFields.add(defaultValue(desc));
                    initFields.add(new InsnNode(AASTORE));
                }
            }

            MethodNode clinit = ClassUtil.getOrCreateStaticInitializer(classNode);
            clinit.instructions.insertBefore(clinit.instructions.getFirst(), initFieldsStatic);

            for (MethodNode constructor : ClassUtil.getConstructors(classNode)) {
                AbstractInsnNode safePoint = safeConstInsertPoint(constructor);
                if (safePoint == null) {
                    constructor.instructions.insertBefore(constructor.instructions.getFirst(), InsnUtil.copy(initFields));
                } else {
                    constructor.instructions.insert(safePoint, InsnUtil.copy(initFields));
                }
            }

            classNode.fields.clear();
            classNode.fields.add(new FieldNode(ACC_PUBLIC, generifiedName, "[Ljava/lang/Object;", null, null));
            classNode.fields.add(new FieldNode(ACC_PUBLIC | ACC_STATIC, generifiedNameStatic, "[Ljava/lang/Object;", null, null));

            HierarchyAnalyzer.recurseParents(classEdge.parents, parent -> {
                Set<String> used = usedFieldNames.computeIfAbsent(parent, _ -> new HashSet<>());
                used.add(generifiedName);
                used.add(generifiedNameStatic);
            });
            HierarchyAnalyzer.recurseChildren(classEdge.children, parent -> {
                Set<String> used = usedFieldNames.computeIfAbsent(parent, _ -> new HashSet<>());
                used.add(generifiedName);
                used.add(generifiedNameStatic);
            });
        }

        return generifiedFields;
    }

    private AbstractInsnNode safeConstInsertPoint(MethodNode methodNode) {
        for (AbstractInsnNode insnNode : methodNode.instructions) {
            if (insnNode.getOpcode() == INVOKESPECIAL) {
                return insnNode;
            }
        }

        return null;
    }

    private InsnList defaultValue(DescriptorMember desc) {
        InsnList defaultValPush = new InsnList();

        if (!desc.isPrimitive() || desc.isArray()) {
            defaultValPush.add(new InsnNode(ACONST_NULL));
        } else {
            switch (desc.getValue()) {
                case "B", "S", "I", "C", "Z" -> {
                    defaultValPush.add(new InsnNode(ICONST_0));
                }
                case "J" -> {
                    defaultValPush.add(new InsnNode(LCONST_0));
                }
                case "D" -> {
                    defaultValPush.add(new LdcInsnNode((double) 0));
                }
                case "F" -> {
                    defaultValPush.add(new LdcInsnNode((float) 0));
                }
                default -> throw new IllegalStateException("Invalid desc for primitive");
            }
        }

        return defaultValPush;
    }

    private void remapFieldUsage(JarContext context, Map<MemberKey, GenerifiedField> generifiedFields) {
        Map<String, ClassEdge> hierarchy = context.getExtension().getHierarchyNameMapped();

        for (ClassNode classNode : context.jar().getClasses().values()) {
            for (MethodNode methodNode : classNode.methods) {
                InsnUtil.loop(methodNode.instructions, insnNode -> {
                    if (!(insnNode instanceof FieldInsnNode fieldInsnNode)) {
                        return;
                    }

                    String owner = fieldInsnNode.owner;
                    ClassEdge ownerEdge = hierarchy.get(fieldInsnNode.owner);
                    if (ownerEdge != null) {
                        Optional<FieldEdge> declaringField = ownerEdge.findNearestField(fieldInsnNode.name, fieldInsnNode.desc);
                        if (declaringField.isPresent()) {
                            owner = declaringField.get().owner().classNode.name;
                        }
                    }

                    GenerifiedField gf = generifiedFields.get(new MemberKey(owner, fieldInsnNode.name, fieldInsnNode.desc));
                    if (gf == null) {
                        return;
                    }

                    InsnList replacement = new InsnList();
                    replacement.add(new FieldInsnNode(gf.isStatic ? GETSTATIC : GETFIELD, gf.owner, gf.name, "[Ljava/lang/Object;"));
                    switch (fieldInsnNode.getOpcode()) {
                        case GETSTATIC, GETFIELD -> {
                            replacement.add(ConstantPusher.getIntPush(gf.index));
                            replacement.add(new InsnNode(AALOAD));
                            replacement.add(gf.originalDesc.unbox());
                        }
                        case PUTSTATIC, PUTFIELD -> {
                            replacement.add(gf.originalDesc.box());
                            replacement.add(new InsnNode(SWAP));
                            replacement.add(ConstantPusher.getIntPush(gf.index));
                            replacement.add(new InsnNode(SWAP));
                            replacement.add(new InsnNode(AASTORE));
                        }
                    }

                    methodNode.instructions.insertBefore(fieldInsnNode, insnNode);
                    methodNode.instructions.remove(fieldInsnNode);
                });
            }
        }
    }

    private record GenerifiedField(String owner, String name, boolean isStatic, DescriptorMember originalDesc,
                                   int index) {
    }
}
