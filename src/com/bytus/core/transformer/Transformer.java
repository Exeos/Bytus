package com.bytus.core.transformer;

import com.bytus.Bytus;
import com.bytus.core.jarloader.BytusJL;
import com.bytus.core.transformer.annotations.TInfo;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;

import java.util.Collection;
import java.util.HashMap;

public abstract class Transformer implements Opcodes {

    public final String name;
    public final String desc;

    public final int priority;

    public Transformer() {
        TInfo infos = getClass().getDeclaredAnnotation(TInfo.class);

        if (infos != null) {
            name = infos.name();
            desc = infos.desc();
            priority = infos.priority();
        } else {
            name = "Name not defined.";
            desc = "Desc not defined.";
            priority = 0;
        }
    }

    public Collection<ClassNode> getClasses() {
        return BytusJL.getClasses().values();
    }

    public HashMap<String, byte[]> getResources() {
        return BytusJL.getResources();
    }

    /**
     * Run the transformer
     *
     * @return true on success, false on fail
     * */
    public abstract boolean transform();
}
