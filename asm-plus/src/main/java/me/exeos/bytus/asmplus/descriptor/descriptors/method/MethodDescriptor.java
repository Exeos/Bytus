package me.exeos.bytus.asmplus.descriptor.descriptors.method;

import me.exeos.bytus.asmplus.descriptor.DescriptorMember;

import java.util.List;

// TODO maybe replace with record
public class MethodDescriptor {

    public final List<DescriptorMember> params;
    public DescriptorMember returnType;

    public MethodDescriptor(List<DescriptorMember> params, DescriptorMember returnType) {
        this.params = params;
        this.returnType = returnType;
    }
}
