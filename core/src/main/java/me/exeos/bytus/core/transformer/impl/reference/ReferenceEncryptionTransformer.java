package me.exeos.bytus.core.transformer.impl.reference;

import me.exeos.asmplus.analysis.hierarchy.edge.ClassEdge;
import me.exeos.asmplus.codegen.xswitch.SwitchCase;
import me.exeos.asmplus.codegen.xswitch.impl.TableSwitchGenerator;
import me.exeos.asmplus.jar.JarArchive;
import me.exeos.asmplus.utils.AsmUtil;
import me.exeos.asmplus.utils.ClassUtil;
import me.exeos.asmplus.utils.InsnUtil;
import me.exeos.asmplus.utils.MethodUtil;
import me.exeos.bytus.core.config.BytusConfig;
import me.exeos.bytus.core.transformer.AbstractTransformer;
import me.exeos.bytus.core.transformer.Priority;
import me.exeos.bytus.core.transformer.context.ClassContext;
import me.exeos.bytus.core.transformer.context.JarContext;
import me.exeos.bytus.core.transformer.context.MethodContext;
import me.exeos.bytus.core.transformer.impl.constants.string.StringEncryptionTransformer;
import me.exeos.bytus.core.transformer.impl.flow.control.BlockRearranger;
import me.exeos.bytus.core.transformer.impl.flow.control.FlowFlatteningTransformer;
import me.exeos.bytus.core.transformer.impl.flow.control.JumpFlatteningTransformer;
import me.exeos.bytus.core.transformer.impl.salt.MethodSaltTransformer;
import me.exeos.bytus.core.utils.NameUtil;
import me.exeos.bytus.core.utils.RandomUtil;
import org.objectweb.asm.Handle;
import org.objectweb.asm.tree.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ReferenceEncryptionTransformer extends AbstractTransformer {

    private final static int CLASS_VERSION = V1_8;
    private static final String cryptFieldKeyName = RandomUtil.getString(1);
    private static final String BSM_DESC = "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;I)Ljava/lang/invoke/CallSite;";
    private static String bsmOwner = null;
    private static String bsmName = null;
    private static String cryptName = null;
    private final Map<ClassNode, Integer> keyMap = new HashMap<>();

    public ReferenceEncryptionTransformer(BytusConfig config) {
        super(config);
    }

    @Override
    public boolean applies() {
        return config.references.enable();
    }

    @Override
    public int priority() {
        return Priority.REF_ENC;
    }

    @Override
    public void transform(JarContext context) {
        ensureNamesSet(context.jar());
        getBootstrap(context);
        super.transform(context);
    }

    @Override
    public void transform(MethodContext context) {
        ensureNamesSet(context.jarCtx().jar());
        if (context.ownerCtx().classNode().name.equals(bsmOwner)) {
            return;
        }

        int key = keyMap.computeIfAbsent(context.ownerCtx().classNode(), _ -> RandomUtil.getInt());
        Map<String, ClassEdge> hierarchy = context.jarCtx().getExtension().getHierarchyNameMapped();
        InsnUtil.loop(context.methodNode().instructions, insnNode -> {
            InsnList indyCall = new InsnList();

            switch (insnNode) {
                case MethodInsnNode methodInsnNode -> {
                    if (methodInsnNode.name.equals("<init>")
                            || methodInsnNode.owner.startsWith("[")
                            || (config.references.firstClassOnly() && !context.jarCtx().jar().getClasses().containsKey(methodInsnNode.owner))
                            || (methodInsnNode.owner.equals(bsmOwner))
                    ) {
                        return;
                    }

                    indyCall.add(context.getExtension().getObfuscatedIntPush(key));
                    indyCall.add(new FieldInsnNode(PUTSTATIC, bsmOwner, cryptFieldKeyName, "I"));
                    indyCall.add(new InvokeDynamicInsnNode(
                            RandomUtil.getString(1),
                            fixMethodDesc(methodInsnNode),
                            new Handle(H_INVOKESTATIC, bsmOwner, bsmName, BSM_DESC, false),
                            StringEncryptionTransformer.crypt(methodInsnNode.owner.replace("/", "."), key),
                            StringEncryptionTransformer.crypt(methodInsnNode.name, key),
                            StringEncryptionTransformer.crypt(methodInsnNode.desc, key),
                            StringEncryptionTransformer.crypt(context.ownerCtx().classNode().name.replace("/", "."), key),
                            getType(insnNode) ^ key
                    ));
                }
                case FieldInsnNode fieldInsnNode -> {
                    if (!hierarchy.containsKey(fieldInsnNode.owner)
                            || (config.references.firstClassOnly() && !context.jarCtx().jar().getClasses().containsKey(fieldInsnNode.owner))
                            || (fieldInsnNode.owner.equals(bsmOwner))
                    ) {
                        return;
                    }

                    hierarchy
                            .get(fieldInsnNode.owner)
                            .findDeclaringClassOfField(fieldInsnNode.name, fieldInsnNode.desc)
                            .ifPresent(declaringEdge -> {
                                if (declaringEdge
                                        .getField(fieldInsnNode.name, fieldInsnNode.desc)
                                        .map(fieldEdge -> AsmUtil.hasAccess(fieldEdge.fieldNode().access, ACC_FINAL))
                                        .orElse(false)) {
                                    return;
                                }

                                indyCall.add(context.getExtension().getObfuscatedIntPush(key));
                                indyCall.add(new FieldInsnNode(PUTSTATIC, bsmOwner, cryptFieldKeyName, "I"));
                                indyCall.add(new InvokeDynamicInsnNode(
                                        RandomUtil.getString(1),
                                        fixFieldDescriptor(fieldInsnNode),
                                        new Handle(H_INVOKESTATIC, bsmOwner, bsmName, BSM_DESC, false),
                                        StringEncryptionTransformer.crypt(declaringEdge.classNode.name.replace("/", "."), key),
                                        StringEncryptionTransformer.crypt(fieldInsnNode.name, key),
                                        StringEncryptionTransformer.crypt(fieldInsnNode.desc, key),
                                        StringEncryptionTransformer.crypt(context.ownerCtx().classNode().name.replace("/", "."), key),
                                        getType(insnNode) ^ key
                                ));
                            });
                }
                default -> {
                }
            }

            if (indyCall.size() > 0) {
                context.methodNode().instructions.insert(insnNode, indyCall);
                context.methodNode().instructions.remove(insnNode);
            }
        });
    }

    private void ensureNamesSet(JarArchive jar) {
        if (bsmOwner == null) {
            bsmOwner = ClassUtil.getNoneCollidingClassName(jar, NameUtil::getName);
        }

        if (bsmName == null) {
            bsmName = RandomUtil.getString(1);
        }

        if (cryptName == null) {
            String name;
            do {
                name = RandomUtil.getString(1);
            } while (name.equals(bsmName));

            cryptName = name;
        }
    }

    private String fixMethodDesc(MethodInsnNode methodInsnNode) {
        switch (methodInsnNode.getOpcode()) {
            case INVOKEVIRTUAL, INVOKEINTERFACE -> {
                return methodInsnNode.desc.replace("(", "(Ljava/lang/Object;");
            }
            case INVOKESPECIAL -> {
                if (methodInsnNode.name.equals("<init>")) {
                    return methodInsnNode.desc.replace(")V", ")L" + methodInsnNode.owner + ";");
                } else {
                    return methodInsnNode.desc.replace("(", "(Ljava/lang/Object;");
                }
            }
            default -> {
                return methodInsnNode.desc;
            }
        }
    }

    private String fixFieldDescriptor(FieldInsnNode fieldInsnNode) {
        return switch (fieldInsnNode.getOpcode()) {
            case GETSTATIC -> "()" + fieldInsnNode.desc;
            case PUTSTATIC -> "(" + fieldInsnNode.desc + ")V";
            case GETFIELD -> "(Ljava/lang/Object;)" + fieldInsnNode.desc;
            case PUTFIELD -> "(Ljava/lang/Object;" + fieldInsnNode.desc + ")V";
            default -> throw new IllegalArgumentException("Opcode " + fieldInsnNode.getOpcode());
        };
    }

    private int getType(AbstractInsnNode insnNode) {
        return switch (insnNode.getOpcode()) {
            case INVOKESTATIC -> 0;
            case INVOKEVIRTUAL, INVOKEINTERFACE -> 1;
            case INVOKESPECIAL -> 2;
            case GETSTATIC -> 3;
            case PUTSTATIC -> 4;
            case GETFIELD -> 5;
            case PUTFIELD -> 6;
            default -> throw new IllegalArgumentException("Provided instruction does not map to Type");
        };
    }

    private void getBootstrap(JarContext context) {
        ClassNode container = new ClassNode();
        container.visit(CLASS_VERSION, ACC_PUBLIC, bsmOwner, null, "java/lang/Object", null);

        /*
        CallSite bootstrap(MethodHandles.Lookup lookup,
                                     String invokedName,
                                     MethodType invokedType,
                                     String owner,
                                     String name,
                                     String desc,
                                     String caller,
                                     int type)
         */
        MethodNode bsm = new MethodNode(
                ACC_PUBLIC | ACC_STATIC,
                bsmName,
                BSM_DESC,
                null, null
        );

        final int lookupSlot = 0;
        final int invokedTypeSlot = 2;
        final int ownerSlot = 3;
        final int nameSlot = 4;
        final int descSlot = 5;
        final int callerSlot = 6;
        final int typeSlot = 7;

        final int classLoaderSlot = 8;
        final int ownerClassSlot = 9;
        final int methodTypeSlot = 10;
        final int targetMHandleSlot = 11;

        LabelNode start = new LabelNode();
        LabelNode end = new LabelNode();
        LabelNode handler = new LabelNode();
        bsm.tryCatchBlocks.add(new TryCatchBlockNode(start, end, handler, "java/lang/Exception"));

        bsm.instructions.add(start);

        bsm.instructions.add(decryptStringSlot(ownerSlot));
        bsm.instructions.add(decryptStringSlot(nameSlot));
        bsm.instructions.add(decryptStringSlot(descSlot));
        bsm.instructions.add(decryptStringSlot(callerSlot));

        bsm.instructions.add(new VarInsnNode(ILOAD, typeSlot));
        bsm.instructions.add(new FieldInsnNode(GETSTATIC, bsmOwner, cryptFieldKeyName, "I"));
        bsm.instructions.add(new InsnNode(IXOR));
        bsm.instructions.add(new VarInsnNode(ISTORE, typeSlot));

        // store lookup.lookupClass().getClassLoader() in classLoaderSlot
        bsm.instructions.add(new VarInsnNode(ALOAD, lookupSlot));
        bsm.instructions.add(new MethodInsnNode(INVOKEVIRTUAL, "java/lang/invoke/MethodHandles$Lookup", "lookupClass", "()Ljava/lang/Class;"));
        bsm.instructions.add(new MethodInsnNode(INVOKEVIRTUAL, "java/lang/Class", "getClassLoader", "()Ljava/lang/ClassLoader;"));
        bsm.instructions.add(new VarInsnNode(ASTORE, classLoaderSlot));

        // store Class.forName(owner, true, classLoader) in ownerClassSlot
        bsm.instructions.add(new VarInsnNode(ALOAD, ownerSlot));
        bsm.instructions.add(new InsnNode(ICONST_1));
        bsm.instructions.add(new VarInsnNode(ALOAD, classLoaderSlot));
        bsm.instructions.add(new MethodInsnNode(INVOKESTATIC, "java/lang/Class", "forName", "(Ljava/lang/String;ZLjava/lang/ClassLoader;)Ljava/lang/Class;"));
        bsm.instructions.add(new VarInsnNode(ASTORE, ownerClassSlot));

        // store MethodType.fromMethodDescriptorString(desc, classLoader) in methodTypeSlot if type < 3
        bsm.instructions.add(new InsnNode(ACONST_NULL));
        bsm.instructions.add(new VarInsnNode(ASTORE, methodTypeSlot));
        bsm.instructions.add(new VarInsnNode(ILOAD, typeSlot));
        bsm.instructions.add(new InsnNode(ICONST_3));
        LabelNode skitMTypeStore = new LabelNode();
        bsm.instructions.add(new JumpInsnNode(IF_ICMPGE, skitMTypeStore));
        bsm.instructions.add(new VarInsnNode(ALOAD, descSlot));
        bsm.instructions.add(new VarInsnNode(ALOAD, classLoaderSlot));
        bsm.instructions.add(new MethodInsnNode(INVOKESTATIC, "java/lang/invoke/MethodType", "fromMethodDescriptorString", "(Ljava/lang/String;Ljava/lang/ClassLoader;)Ljava/lang/invoke/MethodType;"));
        bsm.instructions.add(new VarInsnNode(ASTORE, methodTypeSlot));
        bsm.instructions.add(skitMTypeStore);

        // declare MethodHandle target = null;
        bsm.instructions.add(new InsnNode(ACONST_NULL));
        bsm.instructions.add(new VarInsnNode(ASTORE, targetMHandleSlot));

        // switch on provided type
        bsm.instructions.add(new VarInsnNode(ILOAD, typeSlot));
        LabelNode switchEnd = new LabelNode();
        SwitchCase fieldHandler = fieldHandlerDispatcher(lookupSlot, nameSlot, callerSlot, typeSlot, classLoaderSlot, ownerClassSlot, methodTypeSlot, targetMHandleSlot, switchEnd);
        bsm.instructions.add(TableSwitchGenerator.gen(List.of(
                staticMethodHandler(lookupSlot, nameSlot, ownerClassSlot, methodTypeSlot, targetMHandleSlot, switchEnd),
                virtualMethodHandler(lookupSlot, nameSlot, ownerClassSlot, methodTypeSlot, targetMHandleSlot, switchEnd),
                specialOrConstructorHandler(lookupSlot, nameSlot, callerSlot, classLoaderSlot, ownerClassSlot, methodTypeSlot, targetMHandleSlot, switchEnd),
                fieldHandler,
                fieldHandler,
                fieldHandler,
                fieldHandler
        ), 0, 6, defaultHandler(), false, switchEnd));
        bsm.instructions.add(switchEnd);

        bsm.instructions.add(new TypeInsnNode(NEW, "java/lang/invoke/ConstantCallSite"));
        bsm.instructions.add(new InsnNode(DUP));
        bsm.instructions.add(new VarInsnNode(ALOAD, targetMHandleSlot));
        bsm.instructions.add(new VarInsnNode(ALOAD, invokedTypeSlot));
        bsm.instructions.add(new MethodInsnNode(INVOKEVIRTUAL, "java/lang/invoke/MethodHandle", "asType", "(Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/MethodHandle;"));
        bsm.instructions.add(new MethodInsnNode(INVOKESPECIAL, "java/lang/invoke/ConstantCallSite", "<init>", "(Ljava/lang/invoke/MethodHandle;)V"));
        bsm.instructions.add(end);
        bsm.instructions.add(new InsnNode(ARETURN));

        bsm.instructions.add(handler);
//        bsm.instructions.add(new VarInsnNode(ASTORE, classLoaderSlot));
        bsm.instructions.add(new MethodInsnNode(INVOKEVIRTUAL, "java/lang/Exception", "printStackTrace", "()V"));
        bsm.instructions.add(new TypeInsnNode(NEW, "java/lang/RuntimeException"));
        bsm.instructions.add(new InsnNode(DUP));
        bsm.instructions.add(new LdcInsnNode("Dynamic method invocation failed"));
        bsm.instructions.add(new MethodInsnNode(INVOKESPECIAL, "java/lang/RuntimeException", "<init>", "(Ljava/lang/String;)V"));
        bsm.instructions.add(new InsnNode(ATHROW));

        container.fields.add(new FieldNode(ACC_PUBLIC | ACC_STATIC, cryptFieldKeyName, "I", null, null));
        container.methods.add(bsm);
        container.methods.add(StringEncryptionTransformer.cryptMethod(cryptName));

        context.pipeline().emit(new ClassContext(context, container), Set.of(ReferenceEncryptionTransformer.class, MethodSaltTransformer.class, FlowFlatteningTransformer.class, BlockRearranger.class, JumpFlatteningTransformer.class));
    }

    private SwitchCase staticMethodHandler(int lookupSlot, int nameSlot, int ownerClassSlot, int methodTypeSlot, int targetMHandleSlot, LabelNode switchEnd) {
        InsnList caseInsn = new InsnList();

        caseInsn.add(new VarInsnNode(ALOAD, lookupSlot));
        caseInsn.add(new VarInsnNode(ALOAD, ownerClassSlot));
        caseInsn.add(new VarInsnNode(ALOAD, nameSlot));
        caseInsn.add(new VarInsnNode(ALOAD, methodTypeSlot));
        caseInsn.add(new MethodInsnNode(INVOKEVIRTUAL, "java/lang/invoke/MethodHandles$Lookup", "findStatic", "(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/MethodHandle;"));
        caseInsn.add(new VarInsnNode(ASTORE, targetMHandleSlot));

        caseInsn.add(new JumpInsnNode(GOTO, switchEnd));

        return new SwitchCase(0, caseInsn);
    }

    private SwitchCase virtualMethodHandler(int lookupSlot, int nameSlot, int ownerClassSlot, int methodTypeSlot, int targetMHandleSlot, LabelNode switchEnd) {
        InsnList caseInsn = new InsnList();

        LabelNode normalLookup = new LabelNode();
        caseInsn.add(new VarInsnNode(ALOAD, ownerClassSlot));
        caseInsn.add(new MethodInsnNode(INVOKEVIRTUAL, "java/lang/Class", "isInterface", "()Z"));
        caseInsn.add(new JumpInsnNode(IFEQ, normalLookup));
        caseInsn.add(new MethodInsnNode(INVOKESTATIC, "java/lang/invoke/MethodHandles", "publicLookup", "()Ljava/lang/invoke/MethodHandles$Lookup;"));
        caseInsn.add(new VarInsnNode(ASTORE, lookupSlot));

        caseInsn.add(normalLookup);
        caseInsn.add(new VarInsnNode(ALOAD, lookupSlot));
        caseInsn.add(new VarInsnNode(ALOAD, ownerClassSlot));
        caseInsn.add(new VarInsnNode(ALOAD, nameSlot));
        caseInsn.add(new VarInsnNode(ALOAD, methodTypeSlot));
        caseInsn.add(new MethodInsnNode(INVOKEVIRTUAL, "java/lang/invoke/MethodHandles$Lookup", "findVirtual", "(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/MethodHandle;"));
        caseInsn.add(new VarInsnNode(ASTORE, targetMHandleSlot));

        caseInsn.add(new JumpInsnNode(GOTO, switchEnd));

        return new SwitchCase(0, caseInsn);
    }

    private SwitchCase specialOrConstructorHandler(int lookupSlot, int nameSlot, int callerSlot, int classLoaderSlot, int ownerClassSlot, int methodTypeSlot, int targetMHandleSlot, LabelNode switchEnd) {
        InsnList caseInsn = new InsnList();

        caseInsn.add(new VarInsnNode(ALOAD, nameSlot));
        caseInsn.add(new LdcInsnNode("<init>"));
        caseInsn.add(new MethodInsnNode(INVOKEVIRTUAL, "java/lang/String", "equals", "(Ljava/lang/Object;)Z"));
        LabelNode constructorHandler = new LabelNode();
        caseInsn.add(new JumpInsnNode(IFEQ, constructorHandler));
        // handle special
        caseInsn.add(new VarInsnNode(ALOAD, lookupSlot));
        caseInsn.add(new VarInsnNode(ALOAD, ownerClassSlot));
        caseInsn.add(new VarInsnNode(ALOAD, methodTypeSlot));
        caseInsn.add(new FieldInsnNode(GETSTATIC, "java/lang/Void", "TYPE", "Ljava/lang/Class;"));
        caseInsn.add(new MethodInsnNode(INVOKEVIRTUAL, "java/lang/invoke/MethodType", "changeReturnType", "(Ljava/lang/Class;)Ljava/lang/invoke/MethodType;"));
        caseInsn.add(new MethodInsnNode(INVOKEVIRTUAL, "java/lang/invoke/MethodHandles$Lookup", "findConstructor", "(Ljava/lang/Class;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/MethodHandle;"));
        caseInsn.add(new VarInsnNode(ASTORE, targetMHandleSlot));
        caseInsn.add(new JumpInsnNode(GOTO, switchEnd));

        caseInsn.add(constructorHandler);
        caseInsn.add(new VarInsnNode(ALOAD, lookupSlot));
        caseInsn.add(new VarInsnNode(ALOAD, ownerClassSlot));
        caseInsn.add(new VarInsnNode(ALOAD, nameSlot));
        caseInsn.add(new VarInsnNode(ALOAD, methodTypeSlot));
        caseInsn.add(new VarInsnNode(ALOAD, callerSlot));
        caseInsn.add(new InsnNode(ICONST_1));
        caseInsn.add(new VarInsnNode(ALOAD, classLoaderSlot));
        caseInsn.add(new MethodInsnNode(INVOKESTATIC, "java/lang/Class", "forName", "(Ljava/lang/String;ZLjava/lang/ClassLoader;)Ljava/lang/Class;"));
        caseInsn.add(new MethodInsnNode(INVOKEVIRTUAL, "java/lang/invoke/MethodHandles$Lookup", "findSpecial", "(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/Class;)Ljava/lang/invoke/MethodHandle;"));
        caseInsn.add(new VarInsnNode(ASTORE, targetMHandleSlot));

        caseInsn.add(new JumpInsnNode(GOTO, switchEnd));

        return new SwitchCase(0, caseInsn);
    }

    private SwitchCase fieldHandlerDispatcher(int lookupSlot, int nameSlot, int callerSlot, int typeSlot, int classLoaderSlot, int ownerClassSlot, int methodTypeSlot, int targetMHandleSlot, LabelNode switchEnd) {
        InsnList caseInsn = new InsnList();

        int fieldTypeSlot = 12;

        caseInsn.add(new VarInsnNode(ALOAD, ownerClassSlot));
        caseInsn.add(new VarInsnNode(ALOAD, nameSlot));
        caseInsn.add(new MethodInsnNode(INVOKEVIRTUAL, "java/lang/Class", "getDeclaredField", "(Ljava/lang/String;)Ljava/lang/reflect/Field;"));
        caseInsn.add(new MethodInsnNode(INVOKEVIRTUAL, "java/lang/reflect/Field", "getType", "()Ljava/lang/Class;"));
        caseInsn.add(new VarInsnNode(ASTORE, fieldTypeSlot));

        caseInsn.add(new VarInsnNode(ILOAD, typeSlot));
        caseInsn.add(TableSwitchGenerator.gen(List.of(
                fieldHandler(3, lookupSlot, nameSlot, ownerClassSlot, targetMHandleSlot, fieldTypeSlot),
                fieldHandler(4, lookupSlot, nameSlot, ownerClassSlot, targetMHandleSlot, fieldTypeSlot),
                fieldHandler(5, lookupSlot, nameSlot, ownerClassSlot, targetMHandleSlot, fieldTypeSlot),
                fieldHandler(6, lookupSlot, nameSlot, ownerClassSlot, targetMHandleSlot, fieldTypeSlot)
        ), 3, 6));
        caseInsn.add(new JumpInsnNode(GOTO, switchEnd));

        return new SwitchCase(0, caseInsn);
    }

    private SwitchCase fieldHandler(int type, int lookupSlot, int nameSlot, int ownerClassSlot, int targetMHandleSlot, int fieldTypeSlot) {
        InsnList caseInsn = new InsnList();

        String methodName = switch (type) {
            case 3 -> "findStaticGetter";
            case 4 -> "findStaticSetter";
            case 5 -> "findGetter";
            case 6 -> "findSetter";
            default -> throw new IllegalStateException("Invalid type");
        };

        caseInsn.add(new VarInsnNode(ALOAD, lookupSlot));
        caseInsn.add(new VarInsnNode(ALOAD, ownerClassSlot));
        caseInsn.add(new VarInsnNode(ALOAD, nameSlot));
        caseInsn.add(new VarInsnNode(ALOAD, fieldTypeSlot));
        caseInsn.add(new MethodInsnNode(INVOKEVIRTUAL, "java/lang/invoke/MethodHandles$Lookup", methodName, "(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/Class;)Ljava/lang/invoke/MethodHandle;"));
        caseInsn.add(new VarInsnNode(ASTORE, targetMHandleSlot));

        return new SwitchCase(0, caseInsn);
    }

    private InsnList decryptStringSlot(int slot) {
        InsnList insns = new InsnList();

        insns.add(new VarInsnNode(ALOAD, slot));
        insns.add(new FieldInsnNode(GETSTATIC, bsmOwner, cryptFieldKeyName, "I"));
        insns.add(new MethodInsnNode(INVOKESTATIC, bsmOwner, cryptName, StringEncryptionTransformer.DEC_METHOD_DESC));
        insns.add(new VarInsnNode(ASTORE, slot));

        return insns;
    }

    private SwitchCase defaultHandler() {
        InsnList caseInsn = new InsnList();
        caseInsn.add(MethodUtil.endMethodByThrow());
        return new SwitchCase(0, caseInsn);
    }
}
