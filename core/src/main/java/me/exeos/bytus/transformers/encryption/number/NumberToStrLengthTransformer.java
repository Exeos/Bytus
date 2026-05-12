package me.exeos.bytus.transformers.encryption.number;

import me.exeos.asmplus.utils.ASMUtils;
import me.exeos.asmplus.utils.RandomUtil;
import me.exeos.bytus.api.transformer.Transformer;
import org.objectweb.asm.tree.*;

import java.util.ArrayList;

public class NumberToStrLengthTransformer extends Transformer {

    @Override
    public boolean transform() {
        for (ClassNode classNode : getClasses()) {
            for (MethodNode methodNode : classNode.methods) {
                for (AbstractInsnNode insnNode : methodNode.instructions) {
                    if (!ASMUtils.isIntPush(insnNode) || insnNode.getOpcode() == NEWARRAY) {
                        continue;
                    }

                    int value = ASMUtils.getIntValue(insnNode);
                    if (value < 0) {
                        continue;
                    }

                    int toAddValue = 0;
                    if (value > 15) {
                        int random = RandomUtil.getInt(1, 15);
                        toAddValue = value - random;
                        value = random;
                    }

                    ArrayList<AbstractInsnNode> instructions = new ArrayList<>();

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
}
