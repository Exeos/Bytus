package me.exeos.bytus.core.transformer.impl;

import me.exeos.bytus.asmplus.descriptor.DescriptorMember;
import me.exeos.bytus.asmplus.descriptor.DescriptorParser;
import me.exeos.bytus.asmplus.jar.JarArchive;
import me.exeos.bytus.asmplus.utils.ClassUtil;
import me.exeos.bytus.asmplus.utils.HierarchyUtil;
import me.exeos.bytus.asmplus.utils.InsnUtil;
import me.exeos.bytus.asmplus.utils.MethodUtil;
import me.exeos.bytus.core.config.BytusConfig;
import me.exeos.bytus.core.transformer.AbstractTransformer;
import me.exeos.bytus.core.transformer.Priority;
import me.exeos.bytus.core.transformer.context.ClassContext;
import me.exeos.bytus.core.transformer.context.JarContext;
import me.exeos.bytus.core.transformer.extensions.MethodExtension;
import me.exeos.bytus.core.utils.RandomUtil;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class MethodSaltTransformer extends AbstractTransformer {

    private static final DescriptorMember SALT_PARAM = new DescriptorMember("I", true, false, 0);
    private static final int SALT_MIN = 0;
    private static final int SALT_MAX = 50000;
    private static Map<String, Integer> SALT_BY_METHOD = null;

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
        if (SALT_BY_METHOD == null) {
            SALT_BY_METHOD = buildSaltMap(context.jar());
        }

        for (ClassNode classNode : context.jar().getClasses().values()) {
            for (MethodNode methodNode : classNode.methods) {
                processMethod(context, classNode, methodNode, SALT_BY_METHOD);
            }
        }
    }

    @Override
    public void transform(ClassContext context) {
        buildSaltMap(context.jarCtx().jar()).forEach(SALT_BY_METHOD::putIfAbsent);
        transform(context.jarCtx());
    }

    /**
     * Injects a salt parameter into {@code methodNode} (when eligible) and
     * rewrites every call site inside it to pass the appropriate salt argument.
     * Salt is then registered in the MethodExtension
     */
    private void processMethod(JarContext context, ClassNode classNode, MethodNode methodNode, Map<String, Integer> methodSaltMap) {
        String methodId = classNode.name + methodNode.name + methodNode.desc;
        boolean isSalted = methodSaltMap.containsKey(methodId);
        int saltSlot = isSalted
                ? injectSaltParam(context, methodNode, methodSaltMap.get(methodId))
                : 0;

        rewriteCallSites(methodNode, methodId, saltSlot, isSalted, methodSaltMap);
    }

    /**
     * Adds the salt parameter to {@code methodNode} and registers the salt
     * metadata in the pipeline extension for this method.
     *
     * @return the local variable slot allocated for the salt parameter
     */
    private int injectSaltParam(JarContext context, MethodNode methodNode, int salt) {
        int saltSlot = MethodUtil.addParam(methodNode, SALT_PARAM);

        context.pipeline()
                .getExtension(methodNode)
                .ifPresentOrElse(
                        ext -> ext.saltInfo.setSalt(salt, saltSlot),
                        () -> context.pipeline().assignExtension(methodNode, new MethodExtension(salt, saltSlot))
                );

        return saltSlot;
    }

    /**
     * Iterates over every instruction in {@code methodNode} and, for each
     * {@link MethodInsnNode} that targets a salted method, inserts the correct
     * salt push instruction and updates the callee descriptor.
     */
    private void rewriteCallSites(
            MethodNode methodNode,
            String callerId,
            int callerSaltSlot,
            boolean callerIsSalted,
            Map<String, Integer> saltByMethod
    ) {
        InsnUtil.loop(methodNode.instructions, insn -> {
            if (!(insn instanceof MethodInsnNode callInsn)) {
                return;
            }

            String calleeId = callInsn.owner + callInsn.name + callInsn.desc;
            if (!saltByMethod.containsKey(calleeId)) {
                return;
            }

            methodNode.instructions.insertBefore(
                    callInsn,
                    InsnUtil.getIntPushSalted(
                            saltByMethod.get(calleeId),
                            callerIsSalted,
                            saltByMethod.getOrDefault(callerId, 0),
                            callerSaltSlot
                    )
            );
            callInsn.desc = DescriptorParser.parseMethodDesc(callInsn.desc)
                    .addParam(SALT_PARAM)
                    .toDesc();
        });
    }

    /**
     * Assigns a random salt value to every eligible method in the jar.
     *
     * @return a map of {@code methodId -> salt}
     */
    private Map<String, Integer> buildSaltMap(JarArchive jar) {
        Map<String, Integer> methodSaltMap = new HashMap<>();

        Set<String> exclusionsByDesc = new HashSet<>();
        Set<String> exclusionsByOwner = new HashSet<>();
        buildExclusions(jar, exclusionsByDesc, exclusionsByOwner);

        for (ClassNode classNode : jar.getClasses().values()) {
            if (ClassUtil.isEnum(classNode) || exclusionsByOwner.contains(classNode.name)) {
                continue;
            }

            for (MethodNode methodNode : classNode.methods) {
                String id = classNode.name + methodNode.name + methodNode.desc;
                if (!MethodUtil.isSpecial(methodNode) && !exclusionsByDesc.contains(id)) {
                    methodSaltMap.put(id, RandomUtil.getInt(SALT_MIN, SALT_MAX));
                }
            }
        }

        return methodSaltMap;
    }

    private void buildExclusions(JarArchive jar, Set<String> exclusionsByDesc, Set<String> exclusionsByOwner) {
        for (ClassNode classNode : jar.getClasses().values()) {
            // exclude all interfaces
            if ((classNode.access & ACC_INTERFACE) != 0) {
                exclusionsByOwner.add(classNode.name);
            }
            if (classNode.superName != null && classNode.superName.equals("java/lang/Enum")) {
                exclusionsByOwner.add(classNode.name);
            }
            classNode.methods.forEach(methodNode -> exclusionsByDesc.addAll(MethodUtil.getInvokeDynamicTargets(methodNode)));
        }

        HierarchyUtil.expandExclusions(jar, exclusionsByDesc, Set.of(), exclusionsByOwner);

        // exclude main method
        exclusionsByDesc.add(config.mainClassName + "main" + "([Ljava/lang/String;)V");
    }
}
