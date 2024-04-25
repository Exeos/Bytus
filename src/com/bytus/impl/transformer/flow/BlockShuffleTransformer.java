package com.bytus.impl.transformer.flow;

import com.bytus.core.transformer.Transformer;
import me.exeos.asmplus.codegen.block.CodeBlock;
import me.exeos.asmplus.utils.ASMUtils;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.ArrayList;
import java.util.Collections;

public class BlockShuffleTransformer extends Transformer {
    @Override
    public boolean transform() {
        for (ClassNode classNode : getClasses()) {
            for (MethodNode methodNode : classNode.methods) {
                int methodSize = methodNode.instructions.size();
                int count = -1;
                int every = 5;
                if (methodSize <= 10) {
                    if (methodSize <= 2) {
                        continue;
                    }
                    every = 1;
                }

                ArrayList<CodeBlock> blocks = new ArrayList<>();
                CodeBlock current = new CodeBlock();

                /* Split method insns into blocks */
                for (AbstractInsnNode insnNode : methodNode.instructions) {
                    boolean isLastInsn = insnNode == methodNode.instructions.getLast();

                    current.add(insnNode);

                    if (count % every == 0 || isLastInsn) {
                        if (isLastInsn) {
                            blocks.add(current);
                            break;
                        }
                        CodeBlock next = new CodeBlock().prev(current);
                        current.next(next);
                        blocks.add(current);
                        current = next;
                    }
                    count++;
                }

                /* rearrange method */
                CodeBlock entry = blocks.get(0);
                Collections.shuffle(blocks);

                ArrayList<AbstractInsnNode> rearrangedMethod = new ArrayList<>(ASMUtils.getJumpInsns(entry.entry()));
                for (CodeBlock block : blocks) {
                    rearrangedMethod.addAll(block.genBlockCode(false));
                }
                methodNode.instructions = ASMUtils.convertToIList(rearrangedMethod);
            }
        }
        return true;
    }
}
