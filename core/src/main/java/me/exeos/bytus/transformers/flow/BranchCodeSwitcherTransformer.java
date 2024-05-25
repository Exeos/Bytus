package me.exeos.bytus.transformers.flow;

import me.exeos.asmplus.utils.ASMUtils;
import me.exeos.bytus.api.transformer.Transformer;
import org.objectweb.asm.tree.*;

public class BranchCodeSwitcherTransformer extends Transformer {

    @Override
    public boolean transform() {
        for (ClassNode classNode : getClasses()) {
            for (MethodNode methodNode : classNode.methods) {
                for (AbstractInsnNode insnNode : methodNode.instructions) {
                    if (insnNode.getType() != AbstractInsnNode.JUMP_INSN) {
                        continue;
                    }

                    JumpInsnNode jumpInsnNode = (JumpInsnNode) insnNode;
                    if (jumpInsnNode.getOpcode() < IFEQ || jumpInsnNode.getOpcode() > IF_ACMPNE) {
                        continue;
                    }

                    try {
                        ASMUtils.setOpcode(jumpInsnNode, ASMUtils.getOppositeJumpCode(jumpInsnNode.getOpcode()));
                    } catch (Exception e) {
                        System.out.println("Error setting opcode. Ignored");
                        e.printStackTrace();
                        continue;
                    }

                    LabelNode entry = new LabelNode();
                    LabelNode original = jumpInsnNode.label;
                    jumpInsnNode.label = entry;

                    methodNode.instructions.insert(insnNode, entry);
                    methodNode.instructions.insert(insnNode, ASMUtils.convertToIList(ASMUtils.getJumpInsns(original)));
                }
            }
        }

        return true;
    }
}
