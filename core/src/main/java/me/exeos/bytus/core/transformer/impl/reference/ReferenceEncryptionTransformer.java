package me.exeos.bytus.core.transformer.impl.reference;

import me.exeos.bytus.asmplus.analysis.hierarchy.HierarchyAnalyzer;
import me.exeos.bytus.asmplus.analysis.hierarchy.edge.ClassEdge;
import me.exeos.bytus.asmplus.codegen.xswitch.SwitchCase;
import me.exeos.bytus.asmplus.codegen.xswitch.TableSwitchGenerator;
import me.exeos.bytus.asmplus.utils.AsmUtil;
import me.exeos.bytus.asmplus.utils.ClassUtil;
import me.exeos.bytus.asmplus.utils.InsnUtil;
import me.exeos.bytus.asmplus.utils.MethodUtil;
import me.exeos.bytus.core.config.BytusConfig;
import me.exeos.bytus.core.transformer.AbstractTransformer;
import me.exeos.bytus.core.transformer.Priority;
import me.exeos.bytus.core.transformer.context.ClassContext;
import me.exeos.bytus.core.transformer.context.JarContext;
import me.exeos.bytus.core.transformer.context.MethodContext;
import me.exeos.bytus.core.transformer.impl.MethodSaltTransformer;
import me.exeos.bytus.core.transformer.impl.constants.string.StringEncryptionTransformer;
import me.exeos.bytus.core.utils.RandomUtil;
import org.objectweb.asm.Handle;
import org.objectweb.asm.tree.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class ReferenceEncryptionTransformer extends AbstractTransformer {

    private static final String BSM_DESC = "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;I)Ljava/lang/invoke/CallSite;";
    private static Map<String, ClassEdge> hierarchy;
    private static String bsmOwner = null;
    private static boolean bsmOwnerIsInterface = false;
    private static String bsmName = null;

    public ReferenceEncryptionTransformer(BytusConfig config) {
        super(config);
    }

    @Override
    public boolean applies() {
        return config.references.encryption();
    }

    @Override
    public int priority() {
        return Priority.REF_ENC;
    }

    @Override
    public void transform(JarContext context) {
        super.transform(context);
    }

    @Override
    public void transform(ClassContext context) {
        hierarchy = HierarchyAnalyzer.analyzeNameMapped(context.jarCtx().jar());
        if (bsmOwner == null && bsmName == null) {
            getBootstrap(context);
        }

        super.transform(context);
    }

    @Override
    public void transform(MethodContext context) {
        if (context.ownerCtx().classNode().name.equals(bsmOwner) && context.methodNode().name.equals(bsmName) && context.methodNode().desc.equals(BSM_DESC)) {
            return;
        }

        InsnUtil.loop(context.methodNode().instructions, insnNode -> {
            InsnList indyCall = new InsnList();
            switch (insnNode) {
                case MethodInsnNode methodInsnNode -> {
                    if (methodInsnNode.name.equals("<init>") || methodInsnNode.owner.startsWith("[")) {
                        return;
                    }

                    indyCall.add(new InvokeDynamicInsnNode(
                            RandomUtil.getString(1),
                            fixMethodDesc(methodInsnNode),
                            new Handle(H_INVOKESTATIC, bsmOwner, bsmName, BSM_DESC, bsmOwnerIsInterface),
                            methodInsnNode.owner.replace("/", "."),
                            methodInsnNode.name,
                            methodInsnNode.desc,
                            context.ownerCtx().classNode().name.replace("/", "."),
                            getType(insnNode)
                    ));
                }
                case FieldInsnNode fieldInsnNode -> {
                    if (!hierarchy.containsKey(fieldInsnNode.owner)) {
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

                                indyCall.add(new InvokeDynamicInsnNode(
                                        RandomUtil.getString(1),
                                        fixFieldDescriptor(fieldInsnNode),
                                        new Handle(H_INVOKESTATIC, bsmOwner, bsmName, BSM_DESC, bsmOwnerIsInterface),
                                        declaringEdge.classNode.name.replace("/", "."),
                                        fieldInsnNode.name,
                                        fieldInsnNode.desc,
                                        context.ownerCtx().classNode().name.replace("/", "."),
                                        getType(insnNode)
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

    private void getBootstrap(ClassContext context) {
        ClassNode container = context.classNode();

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
                ClassUtil.getNoneCollidingMethodName(context.jarCtx().jar(), container, RandomUtil::getString),
                "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;I)Ljava/lang/invoke/CallSite;",
                null, null
        );

        bsmOwner = container.name;
        bsmOwnerIsInterface = AsmUtil.hasAccess(container.access, ACC_INTERFACE);
        bsmName = bsm.name;

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
        bsm.instructions.add(new VarInsnNode(ASTORE, classLoaderSlot));
//        bsm.instructions.add(new MethodInsnNode(INVOKEVIRTUAL, "java/lang/RuntimeException", "printStackTrace", "()V"));
        bsm.instructions.add(new TypeInsnNode(NEW, "java/lang/RuntimeException"));
        bsm.instructions.add(new InsnNode(DUP));
        bsm.instructions.add(new LdcInsnNode("Dynamic method invocation failed"));
        bsm.instructions.add(new MethodInsnNode(INVOKESPECIAL, "java/lang/RuntimeException", "<init>", "(Ljava/lang/String;)V"));
        bsm.instructions.add(new InsnNode(ATHROW));

        context.pipeline().emit(new MethodContext(context, bsm), Set.of(ReferenceEncryptionTransformer.class, StringEncryptionTransformer.class, MethodSaltTransformer.class));
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

    private SwitchCase defaultHandler() {
        InsnList caseInsn = new InsnList();
        caseInsn.add(MethodUtil.endMethodByThrow());
        return new SwitchCase(0, caseInsn);
    }
}
