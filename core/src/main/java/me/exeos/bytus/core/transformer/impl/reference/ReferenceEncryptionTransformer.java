package me.exeos.bytus.core.transformer.impl.reference;

import me.exeos.bytus.asmplus.jar.JarArchive;
import me.exeos.bytus.core.transformer.Transformer;
import me.exeos.bytus.core.transformer.TransformerPipeline;
import me.exeos.bytus.core.utils.RandomUtil;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.lang.classfile.Interfaces;
import java.lang.invoke.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class ReferenceEncryptionTransformer extends Transformer {

    public static final String BOOTSTRAP_DESC = "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/String;)Ljava/lang/invoke/CallSite;";

    private final boolean methodCall, fieldAccess;

    public ReferenceEncryptionTransformer(JarArchive jar, List<String> exclusions, List<String> inclusions, boolean methodCall, boolean fieldAccess) {
        super(jar, exclusions, inclusions);
        this.methodCall = methodCall;
        this.fieldAccess = fieldAccess;
    }

    @Override
    public void transform(TransformerPipeline pipeline) {
        AtomicInteger invokeDynamicCount = new AtomicInteger();

        for (ClassNode classNode : getIncludedClasses()) {

            String bootstrapName = RandomUtil.getString(RandomUtil.getInt(12, 36));

            boolean anyCalls = false;

            for (MethodNode methodNode : classNode.methods) {
                if ((classNode.access & ACC_INTERFACE) != 0) continue;

                for (AbstractInsnNode abstractInsnNode : methodNode.instructions.toArray()) {
                    if (abstractInsnNode instanceof MethodInsnNode methodInsnNode && this.methodCall) {

                        if (methodInsnNode.name.equals("<init>")) {
                            AbstractInsnNode prev = abstractInsnNode.getPrevious();
                            if (prev.getOpcode() == DUP) {
                                AbstractInsnNode dup = prev;
                                prev = prev.getPrevious();
                                if (prev.getOpcode() == NEW) {
                                    methodNode.instructions.remove(prev);
                                    methodNode.instructions.remove(dup);
                                }
                            } else {
                                continue; // super call in a constructor
                            }
                        }

                        InvokeDynamicInsnNode invokeDynamicInsnNode = makeInvokeDynamicInsn(classNode, methodInsnNode, bootstrapName);
                        methodNode.instructions.insert(methodInsnNode, invokeDynamicInsnNode);
                        methodNode.instructions.remove(methodInsnNode);

                        anyCalls = true;

                        invokeDynamicCount.getAndIncrement();
                    }
                }
            }

            if (anyCalls)
                addBootstrapMethod(classNode, bootstrapName);
        }

        System.out.println("Replaced " + invokeDynamicCount.get() + " method calls with invokedynamic instructions.");
    }

    private InvokeDynamicInsnNode makeInvokeDynamicInsn(ClassNode owner, MethodInsnNode methodInsnNode, String bootstrapName) {
        Handle bsmHandle = new Handle(H_INVOKESTATIC, owner.name, bootstrapName, BOOTSTRAP_DESC, false);
        return new InvokeDynamicInsnNode(
                RandomUtil.getString(RandomUtil.getInt(12, 36)), // not needed
                fixDescriptor(methodInsnNode.getOpcode(), methodInsnNode),
                bsmHandle,
                getMethodSignature(owner, methodInsnNode));
    }

    private String fixDescriptor(int opcode, MethodInsnNode methodInsnNode) {
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

    private String getAccessCode(int opcode) {
        return switch (opcode) {
            case INVOKEINTERFACE, INVOKEVIRTUAL -> "A";
            case INVOKESTATIC -> "S";
            case INVOKESPECIAL -> "Z";
            default -> throw new IllegalArgumentException("Opcode " + opcode);
        };
    }

    private String getMethodSignature(ClassNode owner, MethodInsnNode methodInsnNode) {
        String methodSignature = methodInsnNode.desc + "#" + getAccessCode(methodInsnNode.getOpcode()) + "#" + methodInsnNode.name + "#" + methodInsnNode.owner;
        if (methodInsnNode.getOpcode() == INVOKESPECIAL)
            methodSignature += "#" + owner.name;
        return methodSignature;
    }

//    public static CallSite bootstrap(MethodHandles.Lookup lookup, String ignored, MethodType methodType, String methodSignature) {
//        try {
//            String memberDesc = methodSignature.split("#")[0];
//            String accessCode = methodSignature.split("#")[1];
//            String memberName = methodSignature.split("#")[2];
//            String className = methodSignature.split("#")[3].replace("/", ".");
//
//            Class<?> clazz = Class.forName(className);
//
//            MethodType correctType = MethodType.fromMethodDescriptorString(memberDesc,
//                    clazz.getClassLoader());
//
//            MethodHandle handle = null;
//
//            if (accessCode.equals("A") || accessCode.equals("S") || accessCode.equals("Z")) {
//                if (accessCode.equals("S")) {
//                    handle = lookup.findStatic(clazz, memberName, correctType).asType(methodType);
//                } else if (accessCode.equals("Z")) {
//                    if (memberName.equals("<init>"))
//                        handle = lookup.findConstructor(clazz, correctType.changeReturnType(void.class)).asType(methodType);
//                    else
//                        handle = lookup.findSpecial(clazz, memberName, correctType, Class.forName(methodSignature.split("#")[4].replace("/", "."))).asType(methodType);
//                } else {
//                    handle = lookup.findVirtual(clazz, memberName, correctType).asType(methodType);
//                }
//            }
//
//            if (handle == null)
//                return null;
//
//            return new MutableCallSite(handle);
//        } catch (Exception e) {
//            throw new RuntimeException("Bootstrap failed: " + e.getMessage(), e);
//        }
//    }
    private void addBootstrapMethod(ClassNode classNode, String bootstrapName) {
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
        methodVisitor.visitLineNumber(103, label0);
        methodVisitor.visitVarInsn(ALOAD, 3);
        methodVisitor.visitLdcInsn("#");
        methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "split", "(Ljava/lang/String;)[Ljava/lang/String;", false);
        methodVisitor.visitInsn(ICONST_0);
        methodVisitor.visitInsn(AALOAD);
        methodVisitor.visitVarInsn(ASTORE, 4);
        Label label5 = new Label();
        methodVisitor.visitLabel(label5);
        methodVisitor.visitLineNumber(104, label5);
        methodVisitor.visitVarInsn(ALOAD, 3);
        methodVisitor.visitLdcInsn("#");
        methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "split", "(Ljava/lang/String;)[Ljava/lang/String;", false);
        methodVisitor.visitInsn(ICONST_1);
        methodVisitor.visitInsn(AALOAD);
        methodVisitor.visitVarInsn(ASTORE, 5);
        Label label6 = new Label();
        methodVisitor.visitLabel(label6);
        methodVisitor.visitLineNumber(105, label6);
        methodVisitor.visitVarInsn(ALOAD, 3);
        methodVisitor.visitLdcInsn("#");
        methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "split", "(Ljava/lang/String;)[Ljava/lang/String;", false);
        methodVisitor.visitInsn(ICONST_2);
        methodVisitor.visitInsn(AALOAD);
        methodVisitor.visitVarInsn(ASTORE, 6);
        Label label7 = new Label();
        methodVisitor.visitLabel(label7);
        methodVisitor.visitLineNumber(106, label7);
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
        methodVisitor.visitLineNumber(108, label8);
        methodVisitor.visitVarInsn(ALOAD, 7);
        methodVisitor.visitMethodInsn(INVOKESTATIC, "java/lang/Class", "forName", "(Ljava/lang/String;)Ljava/lang/Class;", false);
        methodVisitor.visitVarInsn(ASTORE, 8);
        Label label9 = new Label();
        methodVisitor.visitLabel(label9);
        methodVisitor.visitLineNumber(110, label9);
        methodVisitor.visitVarInsn(ALOAD, 4);
        methodVisitor.visitVarInsn(ALOAD, 8);
        Label label10 = new Label();
        methodVisitor.visitLabel(label10);
        methodVisitor.visitLineNumber(111, label10);
        methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Class", "getClassLoader", "()Ljava/lang/ClassLoader;", false);
        Label label11 = new Label();
        methodVisitor.visitLabel(label11);
        methodVisitor.visitLineNumber(110, label11);
        methodVisitor.visitMethodInsn(INVOKESTATIC, "java/lang/invoke/MethodType", "fromMethodDescriptorString", "(Ljava/lang/String;Ljava/lang/ClassLoader;)Ljava/lang/invoke/MethodType;", false);
        methodVisitor.visitVarInsn(ASTORE, 9);
        Label label12 = new Label();
        methodVisitor.visitLabel(label12);
        methodVisitor.visitLineNumber(113, label12);
        methodVisitor.visitInsn(ACONST_NULL);
        methodVisitor.visitVarInsn(ASTORE, 10);
        Label label13 = new Label();
        methodVisitor.visitLabel(label13);
        methodVisitor.visitLineNumber(115, label13);
        methodVisitor.visitVarInsn(ALOAD, 5);
        methodVisitor.visitLdcInsn("A");
        methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "equals", "(Ljava/lang/Object;)Z", false);
        Label label14 = new Label();
        methodVisitor.visitJumpInsn(IFNE, label14);
        methodVisitor.visitVarInsn(ALOAD, 5);
        methodVisitor.visitLdcInsn("S");
        methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "equals", "(Ljava/lang/Object;)Z", false);
        methodVisitor.visitJumpInsn(IFNE, label14);
        methodVisitor.visitVarInsn(ALOAD, 5);
        methodVisitor.visitLdcInsn("Z");
        methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "equals", "(Ljava/lang/Object;)Z", false);
        Label label15 = new Label();
        methodVisitor.visitJumpInsn(IFEQ, label15);
        methodVisitor.visitLabel(label14);
        methodVisitor.visitLineNumber(116, label14);
        methodVisitor.visitFrame(Opcodes.F_FULL, 11, new Object[]{"java/lang/invoke/MethodHandles$Lookup", "java/lang/String", "java/lang/invoke/MethodType", "java/lang/String", "java/lang/String", "java/lang/String", "java/lang/String", "java/lang/String", "java/lang/Class", "java/lang/invoke/MethodType", "java/lang/invoke/MethodHandle"}, 0, new Object[]{});
        methodVisitor.visitVarInsn(ALOAD, 5);
        methodVisitor.visitLdcInsn("S");
        methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "equals", "(Ljava/lang/Object;)Z", false);
        Label label16 = new Label();
        methodVisitor.visitJumpInsn(IFEQ, label16);
        Label label17 = new Label();
        methodVisitor.visitLabel(label17);
        methodVisitor.visitLineNumber(117, label17);
        methodVisitor.visitVarInsn(ALOAD, 0);
        methodVisitor.visitVarInsn(ALOAD, 8);
        methodVisitor.visitVarInsn(ALOAD, 6);
        methodVisitor.visitVarInsn(ALOAD, 9);
        methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/invoke/MethodHandles$Lookup", "findStatic", "(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/MethodHandle;", false);
        methodVisitor.visitVarInsn(ALOAD, 2);
        methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/invoke/MethodHandle", "asType", "(Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/MethodHandle;", false);
        methodVisitor.visitVarInsn(ASTORE, 10);
        methodVisitor.visitJumpInsn(GOTO, label15);
        methodVisitor.visitLabel(label16);
        methodVisitor.visitLineNumber(118, label16);
        methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
        methodVisitor.visitVarInsn(ALOAD, 5);
        methodVisitor.visitLdcInsn("Z");
        methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "equals", "(Ljava/lang/Object;)Z", false);
        Label label18 = new Label();
        methodVisitor.visitJumpInsn(IFEQ, label18);
        Label label19 = new Label();
        methodVisitor.visitLabel(label19);
        methodVisitor.visitLineNumber(119, label19);
        methodVisitor.visitVarInsn(ALOAD, 6);
        methodVisitor.visitLdcInsn("<init>");
        methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "equals", "(Ljava/lang/Object;)Z", false);
        Label label20 = new Label();
        methodVisitor.visitJumpInsn(IFEQ, label20);
        Label label21 = new Label();
        methodVisitor.visitLabel(label21);
        methodVisitor.visitLineNumber(120, label21);
        methodVisitor.visitVarInsn(ALOAD, 0);
        methodVisitor.visitVarInsn(ALOAD, 8);
        methodVisitor.visitVarInsn(ALOAD, 9);
        methodVisitor.visitFieldInsn(GETSTATIC, "java/lang/Void", "TYPE", "Ljava/lang/Class;");
        methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/invoke/MethodType", "changeReturnType", "(Ljava/lang/Class;)Ljava/lang/invoke/MethodType;", false);
        methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/invoke/MethodHandles$Lookup", "findConstructor", "(Ljava/lang/Class;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/MethodHandle;", false);
        methodVisitor.visitVarInsn(ALOAD, 2);
        methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/invoke/MethodHandle", "asType", "(Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/MethodHandle;", false);
        methodVisitor.visitVarInsn(ASTORE, 10);
        methodVisitor.visitJumpInsn(GOTO, label15);
        methodVisitor.visitLabel(label20);
        methodVisitor.visitLineNumber(122, label20);
        methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
        methodVisitor.visitVarInsn(ALOAD, 0);
        methodVisitor.visitVarInsn(ALOAD, 8);
        methodVisitor.visitVarInsn(ALOAD, 6);
        methodVisitor.visitVarInsn(ALOAD, 9);
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
        methodVisitor.visitVarInsn(ASTORE, 10);
        methodVisitor.visitJumpInsn(GOTO, label15);
        methodVisitor.visitLabel(label18);
        methodVisitor.visitLineNumber(124, label18);
        methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
        methodVisitor.visitVarInsn(ALOAD, 0);
        methodVisitor.visitVarInsn(ALOAD, 8);
        methodVisitor.visitVarInsn(ALOAD, 6);
        methodVisitor.visitVarInsn(ALOAD, 9);
        methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/invoke/MethodHandles$Lookup", "findVirtual", "(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/MethodHandle;", false);
        methodVisitor.visitVarInsn(ALOAD, 2);
        methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/invoke/MethodHandle", "asType", "(Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/MethodHandle;", false);
        methodVisitor.visitVarInsn(ASTORE, 10);
        methodVisitor.visitLabel(label15);
        methodVisitor.visitLineNumber(128, label15);
        methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
        methodVisitor.visitVarInsn(ALOAD, 10);
        methodVisitor.visitJumpInsn(IFNONNULL, label3);
        Label label22 = new Label();
        methodVisitor.visitLabel(label22);
        methodVisitor.visitLineNumber(129, label22);
        methodVisitor.visitInsn(ACONST_NULL);
        methodVisitor.visitLabel(label1);
        methodVisitor.visitInsn(ARETURN);
        methodVisitor.visitLabel(label3);
        methodVisitor.visitLineNumber(131, label3);
        methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
        methodVisitor.visitTypeInsn(NEW, "java/lang/invoke/MutableCallSite");
        methodVisitor.visitInsn(DUP);
        methodVisitor.visitVarInsn(ALOAD, 10);
        methodVisitor.visitMethodInsn(INVOKESPECIAL, "java/lang/invoke/MutableCallSite", "<init>", "(Ljava/lang/invoke/MethodHandle;)V", false);
        methodVisitor.visitLabel(label4);
        methodVisitor.visitInsn(ARETURN);
        methodVisitor.visitLabel(label2);
        methodVisitor.visitLineNumber(132, label2);
        methodVisitor.visitFrame(Opcodes.F_FULL, 4, new Object[]{"java/lang/invoke/MethodHandles$Lookup", "java/lang/String", "java/lang/invoke/MethodType", "java/lang/String"}, 1, new Object[]{"java/lang/Exception"});
        methodVisitor.visitVarInsn(ASTORE, 4);
        Label label23 = new Label();
        methodVisitor.visitLabel(label23);
        methodVisitor.visitLineNumber(133, label23);
        methodVisitor.visitTypeInsn(NEW, "java/lang/RuntimeException");
        methodVisitor.visitInsn(DUP);
        methodVisitor.visitVarInsn(ALOAD, 4);
        methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Exception", "getMessage", "()Ljava/lang/String;", false);
        methodVisitor.visitInvokeDynamicInsn("makeConcatWithConstants", "(Ljava/lang/String;)Ljava/lang/String;", new Handle(Opcodes.H_INVOKESTATIC, "java/lang/invoke/StringConcatFactory", "makeConcatWithConstants", "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/invoke/CallSite;", false), new Object[]{"Bootstrap failed: \u0001"});
        methodVisitor.visitVarInsn(ALOAD, 4);
        methodVisitor.visitMethodInsn(INVOKESPECIAL, "java/lang/RuntimeException", "<init>", "(Ljava/lang/String;Ljava/lang/Throwable;)V", false);
        methodVisitor.visitInsn(ATHROW);
        Label label24 = new Label();
        methodVisitor.visitLabel(label24);
        methodVisitor.visitLocalVariable("memberDesc", "Ljava/lang/String;", null, label5, label2, 4);
        methodVisitor.visitLocalVariable("accessCode", "Ljava/lang/String;", null, label6, label2, 5);
        methodVisitor.visitLocalVariable("memberName", "Ljava/lang/String;", null, label7, label2, 6);
        methodVisitor.visitLocalVariable("className", "Ljava/lang/String;", null, label8, label2, 7);
        methodVisitor.visitLocalVariable("clazz", "Ljava/lang/Class;", "Ljava/lang/Class<*>;", label9, label2, 8);
        methodVisitor.visitLocalVariable("correctType", "Ljava/lang/invoke/MethodType;", null, label12, label2, 9);
        methodVisitor.visitLocalVariable("handle", "Ljava/lang/invoke/MethodHandle;", null, label13, label2, 10);
        methodVisitor.visitLocalVariable("e", "Ljava/lang/Exception;", null, label23, label24, 4);
        methodVisitor.visitLocalVariable("lookup", "Ljava/lang/invoke/MethodHandles$Lookup;", null, label0, label24, 0);
        methodVisitor.visitLocalVariable("ignored", "Ljava/lang/String;", null, label0, label24, 1);
        methodVisitor.visitLocalVariable("methodType", "Ljava/lang/invoke/MethodType;", null, label0, label24, 2);
        methodVisitor.visitLocalVariable("methodSignature", "Ljava/lang/String;", null, label0, label24, 3);
        methodVisitor.visitMaxs(7, 11);
        methodVisitor.visitEnd();
        classNode.methods.add(methodVisitor);
    }
}
