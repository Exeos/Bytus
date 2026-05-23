package me.exeos.bytus.asmplus.descriptor.descriptors.method;

import me.exeos.bytus.asmplus.descriptor.DescriptorMember;

import java.util.List;

// TODO maybe replace with record
public class MethodDescriptor {

    public final List<DescriptorMember> args;
    public DescriptorMember returnType;

    public MethodDescriptor(List<DescriptorMember> args, DescriptorMember returnType) {
        this.args = args;
        this.returnType = returnType;
    }
}
