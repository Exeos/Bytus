package me.exeos.bytus.transformers;

import me.exeos.asmplus.utils.ASMUtils;
import me.exeos.bytus.api.transformer.Transformer;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

public class PreProcessor extends Transformer {

    @Override
    public boolean transform() {
        for (ClassNode classNode : getClasses()) {
            ASMUtils.removeDebugInfos(classNode);
            for (MethodNode methodNode : classNode.methods) {
                ASMUtils.removeDebugInfos(methodNode);
            }
        }
        return true;
    }
}
