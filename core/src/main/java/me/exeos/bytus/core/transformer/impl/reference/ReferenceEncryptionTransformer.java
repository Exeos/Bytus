package me.exeos.bytus.core.transformer.impl.reference;

import me.exeos.bytus.asmplus.jar.JarArchive;
import me.exeos.bytus.core.transformer.Transformer;
import me.exeos.bytus.core.transformer.TransformerPipeline;
import me.exeos.bytus.core.utils.RandomUtil;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

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
                for (AbstractInsnNode abstractInsnNode : methodNode.instructions.toArray()) {
                    if (abstractInsnNode instanceof MethodInsnNode methodInsnNode && this.methodCall) {
                        if (methodInsnNode.getOpcode() == INVOKEVIRTUAL || methodInsnNode.getOpcode() == INVOKESTATIC) {
                            InvokeDynamicInsnNode invokeDynamicInsnNode = makeInvokeDynamicInsn(classNode, methodInsnNode, bootstrapName);
                            methodNode.instructions.insert(methodInsnNode, invokeDynamicInsnNode);
                            methodNode.instructions.remove(methodInsnNode);

                            anyCalls = true;

                            invokeDynamicCount.getAndIncrement();
                        }
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
        int accessCode = methodInsnNode.getOpcode();

        String descriptor = methodInsnNode.desc;
        if (accessCode == INVOKEVIRTUAL)
            descriptor = descriptor.replace("(", "(Ljava/lang/Object;"); // add the instance object to the descriptor

        String methodSignature = methodInsnNode.desc + "#" + accessCode + "#" + methodInsnNode.name + "#" + methodInsnNode.owner;
        return new InvokeDynamicInsnNode(RandomUtil.getString(RandomUtil.getInt(12, 36)), descriptor, bsmHandle, methodSignature);
    }

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
        methodVisitor.visitVarInsn(ALOAD, 3);
        methodVisitor.visitLdcInsn("#");
        methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "split", "(Ljava/lang/String;)[Ljava/lang/String;", false);
        methodVisitor.visitInsn(ICONST_0);
        methodVisitor.visitInsn(AALOAD);
        methodVisitor.visitVarInsn(ASTORE, 4);
        Label label5 = new Label();
        methodVisitor.visitLabel(label5);
        methodVisitor.visitVarInsn(ALOAD, 3);
        methodVisitor.visitLdcInsn("#");
        methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "split", "(Ljava/lang/String;)[Ljava/lang/String;", false);
        methodVisitor.visitInsn(ICONST_1);
        methodVisitor.visitInsn(AALOAD);
        methodVisitor.visitVarInsn(ASTORE, 5);
        Label label6 = new Label();
        methodVisitor.visitLabel(label6);
        methodVisitor.visitVarInsn(ALOAD, 3);
        methodVisitor.visitLdcInsn("#");
        methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "split", "(Ljava/lang/String;)[Ljava/lang/String;", false);
        methodVisitor.visitInsn(ICONST_2);
        methodVisitor.visitInsn(AALOAD);
        methodVisitor.visitVarInsn(ASTORE, 6);
        Label label7 = new Label();
        methodVisitor.visitLabel(label7);
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
        methodVisitor.visitVarInsn(ALOAD, 7);
        methodVisitor.visitMethodInsn(INVOKESTATIC, "java/lang/Class", "forName", "(Ljava/lang/String;)Ljava/lang/Class;", false);
        methodVisitor.visitVarInsn(ASTORE, 8);
        Label label9 = new Label();
        methodVisitor.visitLabel(label9);
        methodVisitor.visitVarInsn(ALOAD, 4);
        methodVisitor.visitVarInsn(ALOAD, 8);
        Label label10 = new Label();
        methodVisitor.visitLabel(label10);
        methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Class", "getClassLoader", "()Ljava/lang/ClassLoader;", false);
        Label label11 = new Label();
        methodVisitor.visitLabel(label11);
        methodVisitor.visitMethodInsn(INVOKESTATIC, "java/lang/invoke/MethodType", "fromMethodDescriptorString", "(Ljava/lang/String;Ljava/lang/ClassLoader;)Ljava/lang/invoke/MethodType;", false);
        methodVisitor.visitVarInsn(ASTORE, 9);
        Label label12 = new Label();
        methodVisitor.visitLabel(label12);
        methodVisitor.visitInsn(ACONST_NULL);
        methodVisitor.visitVarInsn(ASTORE, 10);
        Label label13 = new Label();
        methodVisitor.visitLabel(label13);
        methodVisitor.visitVarInsn(ALOAD, 5);
        methodVisitor.visitLdcInsn("182");
        methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "equals", "(Ljava/lang/Object;)Z", false);
        Label label14 = new Label();
        methodVisitor.visitJumpInsn(IFNE, label14);
        methodVisitor.visitVarInsn(ALOAD, 5);
        methodVisitor.visitLdcInsn("184");
        methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "equals", "(Ljava/lang/Object;)Z", false);
        Label label15 = new Label();
        methodVisitor.visitJumpInsn(IFEQ, label15);
        methodVisitor.visitLabel(label14);
        methodVisitor.visitVarInsn(ALOAD, 5);
        methodVisitor.visitLdcInsn("182");
        methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "equals", "(Ljava/lang/Object;)Z", false);
        Label label16 = new Label();
        methodVisitor.visitJumpInsn(IFEQ, label16);
        Label label17 = new Label();
        methodVisitor.visitLabel(label17);
        methodVisitor.visitVarInsn(ALOAD, 0);
        methodVisitor.visitVarInsn(ALOAD, 8);
        methodVisitor.visitVarInsn(ALOAD, 6);
        methodVisitor.visitVarInsn(ALOAD, 9);
        methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/invoke/MethodHandles$Lookup", "findVirtual", "(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/MethodHandle;", false);
        methodVisitor.visitVarInsn(ALOAD, 2);
        methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/invoke/MethodHandle", "asType", "(Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/MethodHandle;", false);
        methodVisitor.visitVarInsn(ASTORE, 10);
        methodVisitor.visitJumpInsn(GOTO, label15);
        methodVisitor.visitLabel(label16);
        methodVisitor.visitVarInsn(ALOAD, 0);
        methodVisitor.visitVarInsn(ALOAD, 8);
        methodVisitor.visitVarInsn(ALOAD, 6);
        methodVisitor.visitVarInsn(ALOAD, 9);
        methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/invoke/MethodHandles$Lookup", "findStatic", "(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/MethodHandle;", false);
        methodVisitor.visitVarInsn(ALOAD, 2);
        methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/invoke/MethodHandle", "asType", "(Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/MethodHandle;", false);
        methodVisitor.visitVarInsn(ASTORE, 10);
        methodVisitor.visitLabel(label15);
        methodVisitor.visitVarInsn(ALOAD, 10);
        methodVisitor.visitJumpInsn(IFNONNULL, label3);
        Label label18 = new Label();
        methodVisitor.visitLabel(label18);
        methodVisitor.visitInsn(ACONST_NULL);
        methodVisitor.visitLabel(label1);
        methodVisitor.visitInsn(ARETURN);
        methodVisitor.visitLabel(label3);
        methodVisitor.visitTypeInsn(NEW, "java/lang/invoke/MutableCallSite");
        methodVisitor.visitInsn(DUP);
        methodVisitor.visitVarInsn(ALOAD, 10);
        methodVisitor.visitMethodInsn(INVOKESPECIAL, "java/lang/invoke/MutableCallSite", "<init>", "(Ljava/lang/invoke/MethodHandle;)V", false);
        methodVisitor.visitLabel(label4);
        methodVisitor.visitInsn(ARETURN);
        methodVisitor.visitLabel(label2);
        methodVisitor.visitVarInsn(ASTORE, 4);
        Label label19 = new Label();
        methodVisitor.visitLabel(label19);
        methodVisitor.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
        methodVisitor.visitVarInsn(ALOAD, 4);
        methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Exception", "getMessage", "()Ljava/lang/String;", false);
        methodVisitor.visitInvokeDynamicInsn("makeConcatWithConstants", "(Ljava/lang/String;)Ljava/lang/String;", new Handle(Opcodes.H_INVOKESTATIC, "java/lang/invoke/StringConcatFactory", "makeConcatWithConstants", "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/invoke/CallSite;", false), new Object[]{"e -> \u0001"});
        methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
        Label label20 = new Label();
        methodVisitor.visitLabel(label20);
        methodVisitor.visitInsn(ACONST_NULL);
        methodVisitor.visitInsn(ARETURN);
        Label label21 = new Label();
        methodVisitor.visitLabel(label21);
        methodVisitor.visitEnd();
        classNode.methods.add(methodVisitor);
    }
}
