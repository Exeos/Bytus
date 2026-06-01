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
