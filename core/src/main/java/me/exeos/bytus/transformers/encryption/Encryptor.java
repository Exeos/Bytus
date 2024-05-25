package me.exeos.bytus.transformers.encryption;

import me.exeos.asmplus.utils.RandomUtil;
import me.exeos.bytus.api.transformer.Transformer;
import me.exeos.bytus.api.utils.RenameUtil;
import org.objectweb.asm.Label;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

public class Encryptor extends Transformer {

    public static ClassNode encUtilClass;

    public static MethodNode getterMethod;
    public static MethodNode intXorMethod;
    public static MethodNode byteXorMethod;

    @Override
    public boolean transform() {
        Encryptor.getterMethod = getGetterMethod();
        Encryptor.intXorMethod = getIntXorMethod();
        Encryptor.byteXorMethod = getByteXorMethod();

        Encryptor.encUtilClass = getGetterClass();
        addClass(encUtilClass);

        return true;
    }

    private ClassNode getGetterClass() {
        ClassNode classNode = new ClassNode();
        classNode.visit(V1_8, ACC_PUBLIC, RenameUtil.next(), null, "java/lang/Object", null);
        classNode.methods.add(getterMethod);
        classNode.methods.add(intXorMethod);
        classNode.methods.add(byteXorMethod);

        return classNode;
    }

    private MethodNode getGetterMethod() {
        MethodNode methodNode = new MethodNode(ACC_PUBLIC | ACC_STATIC, RenameUtil.next(), "([[" + getRandomDesc() + "I)Ljava/lang/String;", null, null);

        methodNode.visitCode();
        methodNode.visitTypeInsn(NEW, "java/lang/String");
        methodNode.visitInsn(DUP);
        methodNode.visitVarInsn(ALOAD, 0);
        methodNode.visitVarInsn(ILOAD, 1);
        methodNode.visitInsn(AALOAD);
        methodNode.visitMethodInsn(INVOKESPECIAL, "java/lang/String", "<init>", "([B)V", false);
        methodNode.visitInsn(ARETURN);
        methodNode.visitMaxs(4, 2);
        methodNode.visitEnd();

        return methodNode;
    }

    private MethodNode getIntXorMethod() {
        MethodNode methodNode = new MethodNode(ACC_PUBLIC | ACC_STATIC, RenameUtil.next(), "(II)I", null, null);
        methodNode.visitCode();
        methodNode.visitVarInsn(ILOAD, 0);
        methodNode.visitVarInsn(ILOAD, 1);
        methodNode.visitInsn(IXOR);
        methodNode.visitInsn(IRETURN);
        methodNode.visitMaxs(2, 2);
        methodNode.visitEnd();

        return methodNode;
    }

    private MethodNode getByteXorMethod() {
        MethodNode methodNode = new MethodNode(ACC_PUBLIC | ACC_STATIC, RenameUtil.next(), "([BI)[B", null, null);
        methodNode.visitCode();
        methodNode.visitInsn(ICONST_0);
        methodNode.visitVarInsn(ISTORE, 3);
        Label label0 = new Label();
        methodNode.visitLabel(label0);
        methodNode.visitVarInsn(ILOAD, 3);
        methodNode.visitVarInsn(ALOAD, 1);
        methodNode.visitInsn(ARRAYLENGTH);
        Label label1 = new Label();
        methodNode.visitJumpInsn(IF_ICMPGE, label1);
        methodNode.visitVarInsn(ALOAD, 1);
        methodNode.visitVarInsn(ILOAD, 3);
        methodNode.visitInsn(DUP2);
        methodNode.visitInsn(BALOAD);
        methodNode.visitVarInsn(ILOAD, 2);
        methodNode.visitInsn(IXOR);
        methodNode.visitInsn(I2B);
        methodNode.visitInsn(BASTORE);
        methodNode.visitIincInsn(3, 1);
        methodNode.visitJumpInsn(GOTO, label0);
        methodNode.visitLabel(label1);
        methodNode.visitVarInsn(ALOAD, 1);
        methodNode.visitInsn(ARETURN);
        methodNode.visitMaxs(4, 4);
        methodNode.visitEnd();

        return methodNode;
    }

    public static String getRandomDesc() {
        String[] desc = new String[] {"B", "I", "S"};
        return desc[RandomUtil.getInt(0, desc.length - 1)];
    }
}
