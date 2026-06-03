package me.exeos.bytus.core.transformer.impl.reference;

import me.exeos.bytus.asmplus.jar.JarArchive;
import me.exeos.bytus.asmplus.utils.ClassUtil;
import me.exeos.bytus.asmplus.utils.InsnUtil;
import me.exeos.bytus.core.config.BytusConfig;
import me.exeos.bytus.core.transformer.AbstractTransformer;
import me.exeos.bytus.core.transformer.Priority;
import me.exeos.bytus.core.transformer.context.ClassContext;
import me.exeos.bytus.core.transformer.context.MethodContext;
import me.exeos.bytus.core.utils.RandomUtil;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public final class ReferenceEncryptionTransformer extends AbstractTransformer {

    public static final String BOOTSTRAP_DESC = "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/String;)Ljava/lang/invoke/CallSite;";

    private final boolean methodCall, fieldAccess;

    public ReferenceEncryptionTransformer(BytusConfig config) {
        super(config);
        this.methodCall = config.references.encryption().methodCalls();
        this.fieldAccess = config.references.encryption().fieldAccess();
    }

    @Override
    public boolean applies() {
        return config.references.encryption().enable();
    }

    @Override
    public int priority() {
        return Priority.REF_ENC;
    }

    @Override
    public void transform(ClassContext context) {
        ClassNode classNode = context.classNode();

        AtomicInteger methodCount = new AtomicInteger();
        AtomicInteger fieldCount = new AtomicInteger();

        if ((classNode.access & ACC_INTERFACE) != 0) return;

        String bootstrapMethodName = ClassUtil.getNoneCollidingMethodName(context.jarCtx().jar(), classNode, RandomUtil::getString);

        AtomicBoolean anyCalls = new AtomicBoolean(false);

        for (MethodNode methodNode : classNode.methods) {
            if (methodNode.instructions.size() == 0) continue;

            InsnUtil.loop(methodNode.instructions, abstractInsnNode -> {
                if (abstractInsnNode instanceof MethodInsnNode methodInsnNode && this.methodCall) {

                    if (methodInsnNode.name.equals("<init>")) {
                        if (methodNode.name.equals("<init>") && methodInsnNode.owner.equals(classNode.superName))
                            return;

                        ClassNode ownerClass = context.jarCtx().jar().getClassNode(methodInsnNode.owner);
                        if (ownerClass == null || (ownerClass.access & ACC_ABSTRACT) != 0) {
                            return; // doesnt work for abstract super call
                        }

                        AbstractInsnNode prev = abstractInsnNode.getPrevious();

                        boolean found = false;
                        while (prev != null) {
                            if (prev.getOpcode() == NEW
                                    && prev instanceof TypeInsnNode typeInsnNode
                                    && Objects.equals(typeInsnNode.desc, methodInsnNode.owner)) {
                                AbstractInsnNode next = prev.getNext();
                                if (next.getOpcode() == DUP) {
                                    methodNode.instructions.remove(next);
                                    methodNode.instructions.remove(prev);
                                    found = true;
                                    break;
                                }
                            }
                            prev = prev.getPrevious();
                        }
                        if (!found) {
                            System.out.println("didnt find new + dup for constructor call " + methodInsnNode.owner + "." + methodInsnNode.name + "." + methodInsnNode.desc);
                            return; // what happened?
                        }
                    }

                    InvokeDynamicInsnNode invokeDynamicInsnNode = this.makeMethodInvokeDynamicInsn(classNode, methodInsnNode, bootstrapMethodName);
                    methodNode.instructions.insert(methodInsnNode, invokeDynamicInsnNode);
                    methodNode.instructions.remove(methodInsnNode);

                    anyCalls.set(true);

                    methodCount.getAndIncrement();
                }
                if (abstractInsnNode instanceof FieldInsnNode fieldInsnNode && this.fieldAccess) {

                    ClassNode declaringClass = this.findDeclaringClass(context.jarCtx().jar(), fieldInsnNode);
                    if (declaringClass == null) return;

                    // check if field is final
                    boolean isFinal = declaringClass.fields.stream()
                            .filter(f -> f.name.equals(fieldInsnNode.name) && f.desc.equals(fieldInsnNode.desc))
                            .findFirst().map(f -> (f.access & ACC_FINAL) != 0).orElse(false);
                    if (isFinal) return;

                    InvokeDynamicInsnNode invokeDynamicInsnNode = this.makeFieldInvokeDynamicInsn(classNode, fieldInsnNode, declaringClass, bootstrapMethodName);
                    methodNode.instructions.insert(fieldInsnNode, invokeDynamicInsnNode);
                    methodNode.instructions.remove(fieldInsnNode);

                    anyCalls.set(true);

                    fieldCount.getAndIncrement();
                }
            });
        }

        if (anyCalls.get()) {
            MethodNode bsm = makeBootstrapMethod(bootstrapMethodName);
            context.pipeline().emit(new MethodContext(context, bsm), Set.of(ReferenceEncryptionTransformer.class));
        }
    }

    private InvokeDynamicInsnNode makeMethodInvokeDynamicInsn(ClassNode owner, MethodInsnNode methodInsnNode, String bootstrapName) {
        Handle bsmHandle = new Handle(H_INVOKESTATIC, owner.name, bootstrapName, BOOTSTRAP_DESC, false);
        return new InvokeDynamicInsnNode(
                "", // not needed
                this.fixMethodDescriptor(methodInsnNode.getOpcode(), methodInsnNode),
                bsmHandle,
                this.getMethodSignature(owner, methodInsnNode));
    }

    private String fixMethodDescriptor(int opcode, MethodInsnNode methodInsnNode) {
        if (opcode == INVOKEVIRTUAL || opcode == INVOKEINTERFACE) {
            return methodInsnNode.desc.replace("(", "(Ljava/lang/Object;");
        }
        if (opcode == INVOKESPECIAL) {
            if (methodInsnNode.name.equals("<init>")) {
                return methodInsnNode.desc.replace(")V", ")L" + methodInsnNode.owner + ";");
            } else {
                return methodInsnNode.desc.replace("(", "(Ljava/lang/Object;");
            }
        }
        return methodInsnNode.desc;
    }

    private InvokeDynamicInsnNode makeFieldInvokeDynamicInsn(ClassNode owner, FieldInsnNode fieldInsnNode, ClassNode declaringClass, String bootstrapName) {
        Handle bsmHandle = new Handle(H_INVOKESTATIC, owner.name, bootstrapName, BOOTSTRAP_DESC, false);
        return new InvokeDynamicInsnNode(
                "", // not needed
                this.fixFieldDescriptor(fieldInsnNode),
                bsmHandle,
                this.getFieldSignature(fieldInsnNode, declaringClass));
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

    private String getAccessCode(int opcode) {
        return switch (opcode) {
            case INVOKEINTERFACE, INVOKEVIRTUAL -> "A";
            case INVOKESTATIC -> "S";
            case INVOKESPECIAL -> "Z";
            case GETSTATIC -> "C";
            case PUTSTATIC -> "F";
            case GETFIELD -> "D";
            case PUTFIELD -> "I";
            default -> throw new IllegalArgumentException("Opcode " + opcode);
        };
    }

    private String getMethodSignature(ClassNode owner, MethodInsnNode methodInsnNode) {
        String methodSignature = methodInsnNode.desc + "#" + this.getAccessCode(methodInsnNode.getOpcode()) + "#" + methodInsnNode.name + "#" + methodInsnNode.owner;
        if (methodInsnNode.getOpcode() == INVOKESPECIAL)
            methodSignature += "#" + owner.name;
        return methodSignature;
    }

    private ClassNode findDeclaringClass(JarArchive jar, FieldInsnNode fieldInsnNode) {
        ClassNode current = jar.getClassNode(fieldInsnNode.owner);
        while (current != null) {
            boolean declared = current.fields.stream()
                    .anyMatch(f -> f.name.equals(fieldInsnNode.name) && f.desc.equals(fieldInsnNode.desc));
            if (declared) return current;
            current = jar.getClassNode(current.superName);
        }
        return null;
    }

    private String getFieldSignature(FieldInsnNode fieldInsnNode, ClassNode declaringClass) {
        return fieldInsnNode.desc + "#" + this.getAccessCode(fieldInsnNode.getOpcode()) + "#" + fieldInsnNode.name + "#" + declaringClass.name;
    }

    private MethodNode makeBootstrapMethod(String bootstrapName) {
        MethodNode methodVisitor = new MethodNode(ACC_PUBLIC | ACC_STATIC, bootstrapName, BOOTSTRAP_DESC, null, null);
        methodVisitor.visitCode();
        Label label0 = new Label();
        Label label1 = new Label();
        Label label2 = new Label();
        methodVisitor.visitTryCatchBlock(label0, label1, label2, "java/lang/Exception");
        Label label3 = new Label();
        Label label4 = new Label();
        methodVisitor.visitTryCatchBlock(label3, label4, label2, "java/lang/Exception");
        methodVisitor.visitLabel(label0);
        methodVisitor.visitLineNumber(163, label0);
        methodVisitor.visitVarInsn(ALOAD, 3);
        methodVisitor.visitLdcInsn("#");
        methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "split", "(Ljava/lang/String;)[Ljava/lang/String;", false);
        methodVisitor.visitInsn(ICONST_0);
        methodVisitor.visitInsn(AALOAD);
        methodVisitor.visitVarInsn(ASTORE, 4);
        Label label5 = new Label();
        methodVisitor.visitLabel(label5);
        methodVisitor.visitLineNumber(164, label5);
        methodVisitor.visitVarInsn(ALOAD, 3);
        methodVisitor.visitLdcInsn("#");
        methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "split", "(Ljava/lang/String;)[Ljava/lang/String;", false);
        methodVisitor.visitInsn(ICONST_1);
        methodVisitor.visitInsn(AALOAD);
        methodVisitor.visitVarInsn(ASTORE, 5);
        Label label6 = new Label();
        methodVisitor.visitLabel(label6);
        methodVisitor.visitLineNumber(165, label6);
        methodVisitor.visitVarInsn(ALOAD, 3);
        methodVisitor.visitLdcInsn("#");
        methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "split", "(Ljava/lang/String;)[Ljava/lang/String;", false);
        methodVisitor.visitInsn(ICONST_2);
        methodVisitor.visitInsn(AALOAD);
        methodVisitor.visitVarInsn(ASTORE, 6);
        Label label7 = new Label();
        methodVisitor.visitLabel(label7);
        methodVisitor.visitLineNumber(166, label7);
        methodVisitor.visitVarInsn(ALOAD, 3);
        methodVisitor.visitLdcInsn("#");
        methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "split", "(Ljava/lang/String;)[Ljava/lang/String;", false);
        methodVisitor.visitInsn(ICONST_3);
        methodVisitor.visitInsn(AALOAD);
        methodVisitor.visitLdcInsn("/");
        methodVisitor.visitLdcInsn(".");
        methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "replace", "(Ljava/lang/CharSequence;Ljava/lang/CharSequence;)Ljava/lang/String;", false);
        methodVisitor.visitVarInsn(ASTORE, 7);
        Label label8 = new Label();
        methodVisitor.visitLabel(label8);
        methodVisitor.visitLineNumber(168, label8);
        methodVisitor.visitVarInsn(ALOAD, 7);
        methodVisitor.visitMethodInsn(INVOKESTATIC, "java/lang/Class", "forName", "(Ljava/lang/String;)Ljava/lang/Class;", false);
        methodVisitor.visitVarInsn(ASTORE, 8);
        Label label9 = new Label();
        methodVisitor.visitLabel(label9);
        methodVisitor.visitLineNumber(170, label9);
        methodVisitor.visitInsn(ACONST_NULL);
        methodVisitor.visitVarInsn(ASTORE, 9);
        Label label10 = new Label();
        methodVisitor.visitLabel(label10);
        methodVisitor.visitLineNumber(172, label10);
        methodVisitor.visitVarInsn(ALOAD, 5);
        methodVisitor.visitLdcInsn("A");
        methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "equals", "(Ljava/lang/Object;)Z", false);
        Label label11 = new Label();
        methodVisitor.visitJumpInsn(IFNE, label11);
        methodVisitor.visitVarInsn(ALOAD, 5);
        methodVisitor.visitLdcInsn("S");
        methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "equals", "(Ljava/lang/Object;)Z", false);
        methodVisitor.visitJumpInsn(IFNE, label11);
        methodVisitor.visitVarInsn(ALOAD, 5);
        methodVisitor.visitLdcInsn("Z");
        methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "equals", "(Ljava/lang/Object;)Z", false);
        Label label12 = new Label();
        methodVisitor.visitJumpInsn(IFEQ, label12);
        methodVisitor.visitLabel(label11);
        methodVisitor.visitLineNumber(173, label11);
        methodVisitor.visitFrame(Opcodes.F_FULL, 10, new Object[]{"java/lang/invoke/MethodHandles$Lookup", "java/lang/String", "java/lang/invoke/MethodType", "java/lang/String", "java/lang/String", "java/lang/String", "java/lang/String", "java/lang/String", "java/lang/Class", "java/lang/invoke/MethodHandle"}, 0, new Object[]{});
        methodVisitor.visitVarInsn(ALOAD, 4);
        methodVisitor.visitVarInsn(ALOAD, 8);
        Label label13 = new Label();
        methodVisitor.visitLabel(label13);
        methodVisitor.visitLineNumber(174, label13);
        methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Class", "getClassLoader", "()Ljava/lang/ClassLoader;", false);
        Label label14 = new Label();
        methodVisitor.visitLabel(label14);
        methodVisitor.visitLineNumber(173, label14);
        methodVisitor.visitMethodInsn(INVOKESTATIC, "java/lang/invoke/MethodType", "fromMethodDescriptorString", "(Ljava/lang/String;Ljava/lang/ClassLoader;)Ljava/lang/invoke/MethodType;", false);
        methodVisitor.visitVarInsn(ASTORE, 10);
        Label label15 = new Label();
        methodVisitor.visitLabel(label15);
        methodVisitor.visitLineNumber(175, label15);
        methodVisitor.visitVarInsn(ALOAD, 5);
        methodVisitor.visitLdcInsn("S");
        methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "equals", "(Ljava/lang/Object;)Z", false);
        Label label16 = new Label();
        methodVisitor.visitJumpInsn(IFEQ, label16);
        Label label17 = new Label();
        methodVisitor.visitLabel(label17);
        methodVisitor.visitLineNumber(176, label17);
        methodVisitor.visitVarInsn(ALOAD, 0);
        methodVisitor.visitVarInsn(ALOAD, 8);
        methodVisitor.visitVarInsn(ALOAD, 6);
        methodVisitor.visitVarInsn(ALOAD, 10);
        methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/invoke/MethodHandles$Lookup", "findStatic", "(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/MethodHandle;", false);
        methodVisitor.visitVarInsn(ALOAD, 2);
        methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/invoke/MethodHandle", "asType", "(Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/MethodHandle;", false);
        methodVisitor.visitVarInsn(ASTORE, 9);
        Label label18 = new Label();
        methodVisitor.visitJumpInsn(GOTO, label18);
        methodVisitor.visitLabel(label16);
        methodVisitor.visitLineNumber(177, label16);
        methodVisitor.visitFrame(Opcodes.F_APPEND, 1, new Object[]{"java/lang/invoke/MethodType"}, 0, null);
        methodVisitor.visitVarInsn(ALOAD, 5);
        methodVisitor.visitLdcInsn("Z");
        methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "equals", "(Ljava/lang/Object;)Z", false);
        Label label19 = new Label();
        methodVisitor.visitJumpInsn(IFEQ, label19);
        Label label20 = new Label();
        methodVisitor.visitLabel(label20);
        methodVisitor.visitLineNumber(178, label20);
        methodVisitor.visitVarInsn(ALOAD, 6);
        methodVisitor.visitLdcInsn("<init>");
        methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "equals", "(Ljava/lang/Object;)Z", false);
        Label label21 = new Label();
        methodVisitor.visitJumpInsn(IFEQ, label21);
        Label label22 = new Label();
        methodVisitor.visitLabel(label22);
        methodVisitor.visitLineNumber(179, label22);
        methodVisitor.visitVarInsn(ALOAD, 0);
        methodVisitor.visitVarInsn(ALOAD, 8);
        methodVisitor.visitVarInsn(ALOAD, 10);
        methodVisitor.visitFieldInsn(GETSTATIC, "java/lang/Void", "TYPE", "Ljava/lang/Class;");
        methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/invoke/MethodType", "changeReturnType", "(Ljava/lang/Class;)Ljava/lang/invoke/MethodType;", false);
        methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/invoke/MethodHandles$Lookup", "findConstructor", "(Ljava/lang/Class;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/MethodHandle;", false);
        methodVisitor.visitVarInsn(ALOAD, 2);
        methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/invoke/MethodHandle", "asType", "(Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/MethodHandle;", false);
        methodVisitor.visitVarInsn(ASTORE, 9);
        methodVisitor.visitJumpInsn(GOTO, label18);
        methodVisitor.visitLabel(label21);
        methodVisitor.visitLineNumber(181, label21);
        methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
        methodVisitor.visitVarInsn(ALOAD, 0);
        methodVisitor.visitVarInsn(ALOAD, 8);
        methodVisitor.visitVarInsn(ALOAD, 6);
        methodVisitor.visitVarInsn(ALOAD, 10);
        methodVisitor.visitVarInsn(ALOAD, 3);
        methodVisitor.visitLdcInsn("#");
        methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "split", "(Ljava/lang/String;)[Ljava/lang/String;", false);
        methodVisitor.visitInsn(ICONST_4);
        methodVisitor.visitInsn(AALOAD);
        methodVisitor.visitLdcInsn("/");
        methodVisitor.visitLdcInsn(".");
        methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "replace", "(Ljava/lang/CharSequence;Ljava/lang/CharSequence;)Ljava/lang/String;", false);
        methodVisitor.visitMethodInsn(INVOKESTATIC, "java/lang/Class", "forName", "(Ljava/lang/String;)Ljava/lang/Class;", false);
        methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/invoke/MethodHandles$Lookup", "findSpecial", "(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/Class;)Ljava/lang/invoke/MethodHandle;", false);
        methodVisitor.visitVarInsn(ALOAD, 2);
        methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/invoke/MethodHandle", "asType", "(Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/MethodHandle;", false);
        methodVisitor.visitVarInsn(ASTORE, 9);
        methodVisitor.visitJumpInsn(GOTO, label18);
        methodVisitor.visitLabel(label19);
        methodVisitor.visitLineNumber(183, label19);
        methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
        methodVisitor.visitVarInsn(ALOAD, 0);
        methodVisitor.visitVarInsn(ALOAD, 8);
        methodVisitor.visitVarInsn(ALOAD, 6);
        methodVisitor.visitVarInsn(ALOAD, 10);
        methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/invoke/MethodHandles$Lookup", "findVirtual", "(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/MethodHandle;", false);
        methodVisitor.visitVarInsn(ALOAD, 2);
        methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/invoke/MethodHandle", "asType", "(Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/MethodHandle;", false);
        methodVisitor.visitVarInsn(ASTORE, 9);
        methodVisitor.visitLabel(label18);
        methodVisitor.visitLineNumber(185, label18);
        methodVisitor.visitFrame(Opcodes.F_CHOP, 1, null, 0, null);
        Label label23 = new Label();
        methodVisitor.visitJumpInsn(GOTO, label23);
        methodVisitor.visitLabel(label12);
        methodVisitor.visitLineNumber(186, label12);
        methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
        methodVisitor.visitVarInsn(ALOAD, 8);
        methodVisitor.visitVarInsn(ALOAD, 6);
        methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Class", "getDeclaredField", "(Ljava/lang/String;)Ljava/lang/reflect/Field;", false);
        methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/reflect/Field", "getType", "()Ljava/lang/Class;", false);
        methodVisitor.visitVarInsn(ASTORE, 10);
        Label label24 = new Label();
        methodVisitor.visitLabel(label24);
        methodVisitor.visitLineNumber(187, label24);
        methodVisitor.visitVarInsn(ALOAD, 5);
        methodVisitor.visitLdcInsn("C");
        methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "equals", "(Ljava/lang/Object;)Z", false);
        Label label25 = new Label();
        methodVisitor.visitJumpInsn(IFEQ, label25);
        Label label26 = new Label();
        methodVisitor.visitLabel(label26);
        methodVisitor.visitLineNumber(188, label26);
        methodVisitor.visitVarInsn(ALOAD, 0);
        methodVisitor.visitVarInsn(ALOAD, 8);
        methodVisitor.visitVarInsn(ALOAD, 6);
        methodVisitor.visitVarInsn(ALOAD, 10);
        methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/invoke/MethodHandles$Lookup", "findStaticGetter", "(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/Class;)Ljava/lang/invoke/MethodHandle;", false);
        methodVisitor.visitVarInsn(ALOAD, 2);
        methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/invoke/MethodHandle", "asType", "(Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/MethodHandle;", false);
        methodVisitor.visitVarInsn(ASTORE, 9);
        methodVisitor.visitJumpInsn(GOTO, label23);
        methodVisitor.visitLabel(label25);
        methodVisitor.visitLineNumber(189, label25);
        methodVisitor.visitFrame(Opcodes.F_APPEND, 1, new Object[]{"java/lang/Class"}, 0, null);
        methodVisitor.visitVarInsn(ALOAD, 5);
        methodVisitor.visitLdcInsn("F");
        methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "equals", "(Ljava/lang/Object;)Z", false);
        Label label27 = new Label();
        methodVisitor.visitJumpInsn(IFEQ, label27);
        Label label28 = new Label();
        methodVisitor.visitLabel(label28);
        methodVisitor.visitLineNumber(190, label28);
        methodVisitor.visitVarInsn(ALOAD, 0);
        methodVisitor.visitVarInsn(ALOAD, 8);
        methodVisitor.visitVarInsn(ALOAD, 6);
        methodVisitor.visitVarInsn(ALOAD, 10);
        methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/invoke/MethodHandles$Lookup", "findStaticSetter", "(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/Class;)Ljava/lang/invoke/MethodHandle;", false);
        methodVisitor.visitVarInsn(ALOAD, 2);
        methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/invoke/MethodHandle", "asType", "(Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/MethodHandle;", false);
        methodVisitor.visitVarInsn(ASTORE, 9);
        methodVisitor.visitJumpInsn(GOTO, label23);
        methodVisitor.visitLabel(label27);
        methodVisitor.visitLineNumber(191, label27);
        methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
        methodVisitor.visitVarInsn(ALOAD, 5);
        methodVisitor.visitLdcInsn("D");
        methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "equals", "(Ljava/lang/Object;)Z", false);
        Label label29 = new Label();
        methodVisitor.visitJumpInsn(IFEQ, label29);
        Label label30 = new Label();
        methodVisitor.visitLabel(label30);
        methodVisitor.visitLineNumber(192, label30);
        methodVisitor.visitVarInsn(ALOAD, 0);
        methodVisitor.visitVarInsn(ALOAD, 8);
        methodVisitor.visitVarInsn(ALOAD, 6);
        methodVisitor.visitVarInsn(ALOAD, 10);
        methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/invoke/MethodHandles$Lookup", "findGetter", "(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/Class;)Ljava/lang/invoke/MethodHandle;", false);
        methodVisitor.visitVarInsn(ALOAD, 2);
        methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/invoke/MethodHandle", "asType", "(Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/MethodHandle;", false);
        methodVisitor.visitVarInsn(ASTORE, 9);
        methodVisitor.visitJumpInsn(GOTO, label23);
        methodVisitor.visitLabel(label29);
        methodVisitor.visitLineNumber(193, label29);
        methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
        methodVisitor.visitVarInsn(ALOAD, 5);
        methodVisitor.visitLdcInsn("I");
        methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "equals", "(Ljava/lang/Object;)Z", false);
        methodVisitor.visitJumpInsn(IFEQ, label23);
        Label label31 = new Label();
        methodVisitor.visitLabel(label31);
        methodVisitor.visitLineNumber(194, label31);
        methodVisitor.visitVarInsn(ALOAD, 0);
        methodVisitor.visitVarInsn(ALOAD, 8);
        methodVisitor.visitVarInsn(ALOAD, 6);
        methodVisitor.visitVarInsn(ALOAD, 10);
        methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/invoke/MethodHandles$Lookup", "findSetter", "(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/Class;)Ljava/lang/invoke/MethodHandle;", false);
        methodVisitor.visitVarInsn(ALOAD, 2);
        methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/invoke/MethodHandle", "asType", "(Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/MethodHandle;", false);
        methodVisitor.visitVarInsn(ASTORE, 9);
        methodVisitor.visitLabel(label23);
        methodVisitor.visitLineNumber(198, label23);
        methodVisitor.visitFrame(Opcodes.F_CHOP, 1, null, 0, null);
        methodVisitor.visitVarInsn(ALOAD, 9);
        methodVisitor.visitJumpInsn(IFNONNULL, label3);
        Label label32 = new Label();
        methodVisitor.visitLabel(label32);
        methodVisitor.visitLineNumber(199, label32);
        methodVisitor.visitInsn(ACONST_NULL);
        methodVisitor.visitLabel(label1);
        methodVisitor.visitInsn(ARETURN);
        methodVisitor.visitLabel(label3);
        methodVisitor.visitLineNumber(201, label3);
        methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
        methodVisitor.visitTypeInsn(NEW, "java/lang/invoke/MutableCallSite");
        methodVisitor.visitInsn(DUP);
        methodVisitor.visitVarInsn(ALOAD, 9);
        methodVisitor.visitMethodInsn(INVOKESPECIAL, "java/lang/invoke/MutableCallSite", "<init>", "(Ljava/lang/invoke/MethodHandle;)V", false);
        methodVisitor.visitLabel(label4);
        methodVisitor.visitInsn(ARETURN);
        methodVisitor.visitLabel(label2);
        methodVisitor.visitLineNumber(202, label2);
        methodVisitor.visitFrame(Opcodes.F_FULL, 4, new Object[]{"java/lang/invoke/MethodHandles$Lookup", "java/lang/String", "java/lang/invoke/MethodType", "java/lang/String"}, 1, new Object[]{"java/lang/Exception"});
        methodVisitor.visitVarInsn(ASTORE, 4);
        Label label33 = new Label();
        methodVisitor.visitLabel(label33);
        methodVisitor.visitLineNumber(203, label33);
        methodVisitor.visitTypeInsn(NEW, "java/lang/RuntimeException");
        methodVisitor.visitInsn(DUP);
        methodVisitor.visitVarInsn(ALOAD, 4);
        methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Exception", "getMessage", "()Ljava/lang/String;", false);
        methodVisitor.visitInvokeDynamicInsn("makeConcatWithConstants", "(Ljava/lang/String;)Ljava/lang/String;", new Handle(Opcodes.H_INVOKESTATIC, "java/lang/invoke/StringConcatFactory", "makeConcatWithConstants", "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/invoke/CallSite;", false), new Object[]{"Bootstrap failed: \u0001"});
        methodVisitor.visitVarInsn(ALOAD, 4);
        methodVisitor.visitMethodInsn(INVOKESPECIAL, "java/lang/RuntimeException", "<init>", "(Ljava/lang/String;Ljava/lang/Throwable;)V", false);
        methodVisitor.visitInsn(ATHROW);
        Label label34 = new Label();
        methodVisitor.visitLabel(label34);
        methodVisitor.visitLocalVariable("correctType", "Ljava/lang/invoke/MethodType;", null, label15, label18, 10);
        methodVisitor.visitLocalVariable("fieldType", "Ljava/lang/Class;", "Ljava/lang/Class<*>;", label24, label23, 10);
        methodVisitor.visitLocalVariable("memberDesc", "Ljava/lang/String;", null, label5, label2, 4);
        methodVisitor.visitLocalVariable("accessCode", "Ljava/lang/String;", null, label6, label2, 5);
        methodVisitor.visitLocalVariable("memberName", "Ljava/lang/String;", null, label7, label2, 6);
        methodVisitor.visitLocalVariable("className", "Ljava/lang/String;", null, label8, label2, 7);
        methodVisitor.visitLocalVariable("clazz", "Ljava/lang/Class;", "Ljava/lang/Class<*>;", label9, label2, 8);
        methodVisitor.visitLocalVariable("handle", "Ljava/lang/invoke/MethodHandle;", null, label10, label2, 9);
        methodVisitor.visitLocalVariable("e", "Ljava/lang/Exception;", null, label33, label34, 4);
        methodVisitor.visitLocalVariable("lookup", "Ljava/lang/invoke/MethodHandles$Lookup;", null, label0, label34, 0);
        methodVisitor.visitLocalVariable("ignored", "Ljava/lang/String;", null, label0, label34, 1);
        methodVisitor.visitLocalVariable("methodType", "Ljava/lang/invoke/MethodType;", null, label0, label34, 2);
        methodVisitor.visitLocalVariable("methodSignature", "Ljava/lang/String;", null, label0, label34, 3);
        methodVisitor.visitMaxs(7, 11);
        methodVisitor.visitEnd();
        return methodVisitor;
    }
}
