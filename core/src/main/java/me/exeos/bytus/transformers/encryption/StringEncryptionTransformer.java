package me.exeos.bytus.transformers.encryption;

import me.exeos.asmplus.utils.ASMUtils;
import me.exeos.asmplus.utils.RandomUtil;
import me.exeos.bytus.api.transformer.Transformer;
import me.exeos.bytus.api.utils.RenameUtil;
import org.objectweb.asm.tree.*;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class StringEncryptionTransformer extends Transformer {

    @Override
    public boolean transform() {
        for (ClassNode classNode : getClasses()) {
            FieldNode poolField = new FieldNode(ACC_PRIVATE | ACC_STATIC, RenameUtil.next(), "[[C", null, null);
            ArrayList<String> strings = new ArrayList<>();

            for (MethodNode methodNode : classNode.methods) {
                for (AbstractInsnNode insnNode : methodNode.instructions) {
                    if (insnNode.getType() != AbstractInsnNode.LDC_INSN) {
                        continue;
                    }

                    LdcInsnNode ldcInsn = (LdcInsnNode) insnNode;
                    if (!(ldcInsn.cst instanceof String)) {
                        continue;
                    }

                    int k1 = RandomUtil.getInt(1, 10);
                    int k2 = RandomUtil.getInt(1, 12);

                    String str = (String) ldcInsn.cst;
                    String enc = encrypt(str, k1, k2);

                    if (str.isEmpty()) {
                        continue;
                    }
                    strings.add(enc);

                    methodNode.instructions.insert(insnNode, ASMUtils.convertToIList(decryptInsns(str.length(), enc, k1, k2, classNode.name, poolField, strings.size() - 1, methodNode)));
                    methodNode.instructions.remove(insnNode);
                }
            }

            if (strings.isEmpty()) {
                continue;
            }
            classNode.fields.add(poolField);

            boolean shouldAdd = false;
            MethodNode initMethod = ASMUtils.getMethod(classNode, "<clinit>", "()V");
            if (initMethod == null) {
                initMethod = new MethodNode(ACC_STATIC, "<clinit>", "()V", null, null);
                shouldAdd = true;
            }

            if (initMethod.instructions == null || initMethod.instructions.size() == 0) {
                ASMUtils.addInstructions(getClinitInsns(classNode, poolField, strings), initMethod);
                initMethod.instructions.add(new InsnNode(RETURN));
            } else {
                initMethod.instructions.insertBefore(initMethod.instructions.getFirst(), ASMUtils.convertToIList(getClinitInsns(classNode, poolField, strings)));
            }

            if (shouldAdd) {
                classNode.methods.add(initMethod);
            }
        }

        return true;
    }

    private List<AbstractInsnNode> decryptInsns(int strLength, String enc, int k1, int k2, String className, FieldNode poolField, int poolIndex, MethodNode methodNode) {
        LinkedList<AbstractInsnNode> instructions = new LinkedList<>();

        int maxLocals = methodNode.maxLocals;

        LabelNode label0 = new LabelNode();
        LabelNode label1 = new LabelNode();
        
        instructions.add(ASMUtils.getIntPush(strLength));
        instructions.add(new IntInsnNode(NEWARRAY, T_CHAR));
        instructions.add(new VarInsnNode(ASTORE, maxLocals + 1));
        instructions.add(new InsnNode(ICONST_0));
        instructions.add(new VarInsnNode(ISTORE, maxLocals + 2));
//        instructions.add(new LdcInsnNode(enc));
//        instructions.add(new MethodInsnNode(INVOKEVIRTUAL, "java/lang/String", "toCharArray", "()[C"));
        instructions.add(new FieldInsnNode(GETSTATIC, className, poolField.name, poolField.desc));
        instructions.add(ASMUtils.getIntPush(poolIndex));
        instructions.add(new InsnNode(AALOAD));

        instructions.add(new VarInsnNode(ASTORE, maxLocals + 3));
        instructions.add(new VarInsnNode(ALOAD, maxLocals + 3));
        instructions.add(new InsnNode(ARRAYLENGTH));
        instructions.add(new VarInsnNode(ISTORE, maxLocals + 4));
        instructions.add(new InsnNode(ICONST_0));
        instructions.add(new VarInsnNode(ISTORE, maxLocals + 5));
        instructions.add(label0);
        instructions.add(new VarInsnNode(ILOAD, maxLocals + 5));
        instructions.add(new VarInsnNode(ILOAD, maxLocals + 4));
        instructions.add(new JumpInsnNode(IF_ICMPGE, label1));
        instructions.add(new VarInsnNode(ALOAD, maxLocals + 3));
        instructions.add(new VarInsnNode(ILOAD, maxLocals + 5));
        instructions.add(new InsnNode(CALOAD));
        instructions.add(new VarInsnNode(ISTORE, maxLocals + 6));
        instructions.add(new VarInsnNode(ALOAD, maxLocals + 1));
        instructions.add(new VarInsnNode(ILOAD, maxLocals + 2));
        instructions.add(new VarInsnNode(ILOAD, maxLocals + 6));
        instructions.add(ASMUtils.getIntPush(k1));
        instructions.add(new VarInsnNode(ILOAD, maxLocals + 2));
        instructions.add(new VarInsnNode(ILOAD, maxLocals + 2));
        instructions.add(ASMUtils.getIntPush(k2));
        instructions.add(new InsnNode(IREM));
        instructions.add(new InsnNode(ISHL));
        instructions.add(new InsnNode(IOR));
        instructions.add(new InsnNode(ISUB));
        instructions.add(new InsnNode(I2C));
        instructions.add(new InsnNode(CASTORE));
        instructions.add(new IincInsnNode(maxLocals + 2, 1));
        instructions.add(new IincInsnNode(maxLocals + 5, 1));
//        new JumpInsnNode(GOTO, label0)
        instructions.addAll(ASMUtils.getJumpInsns(label0));
        instructions.add(label1);
        instructions.add(new TypeInsnNode(NEW, "java/lang/String"));
        instructions.add(new InsnNode(DUP));
        instructions.add(new VarInsnNode(ALOAD, maxLocals + 1));
        instructions.add(new MethodInsnNode(INVOKESPECIAL, "java/lang/String", "<init>", "([C)V"));

        methodNode.maxLocals += 6;

        return instructions;
    }

    private List<AbstractInsnNode> getClinitInsns(ClassNode classNode, FieldNode poolField, ArrayList<String> strings) {
        LinkedList<AbstractInsnNode> instructions = new LinkedList<>();

        instructions.add(ASMUtils.getIntPush(strings.size()));
        instructions.add(new TypeInsnNode(ANEWARRAY, "[C"));
        instructions.add(new FieldInsnNode(PUTSTATIC, classNode.name, poolField.name, poolField.desc));

        for (int i = 0; i < strings.size(); i++) {
            instructions.add(new FieldInsnNode(GETSTATIC, classNode.name, poolField.name, poolField.desc));
            instructions.add(ASMUtils.getIntPush(i));
            instructions.add(new LdcInsnNode(strings.get(i)));
            instructions.add(new MethodInsnNode(INVOKEVIRTUAL, "java/lang/String", "toCharArray", "()[C"));
            instructions.add(new InsnNode(AASTORE));
        }

        return instructions;
    }

    private String encrypt(String str, int k1, int k2) {
        StringBuilder enc = new StringBuilder();
        int i = 0;
        for (char c : str.toCharArray()) {
            enc.append((char) ((int) c + (k1 | i << (i % k2))));
            i++;
        }

        return enc.toString();
    }
}
