package me.exeos.bytus.core.transformer.impl;

import me.exeos.bytus.asmplus.descriptor.DescriptorMember;
import me.exeos.bytus.asmplus.descriptor.DescriptorParser;
import me.exeos.bytus.asmplus.descriptor.descriptors.method.MethodDescriptor;
import me.exeos.bytus.asmplus.jar.JarArchive;
import me.exeos.bytus.asmplus.utils.InsnUtil;
import me.exeos.bytus.asmplus.utils.MethodUtil;
import me.exeos.bytus.core.config.BytusConfig;
import me.exeos.bytus.core.transformer.AbstractTransformer;
import me.exeos.bytus.core.transformer.Priority;
import me.exeos.bytus.core.transformer.context.JarContext;
import me.exeos.bytus.core.transformer.extensions.MethodExtension;
import me.exeos.bytus.core.transformer.impl.flow.data.ParamGenerifier;
import me.exeos.bytus.core.utils.RandomUtil;
import org.objectweb.asm.tree.*;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class MethodSaltTransformer extends AbstractTransformer {

    public MethodSaltTransformer(BytusConfig config) {
        super(config);
    }

    @Override
    public boolean applies() {
        return config.salt;
    }

    @Override
    public int priority() {
        return Priority.SALT;
    }

    @Override
    public void transform(JarContext context) {
        Map<String, Integer> methodSaltMap = mapMethods(context.jar());

        for (ClassNode classNode : context.jar().getClasses().values()) {
            for (MethodNode methodNode : classNode.methods) {
                String methodIdentifier = classNode.name + methodNode.name + methodNode.desc;
                MethodDescriptor newMethodDesc = DescriptorParser
                        .parseMethodDesc(methodNode.desc)
                        .addParam(new DescriptorMember("I", true, false, 0));

                InsnUtil.loop(methodNode.instructions, insnNode -> {
                    if (!(insnNode instanceof MethodInsnNode methodInsnNode)) {
                        return;
                    }

                    String invokedIdentifier = methodInsnNode.owner + methodInsnNode.name + methodInsnNode.desc;
                    if (!methodSaltMap.containsKey(invokedIdentifier)) {
                        return;
                    }

                    int invokedSalt = methodSaltMap.get(invokedIdentifier);
                    InsnList addSaltToCall = new InsnList();
                    // if the method containing this instruction is salted as well, use its salt to create invoked salt
                    if (methodSaltMap.containsKey(methodIdentifier)) {
                        addSaltToCall.add(InsnUtil.getIntPushSalted(
                                invokedSalt,
                                methodSaltMap.get(methodIdentifier),
                                newMethodDesc.getAbsoluteSlot(
                                        newMethodDesc.getParams().size() - 1,
                                        MethodUtil.getParamSlotStart(methodNode)
                                )
                        ));
                    } else {
                        addSaltToCall.add(InsnUtil.getIntPush(invokedSalt));
                    }

                    methodNode.instructions.insertBefore(insnNode, addSaltToCall);
                    // update the desc of the MethodInsn node to match targets new descriptor
                    methodInsnNode.desc = DescriptorParser
                            .parseMethodDesc(methodInsnNode.desc)
                            .addParam(new DescriptorMember("I", true, false, 0))
                            .toDesc();
                });

                if (methodSaltMap.containsKey(methodIdentifier)) {
                    int salt = methodSaltMap.get(methodIdentifier);
                    int saltSlot = newMethodDesc.getAbsoluteSlot(
                            newMethodDesc.getParams().size() - 1,
                            MethodUtil.getParamSlotStart(methodNode)
                    );

                    context.pipeline().getExtension(methodNode).ifPresentOrElse(
                            extension -> extension.setSalt(salt, saltSlot),
                            () -> context.pipeline().assignExtension(methodNode, new MethodExtension(salt, saltSlot))
                    );

                    methodNode.desc = newMethodDesc.toDesc();
                }
            }
        }
    }

    private Map<String, Integer> mapMethods(JarArchive jar) {
        Map<String, Integer> methodSaltMap = new HashMap<>();

        // ownerCtx + name + desc
        Set<String> exclusionsByDesc = new HashSet<>();
        // ownerCtx + name
        Set<String> exclusionsByName = new HashSet<>();
        Set<String> exclusionsByOwner = new HashSet<>();
        buildExclusions(jar, exclusionsByDesc, exclusionsByName, exclusionsByOwner);

        for (ClassNode classNode : jar.getClasses().values()) {
            if (classNode.superName != null && classNode.superName.equals("java/lang/Enum")) {
                continue;
            }
            for (MethodNode methodNode : classNode.methods) {
                String methodIdentifier = classNode.name + methodNode.name + methodNode.desc;
                if (methodNode.name.equals("<init>")
                        || methodNode.name.equals("<clinit>")
                        || exclusionsByDesc.contains(methodIdentifier)
                        || exclusionsByName.contains(classNode.name + methodNode.name)
                        || exclusionsByOwner.contains(classNode.name)) {
                    continue;
                }

                methodSaltMap.put(methodIdentifier, RandomUtil.getInt(0, 50000));
            }
        }

        return methodSaltMap;
    }

    private static void buildExclusions(JarArchive jar, Set<String> exclusionsByDesc, Set<String> exclusionsByName, Set<String> exclusionsByOwner) {
        for (ClassNode classNode : jar.getClasses().values()) {
            // map tracking amount of methods declared by their ower + name
            for (MethodNode methodNode : classNode.methods) {
                // exclude all methods declared in interfaces
                if ((classNode.access & ACC_INTERFACE) != 0) {
                    exclusionsByName.add(classNode.name + methodNode.name);
                }
                if (classNode.superName != null && classNode.superName.equals("java/lang/Enum")) {
                    exclusionsByOwner.add(classNode.name);
                }

                // exclude all methods invoked by InvokeDynamic
                exclusionsByDesc.addAll(ParamGenerifier.getMethodsTargetedByInvokedynamic(methodNode));
            }
        }

        ParamGenerifier.expandExclusions(jar, exclusionsByDesc, exclusionsByName, exclusionsByOwner);

        // exclude main method
        if (jar.getManifest() != null) {
            String mainClassName = jar.getManifest().getMainAttributes().getValue("Main-Class");
            if (mainClassName != null) {
                exclusionsByDesc.add(mainClassName.replace(".", "/") + "main" + "([Ljava/lang/String;)V");
            }
        }
    }
}
