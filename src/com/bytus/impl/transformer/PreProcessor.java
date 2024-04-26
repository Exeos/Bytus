package com.bytus.impl.transformer;

import com.bytus.core.transformer.Transformer;
import me.exeos.asmplus.utils.ASMUtils;
import me.exeos.asmplus.utils.RandomUtil;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

public class PreProcessor extends Transformer {

    @Override
    public boolean transform() {
        for (ClassNode classNode : getClasses()) {
            stripDebugInfo(classNode);
            for (MethodNode methodNode : classNode.methods) {
                stripDebugInfo(methodNode);
//                insertMessages(methodNode);
            }
        }
        return true;
    }

    private void stripDebugInfo(ClassNode classNode) {
        classNode.sourceFile = null;
        classNode.sourceDebug = null;
    }

    private void stripDebugInfo(MethodNode methodNode) {
        methodNode.localVariables = null;
        methodNode.parameters = null;
    }

    private void insertMessages(MethodNode methodNode) {
        int amount = 1;
        int methodSize = methodNode.instructions.size();
        if (methodSize < 2) {
            return;
        }
        if (methodSize > 5) {
            amount = 2;
        }
        if (methodSize > 20) {
            amount = 3;
        }
        if (methodSize > 50) {
            amount = 5;
        }

        for (int i = 0; i < amount; i++) {
            methodNode.instructions
                    .insert(
                            methodNode.instructions.get(RandomUtil.getInt(0, methodSize)),
                            ASMUtils.convertToIList(
                                    ASMUtils.getCheckCastMessage(com.bytus.utils.RandomUtil.randomPhrase())
                            )
                    );
        }
    }
}
