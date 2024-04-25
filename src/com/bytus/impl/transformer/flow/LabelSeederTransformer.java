package com.bytus.impl.transformer.flow;

import com.bytus.core.transformer.Transformer;
import me.exeos.asmplus.utils.ASMUtils;
import org.objectweb.asm.tree.*;

import java.util.ArrayList;

@Deprecated
public class LabelSeederTransformer extends Transformer {

    @Override
    public boolean transform() {
        for (ClassNode classNode : getClasses()) {
            for (MethodNode methodNode : classNode.methods) {
                /* Insert proxy jumps after method return */
                ArrayList<AbstractInsnNode> proxyJumps = new ArrayList<>();
                AbstractInsnNode methodEnd = ASMUtils.getMethodEnd(methodNode);
                if (methodEnd == null) {
                    System.out.println("Skipped " + classNode.name + "." + methodNode.name + methodNode.desc + " because couldn't find return insn");
                    continue;
                }

                int count = -1;
                int every = 10;
                for (AbstractInsnNode insnNode : methodNode.instructions.toArray()) {
                    count++;
                    if (count % every != 0 && count > 15) {
                        continue;
                    }

                    AbstractInsnNode prev = insnNode.getPrevious();
                    if (prev == null) {
                        continue;
                    }

                    LabelNode exit = new LabelNode();
                    ArrayList<AbstractInsnNode> jumpToProxy = new ArrayList<>();


                    LabelNode proxy = new LabelNode();

                    // proxy to exit
                    proxyJumps.add(proxy);
                    proxyJumps.addAll(ASMUtils.getJumpInsns(exit));

                    /* exit to proxy then exit */
                    jumpToProxy.addAll(ASMUtils.getJumpInsns(proxy));
                    jumpToProxy.add(exit);


                    /* insert code */
                    methodNode.instructions.insert(prev, ASMUtils.convertToIList(jumpToProxy));
                }
                methodNode.maxStack += 30;
                methodNode.maxLocals += 30;
                methodNode.instructions.insert(methodEnd, ASMUtils.convertToIList(proxyJumps));
            }
        }
        return true;
    }
}
