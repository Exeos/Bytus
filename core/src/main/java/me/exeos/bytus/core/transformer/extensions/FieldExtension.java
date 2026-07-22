package me.exeos.bytus.core.transformer.extensions;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;

public class FieldExtension {

    public final ClassNode owner;
    public final FieldNode fieldNode;
    public boolean isSaltField;

    public FieldExtension(ClassNode owner, FieldNode fieldNode) {
        this(owner, fieldNode, false);
    }

    public FieldExtension(ClassNode owner, FieldNode fieldNode, boolean isSaltField) {
        this.owner = owner;
        this.fieldNode = fieldNode;
        this.isSaltField = isSaltField;
    }
}
