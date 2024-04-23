package com.bytus.core.mapping;

public class FieldEntry {

    public final String newName;
    private final String srcOwner;
    private final String srcName;
    private final String srcDesc;

    public FieldEntry(String newName, String srcOwner, String srcName, String srcDesc) {
        this.newName = newName;
        this.srcOwner = srcOwner;
        this.srcName = srcName;
        this.srcDesc = srcDesc;
    }

    public boolean match(String owner, String name, String dec) {
        return owner.equals(srcOwner) && name.equals(srcName) && dec.equals(srcDesc);
    }
}
