package me.exeos.bytus.core.transformer;

import me.exeos.bytus.asmplus.jar.JarArchive;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;

import java.util.List;

public abstract class Transformer implements Opcodes {

    private final JarArchive jar;

    private final List<String> exclusions;
    private final List<String> inclusions;

    // TODO supplying the jarCtx to each transformer is kinda retarded
    public Transformer(JarArchive jar, List<String> exclusions, List<String> inclusions) {
        this.jar = jar;
        this.exclusions = exclusions;
        this.inclusions = inclusions;
    }

    public abstract void transform(TransformerPipeline pipeline);

    protected JarArchive getJar() {
        return jar;
    }

    protected List<ClassNode> getIncludedClasses() {
        return jar.classes().values().stream().filter(this::isIncluded).toList();
    }

    private boolean isIncluded(ClassNode classNode) {
        if (!inclusions.isEmpty()) {
            return inclusions.contains(classNode.name);
        }

        return !exclusions.contains(classNode.name);
    }
}
