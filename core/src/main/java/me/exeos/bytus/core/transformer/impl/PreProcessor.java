package me.exeos.bytus.core.transformer.impl;

import me.exeos.bytus.asmplus.jar.JarArchive;
import me.exeos.bytus.core.transformer.Transformer;
import me.exeos.bytus.core.transformer.TransformerPipeline;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.List;

public class PreProcessor extends Transformer {

    public PreProcessor(JarArchive jar, List<String> exclusions, List<String> inclusions) {
        super(jar, exclusions, inclusions);
    }

    @Override
    public void transform(TransformerPipeline pipeline) {
        for (ClassNode classNode : getIncludedClasses()) {
            classNode.sourceDebug = null;
            classNode.sourceFile = null;

            for (MethodNode method : classNode.methods) {
                method.localVariables = null;
                method.parameters = null;
            }
        }
    }
}
