package me.exeos.bytus.api.transformer;

import me.exeos.bytus.Bytus;
import me.exeos.bytus.api.config.ConfigInterface;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;

import java.util.ArrayList;
import java.util.HashMap;

public abstract class Transformer implements ConfigInterface, Opcodes {

    public abstract boolean transform();

    protected ArrayList<ClassNode> getClasses() {
        return new ArrayList<>(Bytus.instance.jarLoader.classes.values());
    }

    protected HashMap<String, byte[]> getResources() {
        return Bytus.instance.jarLoader.resources;
    }
}
