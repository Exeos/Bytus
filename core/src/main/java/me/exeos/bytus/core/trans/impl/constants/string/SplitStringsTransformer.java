package me.exeos.bytus.core.trans.impl.constants.string;

import me.exeos.bytus.asmplus.jar.JarArchive;
import me.exeos.bytus.core.config.BytusConfig;
import me.exeos.bytus.core.trans.AbstractTransformer;
import me.exeos.bytus.core.trans.context.InsnListContext;
import me.exeos.bytus.core.transformer.Transformer;
import me.exeos.bytus.core.transformer.TransformerPipeline;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;

import java.util.List;

public final class SplitStringsTransformer implements AbstractTransformer {

    @Override
    public boolean applies(BytusConfig config) {
        return false;
    }

    @Override
    public int priority() {
        return 0;
    }

    @Override
    public void transform(InsnListContext context) {
        for (AbstractInsnNode insnNode : context.insnList()) {
            if (insnNode instanceof LdcInsnNode ldcInsnNode && ldcInsnNode.cst instanceof String string) {
                String[] split = string.split("(?<=\\G.{15})"); // split into strings with length 5
                if (split.length > 1) {
                    // replace the original string with the first part
                    ldcInsnNode.cst = split[0];

                    InsnList concat = new InsnList();
                    // insert instructions to load the remaining parts and concatenate them
                    for (int i = 1; i < split.length; i++) {
                        concat.add(new LdcInsnNode(split[i]));
                        concat.add(new MethodInsnNode(INVOKEVIRTUAL, "java/lang/String",
                                "concat", "(Ljava/lang/String;)Ljava/lang/String;", false));
                    }

                    context.insnList().insert(insnNode, concat);
                }
            }
        }
    }
}
