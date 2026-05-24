package me.exeos.bytus.asmplus.descriptor;

import me.exeos.bytus.asmplus.utils.TypeUtil;

public class DescriptorMember {

    public String value;
    public boolean isPrimitive;
    public boolean isArray;
    public int arrayDepth;

    public DescriptorMember(String value, boolean isPrimitive, boolean isArray, int arrayDepth) {
        this.value = value;
        this.isPrimitive = isPrimitive;
        this.isArray = isArray;
        this.arrayDepth = arrayDepth;
    }

    public String toDesc() {
        StringBuilder prefix = new StringBuilder();
        StringBuilder suffix = new StringBuilder();

        if (isArray) {
            prefix.repeat("[", arrayDepth);
        }

        if (!isPrimitive) {
            prefix.append("L");
            suffix.append(";");
        }

        return prefix + value + suffix;
    }

    public String getType() {
        StringBuilder typeBuilder = new StringBuilder();

        if (isArray) {
            typeBuilder.repeat("[", arrayDepth);
        }

        typeBuilder.append(value);
        return typeBuilder.toString();
    }

    public DescriptorMember toNonePrimitive() {
        if (!isPrimitive) {
            return this;
        }

        return new DescriptorMember(TypeUtil.primitiveToClass(value.toCharArray()[0]), false, isArray, arrayDepth);
    }
}
