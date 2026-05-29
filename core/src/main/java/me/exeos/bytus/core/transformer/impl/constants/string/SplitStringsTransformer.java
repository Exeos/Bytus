package me.exeos.bytus.core.transformer.impl.constants.string;

import me.exeos.bytus.asmplus.jar.JarArchive;
import me.exeos.bytus.core.transformer.Transformer;
import me.exeos.bytus.core.transformer.TransformerPipeline;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;

import java.util.List;

public final class SplitStringsTransformer extends Transformer {
    public SplitStringsTransformer(JarArchive jar, List<String> exclusions, List<String> inclusions) {
        super(jar, exclusions, inclusions);
    }

    @Override
    public void transform(TransformerPipeline pipeline) {
        getIncludedClasses().forEach(classNode ->
                classNode.methods.forEach(methodNode ->
                        methodNode.instructions.forEach(insnNode -> {
                            if (insnNode instanceof LdcInsnNode ldcInsnNode && ldcInsnNode.cst instanceof String string) {
                                String[] split = string.split("(?<=\\G.{10})"); // split into strings with length 10
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

                                    methodNode.instructions.insert(insnNode, concat);
                                }
                            }
                        })));
    }
}
