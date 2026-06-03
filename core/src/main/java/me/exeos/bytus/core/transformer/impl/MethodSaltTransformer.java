package me.exeos.bytus.core.transformer.impl;

import me.exeos.bytus.asmplus.descriptor.DescriptorMember;
import me.exeos.bytus.asmplus.descriptor.DescriptorParser;
import me.exeos.bytus.asmplus.descriptor.descriptors.method.MethodDescriptor;
import me.exeos.bytus.asmplus.utils.InsnUtil;
import me.exeos.bytus.core.config.BytusConfig;
import me.exeos.bytus.core.transformer.AbstractTransformer;
import me.exeos.bytus.core.transformer.context.JarContext;
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
        return true;
    }

    @Override
    public int priority() {
        return 1;
    }

    @Override
    public void transform(JarContext context) {
        // ownerCtx + name + desc
        Set<String> exclusionsByDesc = new HashSet<>();
        // ownerCtx + name
        Set<String> exclusionsByName = new HashSet<>();
        ParamGenerifier.buildExclusions(context.jar(), exclusionsByDesc, exclusionsByName);

        Map<String, Integer> methodKeys = new HashMap<>();
        Map<String, String> originalDesc = new HashMap<>();
        Map<String, Integer> saltLocalIndex = new HashMap<>();
        for (ClassNode classNode : context.jar().getClasses().values()) {
            for (MethodNode methodNode : classNode.methods) {
                String key = classNode.name + methodNode.name + methodNode.desc;
                if (methodNode.name.equals("<init>") || methodNode.name.equals("<clinit>") || exclusionsByDesc.contains(key) || exclusionsByName.contains(classNode.name + methodNode.name)) {
                    continue;
                }

                MethodDescriptor methodDescriptor = DescriptorParser.parseMethodDesc(methodNode.desc);
                methodDescriptor.addParam(new DescriptorMember("I", true, false, 0));

                methodKeys.put(key, RandomUtil.getInt(0, Integer.MAX_VALUE / 2));
                String org = methodNode.desc;
                methodNode.desc = methodDescriptor.toDesc();
                saltLocalIndex.put(key, methodNode.maxLocals++);
                originalDesc.put(classNode.name + methodNode.name + methodNode.desc, org);
            }
        }

        for (ClassNode classNode : context.jar().getClasses().values()) {
            for (MethodNode methodNode : classNode.methods) {
                InsnUtil.loop(methodNode.instructions, insnNode -> {
                    if (insnNode instanceof MethodInsnNode methodInsnNode) {
                        String key = methodInsnNode.owner + methodInsnNode.name + methodInsnNode.desc;
                        if (methodKeys.containsKey(key)) {
                            MethodDescriptor invokeDesc = DescriptorParser.parseMethodDesc(methodInsnNode.desc);
                            DescriptorMember saltParam = new DescriptorMember("I", true, false, 0);
                            invokeDesc.addParam(saltParam);

                            int targetKey = methodKeys.get(key);
                            String k2 = classNode.name + methodNode.name + originalDesc.getOrDefault(classNode.name + methodNode.name + methodNode.desc, methodNode.desc);
                            InsnList loadkey = new InsnList();
                            if (methodKeys.containsKey(k2)) {
                                int mKey = methodKeys.get(k2);
                                int diff = mKey - targetKey;
                                int slatLocal = saltLocalIndex.get(k2);
                                loadkey.add(new VarInsnNode(ILOAD, slatLocal));
                                loadkey.add(InsnUtil.getIntPush(diff));
                                loadkey.add(new InsnNode(ISUB));
                            } else {
                                loadkey.add(InsnUtil.getIntPush(targetKey));
                            }

                            methodNode.instructions.insertBefore(insnNode, loadkey);
                            methodInsnNode.desc = invokeDesc.toDesc();
                        }
                    }
                });
            }
        }
    }
}
