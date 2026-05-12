package me.exeos.bytus.transformers.encryption.number;

import me.exeos.asmplus.utils.ASMUtils;
import me.exeos.asmplus.utils.RandomUtil;
import me.exeos.bytus.api.transformer.Transformer;
import me.exeos.bytus.api.utils.RenameUtil;
import org.objectweb.asm.tree.*;

import java.util.ArrayList;

public class UnderOverFlowIntTransformer extends Transformer {

    @Override
    public boolean transform() {
        for (ClassNode classNode : getClasses()) {
            for (MethodNode methodNode : classNode.methods) {
                for (AbstractInsnNode insnNode : methodNode.instructions) {
                    if (!ASMUtils.isIntPush(insnNode) || insnNode.getOpcode() == NEWARRAY) {
                        continue;
                    }
                    int value = ASMUtils.getIntValue(insnNode);
                    int rand = RandomUtil.getInt(1, 1000);
                    int a = value < 0 ? Integer.MAX_VALUE - rand : Integer.MIN_VALUE + rand;
                    int b = value - (value < 0 ? Integer.MAX_VALUE : Integer.MIN_VALUE);

                    ArrayList<AbstractInsnNode> instructions = new ArrayList<>();
                    instructions.add(ASMUtils.getIntPush(a));
                    instructions.add(ASMUtils.getIntPush(value < 0 ? rand : -rand));
                    instructions.add(new InsnNode(IADD));
                    instructions.add(ASMUtils.getIntPush(b));
                    instructions.add(new InsnNode(IADD));

                    methodNode.instructions.insert(insnNode, ASMUtils.convertToIList(instructions));
                    methodNode.instructions.remove(insnNode);
                }
            }
        }

        return true;
    }
}
