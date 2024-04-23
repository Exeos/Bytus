package com.bytus.impl.transformer.renamer;

import com.bytus.Config;
import com.bytus.core.mapping.FieldEntry;
import com.bytus.core.mapping.Lookup;
import com.bytus.core.mapping.MethodEntry;
import com.bytus.core.transformer.Transformer;
import com.bytus.utils.NameGen;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;
import org.stianloader.remapper.Remapper;

public class RenameTransformer extends Transformer {

    @Override
    public boolean transform() {
        Lookup lookup = new Lookup();
        NameGen classNameGen = new NameGen();

        /* gen mappings */
        for (ClassNode classNode : getClasses()) {
            boolean canRenameClass = Config.canRenameClass(classNode.name);
            String newCName = classNameGen.name();

            if (canRenameClass) {
                lookup.classMap.put(classNode.name, newCName);
                if (classNode.name.equals(Config.ENTRY_CLASS)) {
                    Config.ENTRY_CLASS = newCName;
                }
            }

            NameGen fieldNameGen = new NameGen();
            NameGen methodNameGen = new NameGen();
            for (FieldNode fieldNode : classNode.fields) {
                lookup.fieldEntries.add(new FieldEntry(fieldNameGen.name(), classNode.name, fieldNode.name, fieldNode.desc));
            }
            for (MethodNode methodNode : classNode.methods) {
                if ((canRenameClass || !(methodNode.name.equals("main") && methodNode.desc.equals("([Ljava/lang/String;)V")))
                        && !(methodNode.name.equals("<init>") && methodNode.desc.equals("()V"))
                        && !(methodNode.name.equals("<clinit>") && methodNode.desc.equals("()V"))) {
                    lookup.methodEntries.add(new MethodEntry(methodNameGen.name(), classNode.name, methodNode.name, methodNode.desc));
                }
            }
        }

        /* apply mappings */
        Remapper remapper = new Remapper(lookup);
        StringBuilder sharedBuilder = new StringBuilder();
        for (ClassNode classNode : getClasses()) {
            remapper.remapNode(classNode, sharedBuilder);
        }
        return true;
    }
}
