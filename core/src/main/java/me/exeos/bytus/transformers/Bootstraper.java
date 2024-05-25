package me.exeos.bytus.transformers;

import me.exeos.bytus.api.transformer.Transformer;
import me.exeos.bytus.transformers.packer.ClassEncryptionTransformer;
import org.objectweb.asm.*;

public class Bootstraper extends Transformer {

    @Override
    public boolean transform() {

        if (isPackEnabled()) {
            addClass(decCLBootstrap());
        } else {
            addClass(defaultBootstrap());
        }

        return true;
    }

    private byte[] defaultBootstrap() {
        ClassWriter classWriter = new ClassWriter(0);
        MethodVisitor methodVisitor;

        classWriter.visit(V1_8, ACC_PUBLIC | ACC_SUPER, getBootstrapName(), null, "java/lang/Object", null);

        {
            methodVisitor = classWriter.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
            methodVisitor.visitCode();
            Label label0 = new Label();
            methodVisitor.visitLabel(label0);
            methodVisitor.visitVarInsn(ALOAD, 0);
            methodVisitor.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
            methodVisitor.visitInsn(RETURN);
            Label label1 = new Label();
            methodVisitor.visitLabel(label1);
            methodVisitor.visitMaxs(1, 1);
            methodVisitor.visitEnd();
        }
        {
            methodVisitor = classWriter.visitMethod(ACC_PUBLIC | ACC_STATIC, "main", "([Ljava/lang/String;)V", null, null);
            methodVisitor.visitCode();
            Label label0 = new Label();
            methodVisitor.visitLabel(label0);
            methodVisitor.visitVarInsn(ALOAD, 0);
            methodVisitor.visitMethodInsn(INVOKESTATIC, getMainClass(), "main", "([Ljava/lang/String;)V", false);
            Label label1 = new Label();
            methodVisitor.visitLabel(label1);
            methodVisitor.visitInsn(RETURN);
            Label label2 = new Label();
            methodVisitor.visitLabel(label2);
            methodVisitor.visitMaxs(1, 1);
            methodVisitor.visitEnd();
        }
        classWriter.visitEnd();

        return classWriter.toByteArray();
    }

    /* bootstrap with ClassLoader to decryption */
    private byte[] decCLBootstrap() {
        ClassWriter classWriter = new ClassWriter(/*ClassWriter.COMPUTE_FRAMES*/ 0);
        MethodVisitor methodVisitor;

        String decCLName = ClassEncryptionTransformer.clName;

        classWriter.visit(V1_8, ACC_PUBLIC | ACC_SUPER, getBootstrapName(), null, "java/lang/Object", null);
        {
            methodVisitor = classWriter.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
            methodVisitor.visitCode();
            Label label0 = new Label();
            methodVisitor.visitLabel(label0);
            methodVisitor.visitVarInsn(ALOAD, 0);
            methodVisitor.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
            methodVisitor.visitInsn(RETURN);
            Label label1 = new Label();
            methodVisitor.visitLabel(label1);
            methodVisitor.visitMaxs(1, 1);
            methodVisitor.visitEnd();
        }
        {
            methodVisitor = classWriter.visitMethod(ACC_PUBLIC | ACC_STATIC, "main", "([Ljava/lang/String;)V", null, null);
            methodVisitor.visitCode();
            Label label0 = new Label();
            Label label1 = new Label();
            Label label2 = new Label();
            methodVisitor.visitTryCatchBlock(label0, label1, label2, "java/lang/Exception");
            methodVisitor.visitLabel(label0);
            methodVisitor.visitTypeInsn(NEW, decCLName);
            methodVisitor.visitInsn(DUP);
            methodVisitor.visitMethodInsn(INVOKESPECIAL, decCLName, "<init>", "()V", false);
            methodVisitor.visitVarInsn(ASTORE, 1);
            Label label3 = new Label();
            methodVisitor.visitLabel(label3);
            methodVisitor.visitVarInsn(ALOAD, 1);
            methodVisitor.visitLdcInsn(getMainClass().replace("/", "."));
            methodVisitor.visitMethodInsn(INVOKEVIRTUAL, decCLName, "findClass", "(Ljava/lang/String;)Ljava/lang/Class;", false);
            methodVisitor.visitVarInsn(ASTORE, 2);
            Label label4 = new Label();
            methodVisitor.visitLabel(label4);
            methodVisitor.visitVarInsn(ALOAD, 2);
            methodVisitor.visitLdcInsn(getMainClassMain());
            methodVisitor.visitInsn(ICONST_1);
            methodVisitor.visitTypeInsn(ANEWARRAY, "java/lang/Class");
            methodVisitor.visitInsn(DUP);
            methodVisitor.visitInsn(ICONST_0);
            methodVisitor.visitLdcInsn(Type.getType("[Ljava/lang/String;"));
            methodVisitor.visitInsn(AASTORE);
            methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Class", "getMethod", "(Ljava/lang/String;[Ljava/lang/Class;)Ljava/lang/reflect/Method;", false);
            methodVisitor.visitVarInsn(ASTORE, 3);
            Label label5 = new Label();
            methodVisitor.visitLabel(label5);
            methodVisitor.visitVarInsn(ALOAD, 3);
            methodVisitor.visitInsn(ACONST_NULL);
            methodVisitor.visitInsn(ICONST_1);
            methodVisitor.visitTypeInsn(ANEWARRAY, "java/lang/Object");
            methodVisitor.visitInsn(DUP);
            methodVisitor.visitInsn(ICONST_0);
            methodVisitor.visitInsn(ACONST_NULL);
            methodVisitor.visitInsn(AASTORE);
            methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/reflect/Method", "invoke", "(Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;", false);
            methodVisitor.visitInsn(POP);
            methodVisitor.visitLabel(label1);
            Label label6 = new Label();
            methodVisitor.visitJumpInsn(GOTO, label6);
            methodVisitor.visitLabel(label2);
            methodVisitor.visitFrame(Opcodes.F_SAME1, 0, null, 1, new Object[]{"java/lang/Exception"});
            methodVisitor.visitVarInsn(ASTORE, 1);
            Label label7 = new Label();
            methodVisitor.visitLabel(label7);
            methodVisitor.visitTypeInsn(NEW, "java/lang/RuntimeException");
            methodVisitor.visitInsn(DUP);
            methodVisitor.visitVarInsn(ALOAD, 1);
            methodVisitor.visitMethodInsn(INVOKESPECIAL, "java/lang/RuntimeException", "<init>", "(Ljava/lang/Throwable;)V", false);
            methodVisitor.visitInsn(ATHROW);
            methodVisitor.visitLabel(label6);
            methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
            methodVisitor.visitInsn(RETURN);
            Label label8 = new Label();
            methodVisitor.visitLabel(label8);
            methodVisitor.visitMaxs(6, 4);
            methodVisitor.visitEnd();
        }
        classWriter.visitEnd();

        return classWriter.toByteArray();
    }
}
