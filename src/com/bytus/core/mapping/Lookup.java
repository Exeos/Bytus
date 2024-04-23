package com.bytus.core.mapping;

import org.stianloader.remapper.MappingLookup;

import java.util.ArrayList;
import java.util.HashMap;

public class Lookup implements MappingLookup {

    public final HashMap<String, String> classMap = new HashMap<>();
    public final ArrayList<FieldEntry> fieldEntries = new ArrayList<>();
    public final ArrayList<MethodEntry> methodEntries = new ArrayList<>();

    @Override
    public String getRemappedClassName(String srcName) {
        return classMap.getOrDefault(srcName, srcName);
    }

    @Override
    public String getRemappedFieldName(String srcOwner, String srcName, String srcDesc) {
        for (FieldEntry fieldEntry : fieldEntries) {
            if (fieldEntry.match(srcOwner, srcName, srcDesc)) {
                return fieldEntry.newName;
            }
        }
        return srcName;
    }

    @Override
    public String getRemappedMethodName(String srcOwner, String srcName, String srcDesc) {
        for (MethodEntry methodEntry : methodEntries) {
            if (methodEntry.match(srcOwner, srcName, srcDesc)) {
                return methodEntry.newName;
            }
        }
        return srcName;
    }
}
