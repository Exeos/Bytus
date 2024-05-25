package me.exeos.bytus.transformers.encryption;

import me.exeos.asmplus.utils.ASMUtils;
import me.exeos.asmplus.utils.RandomUtil;
import me.exeos.bytus.api.transformer.Transformer;
import me.exeos.bytus.api.utils.RenameUtil;
import org.objectweb.asm.tree.*;

import java.util.ArrayList;

public class NumberEncryptionTransformer extends Transformer {

    @Override
    public boolean transform() {
        ClassNode decClass = new ClassNode();
        decClass.visit(V1_8, ACC_PUBLIC, RenameUtil.next(), null, "java/lang/Object", null);

        MethodNode intXorMethod = xorMethod();
        decClass.methods.add(intXorMethod);

        excludeFromPacker(decClass);
        addClass(decClass);

        for (ClassNode classNode : getClasses()) {
            for (MethodNode methodNode : classNode.methods) {
                for (AbstractInsnNode insnNode : methodNode.instructions.toArray()) {
                    if (!ASMUtils.isIntPush(insnNode) || insnNode.getOpcode() == NEWARRAY) {
                        continue;
                    }
                    int value = ASMUtils.getIntValue(insnNode);
                    ArrayList<AbstractInsnNode> instructions = new ArrayList<>();

                    /* xor */
                    if (value < 0 || RandomUtil.chance(50)) {
                        int key = RandomUtil.getInt(Byte.MIN_VALUE, Byte.MAX_VALUE);
                        instructions.add(ASMUtils.getIntPush(value ^ key));
                        instructions.add(ASMUtils.getIntPush(key));
                        instructions.add(new MethodInsnNode(INVOKESTATIC, decClass.name, intXorMethod.name, intXorMethod.desc));

                        methodNode.instructions.insert(insnNode, ASMUtils.convertToIList(instructions));
                        methodNode.instructions.remove(insnNode);
                        continue;
                    }

                    int toAddValue = 0;
                    if (value > 15) {
                        int random = RandomUtil.getInt(1, 15);
                        toAddValue = value - random;
                        value = random;
                    }

                    instructions.add(new LdcInsnNode(RandomUtil.getString(value)));
                    instructions.add(new MethodInsnNode(INVOKEVIRTUAL, "java/lang/String", "length", "()I"));
                    if (toAddValue > 0) {
                        instructions.add(ASMUtils.getIntPush(toAddValue));
                        instructions.add(new InsnNode(IADD));
                    }

                    methodNode.instructions.insert(insnNode, ASMUtils.convertToIList(instructions));
                    methodNode.instructions.remove(insnNode);
                }
            }
        }

        return true;
    }


    private MethodNode xorMethod() {
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
}
