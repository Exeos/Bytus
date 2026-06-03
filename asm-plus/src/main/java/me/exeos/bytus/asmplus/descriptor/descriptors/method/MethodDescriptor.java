package me.exeos.bytus.asmplus.descriptor.descriptors.method;

import me.exeos.bytus.asmplus.descriptor.DescriptorMember;

import java.util.List;

public class MethodDescriptor {
    private List<DescriptorMember> params;
    private DescriptorMember returnType;

    public MethodDescriptor(List<DescriptorMember> params, DescriptorMember returnType) {
        this.params = params;
        this.returnType = returnType;
    }

    public String toDesc() {
        StringBuilder descBuilder = new StringBuilder();
        descBuilder.append("(");
        for (DescriptorMember param : params) {
            descBuilder.append(param.toDesc());
        }
        descBuilder.append(")");
        descBuilder.append(returnType.toDesc());

        return descBuilder.toString();
    }

    public int getRelativeSlot(DescriptorMember of) {
        return getAbsoluteSlot(of, 0);
    }

    public int getAbsoluteSlot(DescriptorMember of, int offset) {
        int slot = offset;
        for (DescriptorMember descriptorMember : getParams()) {
            if (descriptorMember == of) {
                break;
            }
            slot += descriptorMember.getSlotWidth();
        }

        return slot;
    }

    public void addParam(DescriptorMember param) {
        params.add(param);
    }

    public List<DescriptorMember> getParams() {
        return params;
    }

    public void setParams(List<DescriptorMember> params) {
        this.params = params;
    }

    public DescriptorMember getReturnType() {
        return returnType;
    }

    public void setReturnType(DescriptorMember returnType) {
        this.returnType = returnType;
    }
}
