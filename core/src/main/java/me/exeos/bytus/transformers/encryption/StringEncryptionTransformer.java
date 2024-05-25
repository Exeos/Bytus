package me.exeos.bytus.transformers.encryption;

import me.exeos.asmplus.utils.ASMUtils;
import me.exeos.bytus.api.transformer.Transformer;
import me.exeos.bytus.api.utils.RenameUtil;
import org.objectweb.asm.tree.*;

import java.util.ArrayList;

public class StringEncryptionTransformer extends Transformer {

    @Override
    public boolean transform() {
        for (ClassNode classNode : getClasses()) {
            FieldNode poolField = new FieldNode(ACC_PRIVATE | ACC_STATIC, RenameUtil.next(), "[[" + Encryptor.getRandomDesc(), null, null);
            ArrayList<String> strings = new ArrayList<>();

            for (MethodNode methodNode : classNode.methods) {
                for (AbstractInsnNode insnNode : methodNode.instructions) {
                    if (insnNode.getType() != AbstractInsnNode.LDC_INSN) {
                        continue;
                    }

                    LdcInsnNode ldcInsnNode = (LdcInsnNode) insnNode;
                    if (!(ldcInsnNode.cst instanceof String)) {
                        continue;
                    }

                    strings.add((String) ldcInsnNode.cst);
                    ArrayList<AbstractInsnNode> instructions = new ArrayList<>();

                    instructions.add(new FieldInsnNode(GETSTATIC, classNode.name, poolField.name, poolField.desc));
                    instructions.add(ASMUtils.getIntPush(strings.size() - 1));
                    instructions.add(new MethodInsnNode(INVOKESTATIC, Encryptor.encUtilClass.name, Encryptor.getterMethod.name, Encryptor.getterMethod.desc));

                    methodNode.instructions.insert(insnNode, ASMUtils.convertToIList(instructions));
                    methodNode.instructions.remove(insnNode);
                }
            }

            if (strings.isEmpty()) {
                continue;
            }

            boolean shouldAdd = false;
            MethodNode initMethod = ASMUtils.getMethod(classNode, "<clinit>", "()V");
            if (initMethod == null) {
                initMethod = new MethodNode(ACC_STATIC, "<clinit>", "()V", null, null);
                shouldAdd = true;
            }

            if (initMethod.instructions == null || initMethod.instructions.size() == 0) {
                ASMUtils.addInstructions(getInitInstructions(classNode, poolField, strings), initMethod);
                initMethod.instructions.add(new InsnNode(RETURN));
            } else {
                initMethod.instructions.insertBefore(initMethod.instructions.getFirst(), ASMUtils.convertToIList(getInitInstructions(classNode, poolField, strings)));
            }

            if (shouldAdd) {
                classNode.methods.add(initMethod);
            }

            classNode.fields.add(poolField);
        }
        return true;
    }

    private ArrayList<AbstractInsnNode> getInitInstructions(ClassNode classNode, FieldNode fieldNode, ArrayList<String> strings) {
        ArrayList<AbstractInsnNode> instructions = new ArrayList<>();
        String desc = Encryptor.getRandomDesc();

        instructions.add(ASMUtils.getIntPush(strings.size()));
        instructions.add(new TypeInsnNode(ANEWARRAY, "[" + desc));
        instructions.add(new FieldInsnNode(PUTSTATIC, classNode.name, fieldNode.name, fieldNode.desc));

        for (int i = 0; i < strings.size(); i++) {
            instructions.add(new FieldInsnNode(GETSTATIC, classNode.name, fieldNode.name, fieldNode.desc));
            instructions.add(ASMUtils.getIntPush(i));
            byte[] strBytes = strings.get(i).getBytes();
            instructions.add(ASMUtils.getIntPush(strBytes.length));
            instructions.add(new IntInsnNode(NEWARRAY, (desc.equals("B") ? T_BYTE : (desc.equals("I") ? T_INT : T_SHORT))));
            for (int j = 0; j < strBytes.length; j++) {
                instructions.add(new InsnNode(DUP));
                instructions.add(ASMUtils.getIntPush(j));
                instructions.add(ASMUtils.getIntPush(strBytes[j]));
                instructions.add(new InsnNode(BASTORE));
            }
            instructions.add(new InsnNode(AASTORE));
        }

        return instructions;
    }
}
