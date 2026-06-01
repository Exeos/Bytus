package me.exeos.bytus.core.transformer.impl;

import me.exeos.bytus.asmplus.descriptor.DescriptorMember;
import me.exeos.bytus.asmplus.descriptor.DescriptorParser;
import me.exeos.bytus.asmplus.descriptor.descriptors.method.MethodDescriptor;
import me.exeos.bytus.asmplus.jar.JarArchive;
import me.exeos.bytus.core.config.BytusConfig;
import me.exeos.bytus.core.transformer.AbstractTransformer;
import me.exeos.bytus.core.transformer.context.JarContext;
import me.exeos.bytus.core.utils.RandomUtil;
import org.objectweb.asm.tree.*;

import java.util.HashMap;
import java.util.Map;

public class Renamor extends AbstractTransformer {

    public Renamor(BytusConfig config) {
        super(config);
    }

    @Override
    public boolean applies() {
        return true;
    }

    @Override
    public int priority() {
        return 0;
    }

    @Override
    public void transform(JarContext context) {
        Map<String, String> classMappings = genClassMappings(context.jar());
        Map<String, ClassNode> newMap = new HashMap<>();

        for (Map.Entry<String, String> entry : classMappings.entrySet()) {
            newMap.put(entry.getValue(), context.jar().getClasses().get(entry.getKey()));
        }
        context.jar().setClasses(newMap);

        for (ClassNode classNode : context.jar().getClasses().values()) {
            for (FieldNode field : classNode.fields) {
                DescriptorMember fieldDesc = DescriptorParser.parseFieldDesc(field.desc);
                if (!fieldDesc.isPrimitive()) {
                    fieldDesc.setValue(classMappings.getOrDefault(fieldDesc.getValue(), fieldDesc.getValue()));
                }

                field.desc = fieldDesc.toDesc();
            }
            for (MethodNode methodNode : classNode.methods) {
                MethodDescriptor methodDesc = DescriptorParser.parseMethodDesc(methodNode.desc);
                for (int i = 0; i < methodDesc.getParams().size(); i++) {
                    DescriptorMember param = methodDesc.getParams().get(i);
                    if (!param.isPrimitive()) {
                        param.setValue(classMappings.getOrDefault(param.getValue(), param.getValue()));
                    }
                }

                DescriptorMember returnType = methodDesc.getReturnType();
                if (!returnType.isPrimitive()) {
                    returnType.setValue(classMappings.getOrDefault(returnType.getValue(), returnType.getValue()));
                }

                methodNode.desc = methodDesc.toDesc();

                for (AbstractInsnNode insnNode : methodNode.instructions) {
                    switch (insnNode) {
                        case FieldInsnNode fieldInsnNode -> {
                            fieldInsnNode.owner = classMappings.getOrDefault(fieldInsnNode.owner, fieldInsnNode.owner);
                            DescriptorMember fieldDesc = DescriptorParser.parseFieldDesc(fieldInsnNode.desc);
                            if (!fieldDesc.isPrimitive()) {
                                fieldDesc.setValue(classMappings.getOrDefault(fieldDesc.getValue(), fieldDesc.getValue()));
                                fieldInsnNode.desc = fieldDesc.toDesc();
                            }
                        }
                        case MethodInsnNode methodInsnNode -> {
                            methodInsnNode.owner = classMappings.getOrDefault(methodInsnNode.owner, methodInsnNode.owner);
                            MethodDescriptor methodInsnDesc = DescriptorParser.parseMethodDesc(methodInsnNode.desc);
                            for (int i = 0; i < methodInsnDesc.getParams().size(); i++) {
                                DescriptorMember param = methodInsnDesc.getParams().get(i);
                                if (!param.isPrimitive()) {
                                    param.setValue(classMappings.getOrDefault(param.getValue(), param.getValue()));
                                }
                            }

                            DescriptorMember insnReturnType = methodInsnDesc.getReturnType();
                            if (!insnReturnType.isPrimitive()) {
                                insnReturnType.setValue(classMappings.getOrDefault(insnReturnType.getValue(), insnReturnType.getValue()));
                            }

                            methodInsnNode.desc = methodInsnDesc.toDesc();
                        }
                        case TypeInsnNode typeInsnNode -> {
                            typeInsnNode.desc = classMappings.getOrDefault(typeInsnNode.desc, typeInsnNode.desc);
                        }
                        default -> {
                        }
                    }
                }
            }

            if (classNode.superName != null && classMappings.containsKey(classNode.superName)) {
                classNode.superName = classMappings.get(classNode.superName);
            }

            for (int i = 0; i < classNode.interfaces.size(); i++) {
                String cInterface = classNode.interfaces.get(i);
                classNode.interfaces.set(i, classMappings.getOrDefault(cInterface, cInterface));
            }

            classNode.name = classMappings.getOrDefault(classNode.name, classNode.name);
        }
    }

    private Map<String, String> genClassMappings(JarArchive archive) {
        Map<String, String> classMapping = new HashMap<>();
        for (ClassNode classNode : archive.getClasses().values()) {
            if (classNode.name.equals(config.mainClassName)) {
                classMapping.put(classNode.name, classNode.name);
            } else {
                int l = 1;
                String rnd = RandomUtil.getString(l);
                while (classMapping.containsValue(rnd)) {
                    l++;
                    rnd = RandomUtil.getString(l);
                }
                classMapping.put(classNode.name, rnd);
            }
        }

        return classMapping;
    }
}
