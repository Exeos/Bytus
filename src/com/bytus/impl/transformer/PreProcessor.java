package com.bytus.impl.transformer;

import com.bytus.core.transformer.Transformer;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

public class PreProcessor extends Transformer {

    @Override
    public boolean transform() {
        for (ClassNode classNode : getClasses()) {
            stripDebugInfo(classNode);
            for (MethodNode methodNode : classNode.methods) {
                stripDebugInfo(methodNode);
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
}
