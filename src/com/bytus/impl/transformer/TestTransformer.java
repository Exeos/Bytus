package com.bytus.impl.transformer;

import com.bytus.core.transformer.Transformer;
import org.objectweb.asm.tree.ClassNode;

public class TestTransformer extends Transformer {

    @Override
    public boolean transform() {
        for (ClassNode classNode : getClasses()) {
            System.out.println(classNode.name);
        }

        return true;
    }
}
