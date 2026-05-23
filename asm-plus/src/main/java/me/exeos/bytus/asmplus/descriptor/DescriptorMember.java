package me.exeos.bytus.asmplus.descriptor;

public class DescriptorMember {

    public String value;
    public boolean isPrimitive;

    public DescriptorMember(String value, boolean isPrimitive) {
        this.value = value;
        this.isPrimitive = isPrimitive;
    }
}
