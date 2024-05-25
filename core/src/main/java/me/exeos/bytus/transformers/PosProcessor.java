package me.exeos.bytus.transformers;

import me.exeos.asmplus.utils.ASMUtils;
import me.exeos.bytus.api.transformer.Transformer;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.Collections;

public class PosProcessor extends Transformer {

    @Override
    public boolean transform() {
        for (ClassNode classNode : getClasses()) {
            ASMUtils.removeDebugInfos(classNode);
            for (MethodNode methodNode : classNode.methods) {
                ASMUtils.removeDebugInfos(methodNode);
            }

            Collections.shuffle(classNode.fields);
            Collections.shuffle(classNode.methods);
        }
        return true;
    }
}
