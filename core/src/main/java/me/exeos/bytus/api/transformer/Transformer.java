package me.exeos.bytus.api.transformer;

import me.exeos.bytus.Bytus;
import me.exeos.bytus.api.config.ConfigInterface;
import me.exeos.bytus.transformers.packer.ClassEncryptionTransformer;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

public abstract class Transformer implements ConfigInterface, Opcodes {

    public abstract boolean transform();

    protected List<ClassNode> getClasses() {
        return Bytus.instance.jarLoader.classes.values().stream().filter(classNode -> !isExcluded(classNode.name)).collect(Collectors.toList());
    }

    protected ArrayList<ClassNode> getAllClasses() {
        return new ArrayList<>(Bytus.instance.jarLoader.classes.values());
    }

    protected HashMap<String, byte[]> getResources() {
        return Bytus.instance.jarLoader.resources;
    }

    protected void addClass(ClassNode classNode) {
        Bytus.instance.jarLoader.classes.put(classNode.name, classNode);
    }

    protected void addClass(byte[] classBytes) {
        Bytus.instance.jarLoader.putClass(classBytes);
    }

    public void excludeFromPacker(ClassNode classNode) {
        ClassEncryptionTransformer.excluded.add(classNode);
    }
}
