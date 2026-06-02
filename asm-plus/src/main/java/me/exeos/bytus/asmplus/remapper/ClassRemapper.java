package me.exeos.bytus.asmplus.remapper;

import me.exeos.bytus.asmplus.descriptor.DescriptorMember;
import me.exeos.bytus.asmplus.descriptor.DescriptorParser;
import me.exeos.bytus.asmplus.descriptor.descriptors.method.MethodDescriptor;
import me.exeos.bytus.asmplus.jar.JarArchive;
import org.objectweb.asm.ConstantDynamic;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ClassRemapper {

    private final Map<String, String> mapping;

    public ClassRemapper(Map<String, String> mapping) {
        this.mapping = mapping;
    }

    public void remap(JarArchive archive) {
        updateArchiveMap(archive);

        for (ClassNode classNode : archive.getClasses().values()) {
            classNode.name = getMapped(classNode.name);
            classNode.superName = getMapped(classNode.superName);
            classNode.interfaces.replaceAll(this::getMapped);
            classNode.signature = null;
            if (classNode.visibleAnnotations != null) {
                classNode.visibleAnnotations.replaceAll(this::remapAnnotation);
            }
            if (classNode.invisibleAnnotations != null) {
                classNode.invisibleAnnotations.replaceAll(this::remapAnnotation);
            }
            classNode.fields.forEach(this::remapFieldNode);
            classNode.methods.forEach(this::remapMethodNode);
        }

        for (Map.Entry<String, String> entry : mapping.entrySet()) {
            System.out.println(entry.getKey() + " -> " + entry.getValue());
        }
    }

    private void updateArchiveMap(JarArchive archive) {
        Map<String, ClassNode> newMap = new HashMap<>();
        for (Map.Entry<String, String> entry : mapping.entrySet()) {
            newMap.put(entry.getValue(), archive.getClasses().get(entry.getKey()));
        }
        archive.setClasses(newMap);
    }

    private void remapFieldNode(FieldNode target) {
        target.desc = remapDescMember(
                DescriptorParser.parseFieldDesc(target.desc)
        ).toDesc();
    }

    private void remapMethodNode(MethodNode target) {
        target.desc = remapMethodDesc(DescriptorParser.parseMethodDesc(target.desc)).toDesc();

        for (AbstractInsnNode insnNode : target.instructions) {
            switch (insnNode) {
                case FieldInsnNode fieldInsnNode -> remapFieldInsnNode(fieldInsnNode);
                case MethodInsnNode methodInsnNode -> remapMethodInsnNode(methodInsnNode);
                case TypeInsnNode typeInsnNode ->
                        typeInsnNode.desc = remapDescMember(DescriptorParser.parseType(typeInsnNode.desc)).toType();
                case LdcInsnNode ldcInsnNode -> remapLdcInsnNode(ldcInsnNode);
                case InvokeDynamicInsnNode indy -> {
                    MethodDescriptor remapped = remapMethodDesc(DescriptorParser.parseMethodDesc(indy.desc));
                    indy.desc = remapped.toDesc();

                    for (int i = 0; i < indy.bsmArgs.length; i++) {
                        Object bsmArg = indy.bsmArgs[i];

                        switch (bsmArg) {
                            case Handle handle -> indy.bsmArgs[i] = remapHandle(handle);
                            case Type type -> indy.bsmArgs[i] = remapType(type);
                            default -> System.out.println("Ignored BSM-Arg");
                        }
                    }
                }
                default -> {
                }
            }
        }
    }

    private void remapLdcInsnNode(LdcInsnNode ldcInsnNode) {
        switch (ldcInsnNode.cst) {
            case Handle handle -> ldcInsnNode.cst = remapHandle(handle);
            case Type type -> ldcInsnNode.cst = remapType(type);
            case ConstantDynamic constantDynamic -> {
                Handle bsm = remapHandle(constantDynamic.getBootstrapMethod());
                List<Object> bsmArgs = new ArrayList<>();
                for (int i = 0; i < constantDynamic.getBootstrapMethodArgumentCount(); i++) {
                    switch (constantDynamic.getBootstrapMethodArgument(i)) {
                        case Handle handle -> {
                            bsmArgs.add(remapHandle(handle));
                        }
                        case Type type -> {
                            bsmArgs.add(remapType(type));
                        }
                        default -> System.out.println("Ignored BSM-Argument");
                    }
                }

                ldcInsnNode.cst = new ConstantDynamic(
                        constantDynamic.getName(),
                        remapMethodDesc(DescriptorParser.parseMethodDesc(constantDynamic.getDescriptor())).toDesc(),
                        bsm,
                        bsmArgs
                );
            }
            default -> {
            }
        }
    }

    private AnnotationNode remapAnnotation(AnnotationNode annotationNode) {
        annotationNode.desc = remapDescMember(DescriptorParser.parseMember(annotationNode.desc)).toDesc();
        if (annotationNode.values != null) {
            annotationNode.values.replaceAll(this::remapAnnotationValue);
        }

        return annotationNode;
    }

    private Object remapAnnotationValue(Object value) {
        switch (value) {
            case Type type -> {
                return remapType(type);
            }
            case AnnotationNode inner -> {
                return remapAnnotation(inner);
            }
            case List list -> {
                for (int i1 = 0; i1 < list.size(); i1++) {
                    list.set(i1, remapAnnotationValue(list.get(i1)));
                }
                return value;
            }
            default -> {
                return value;
            }
        }
    }

    private void remapFieldInsnNode(FieldInsnNode target) {
        target.owner = remapDescMember(DescriptorParser.parseType(target.owner)).toType();
        target.desc = remapDescMember(DescriptorParser.parseFieldDesc(target.desc)).toDesc();
    }

    private void remapMethodInsnNode(MethodInsnNode target) {
        target.owner = remapDescMember(DescriptorParser.parseType(target.owner)).toType();
        target.desc = remapMethodDesc(DescriptorParser.parseMethodDesc(target.desc)).toDesc();
    }

    private Handle remapHandle(Handle target) {
        Type t = Type.getType(target.getDesc());
        String remappedDesc = t.getSort() == Type.METHOD ?
                remapMethodDesc(DescriptorParser.parseMethodDesc(target.getDesc())).toDesc()
                :
                remapDescMember(DescriptorParser.parseMembers(target.getDesc()).getFirst()).toDesc();

        return new Handle(
                target.getTag(),
                getMapped(target.getOwner()),
                target.getName(),
                remappedDesc,
                target.isInterface()
        );
    }

    private Type remapType(Type target) {
        if (target.getSort() == Type.METHOD) {
            return Type.getType(
                    remapMethodDesc(
                            DescriptorParser.parseMethodDesc(target.getDescriptor())
                    ).toDesc()
            );
        }

        return Type.getType(
                remapDescMember(
                        DescriptorParser.parseMembers(target.getDescriptor()).getFirst()
                ).toDesc()
        );
    }

    private MethodDescriptor remapMethodDesc(MethodDescriptor target) {
        target.getParams().forEach(this::remapDescMember);
        remapDescMember(target.getReturnType());

        return target;
    }

    private DescriptorMember remapDescMember(DescriptorMember target) {
        if (!target.isPrimitive()) {
            target.setValue(getMapped(target.getValue()));
        }

        return target;
    }

    private String getMapped(String original) {
        return mapping.getOrDefault(original, original);
    }
}
