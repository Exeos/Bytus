package me.exeos.bytus.asmplus.descriptor.descriptors.method;

import me.exeos.bytus.asmplus.descriptor.DescriptorMember;

import java.util.List;

public record MethodDescriptor(List<DescriptorMember> params, DescriptorMember returnType) {}
