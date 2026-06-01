package me.exeos.bytus.asmplus.descriptor;

import me.exeos.bytus.asmplus.utils.TypeUtil;

public record DescriptorMember(String value, boolean isPrimitive, boolean isArray, int arrayDepth) {

    public int getSlotWidth() {
        if (!isArray && isPrimitive && (value.equals("D") || value.equals("J"))) {
            return 2;
        }

        return 1;
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
        StringBuilder suffix = new StringBuilder();

        if (isArray) {
            typeBuilder.repeat("[", arrayDepth);
            if (!isPrimitive) {
                typeBuilder.append("L");
                suffix.append(";");
            }
        }

        typeBuilder.append(value);
        return typeBuilder.toString() + suffix;
    }

    public DescriptorMember toNonePrimitive() {
        if (!isPrimitive) {
            return this;
        }

        return new DescriptorMember(TypeUtil.primitiveToClass(value.toCharArray()[0]), false, isArray, arrayDepth);
    }
}
