package me.exeos.bytus.transformers.renamer;

import me.exeos.bytus.Bytus;
import me.exeos.bytus.api.generation.UniqueNameGen;
import me.exeos.bytus.api.transformer.Transformer;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;
import org.stianloader.remapper.MemberRef;
import org.stianloader.remapper.Remapper;
import org.stianloader.remapper.SimpleHierarchyAwareMappingLookup;

import java.util.HashMap;

public class RenameTransformer extends Transformer {

    @Override
    public boolean transform() {
        StringBuilder shared = new StringBuilder();

        Remapper remapper = remap();
        HashMap<String, ClassNode> remapped = new HashMap<>();

        for (ClassNode classNode : getAllClasses()) {
            remapper.remapNode(classNode, shared);
            remapped.put(classNode.name, classNode);
        }

        Bytus.instance.jarLoader.classes = remapped;

        return true;
    }

    /* generate mappings */
    private Remapper remap() {
        SimpleHierarchyAwareMappingLookup lookup = new SimpleHierarchyAwareMappingLookup(getClasses());
        UniqueNameGen classNameGen = new UniqueNameGen();
        for (ClassNode classNode : getClasses()) {
            if (classNode.name.equals(getBootstrapName())) {
                continue;
            }
            String newClassName = classNameGen.next();
            lookup.remapClass(classNode.name, newClassName);

            UniqueNameGen classFieldGen = new UniqueNameGen();
            UniqueNameGen classMethodGen = new UniqueNameGen();

            for (FieldNode fieldNode : classNode.fields) {
                lookup.remapMember(new MemberRef(classNode.name, fieldNode.name, fieldNode.desc), classFieldGen.next());
            }

            for (MethodNode methodNode : classNode.methods) {
                if (methodNode.name.contains("<") || methodNode.name.contains(">")) {
                    continue;
                }
                lookup.remapMember(new MemberRef(classNode.name, methodNode.name, methodNode.desc), classMethodGen.next());
            }
        }

        return new Remapper(lookup);
    }
}
