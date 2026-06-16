package me.exeos.bytus.core.transformer.impl;

import me.exeos.bytus.asmplus.analysis.hierarchy.edge.ClassEdge;
import me.exeos.bytus.asmplus.analysis.hierarchy.edge.MethodEdge;
import me.exeos.bytus.asmplus.descriptor.DescriptorMember;
import me.exeos.bytus.asmplus.descriptor.DescriptorParser;
import me.exeos.bytus.asmplus.jar.JarArchive;
import me.exeos.bytus.asmplus.utils.*;
import me.exeos.bytus.core.config.BytusConfig;
import me.exeos.bytus.core.transformer.AbstractTransformer;
import me.exeos.bytus.core.transformer.Priority;
import me.exeos.bytus.core.transformer.context.JarContext;
import me.exeos.bytus.core.transformer.context.MethodContext;
import me.exeos.bytus.core.transformer.extensions.MethodExtension;
import me.exeos.bytus.core.utils.RandomUtil;
import org.objectweb.asm.Handle;
import org.objectweb.asm.tree.*;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

public class MethodSaltTransformer extends AbstractTransformer {

    private static final DescriptorMember SALT_PARAM = new DescriptorMember("I", true, false, 0);
    private static final int SALT_MIN = 0;
    private static final int SALT_MAX = 50000;
    private static final Set<AbstractInsnNode> rewrittenCallees = new HashSet<>();
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
            SALT_BY_METHOD = buildSaltMap(context);
        }

        Map<String, ClassEdge> hierarchy = context.getExtension().getHierarchyNameMapped();
        for (ClassNode classNode : context.jar().getClasses().values()) {
            for (MethodNode methodNode : classNode.methods) {
                processMethod(context, hierarchy, classNode, methodNode);
            }
        }

        for (ClassNode classNode : context.jar().getClasses().values()) {
            for (MethodNode methodNode : classNode.methods) {
                String methodId = classNode.name + methodNode.name + methodNode.desc;
                boolean isSalted = SALT_BY_METHOD.containsKey(methodId);

                if (isSalted) {
                    injectSaltParam(context, methodNode, SALT_BY_METHOD.get(methodId));
                }
            }
        }
    }

    @Override
    public void transform(MethodContext context) {
        buildSaltMap(context.jarCtx()).forEach(SALT_BY_METHOD::putIfAbsent);
        transform(context.jarCtx());
    }

    /**
     * Injects a salt parameter into {@code methodNode} (when eligible) and
     * rewrites every call site inside it to pass the appropriate salt argument.
     * Salt is then registered in the MethodExtension
     */
    private void processMethod(JarContext context, Map<String, ClassEdge> hierarchy, ClassNode classNode, MethodNode methodNode) {
        String methodId = classNode.name + methodNode.name + methodNode.desc;
        boolean isSalted = SALT_BY_METHOD.containsKey(methodId);
        int saltSlot = isSalted
                ? MethodUtil.getNewParamSlot(methodNode, SALT_PARAM)
                : 0;

        if (isSalted)
            MethodUtil.remapLocals(methodNode.instructions, saltSlot);

        rewriteCallSites(hierarchy, methodNode, methodId, saltSlot, isSalted);
    }

    /**
     * Registers the salt metadata in the pipeline extension for this method.
     */
    private void injectSaltParam(JarContext context, MethodNode methodNode, int salt) {
        MethodExtension extension = context.pipeline().getExtension(methodNode);
        if (!extension.saltInfo.hasSalt()) {
            extension.saltInfo.setSalt(salt, MethodUtil.addParam(methodNode, SALT_PARAM, false));
        }

        extension.saltInfo.getSaltSlot();
    }

    /**
     * Iterates over every instruction in {@code methodNode} and, for each
     * {@link MethodInsnNode} that targets a salted method, inserts the correct
     * salt push instruction and updates the callee descriptor.
     */
    private void rewriteCallSites(
            Map<String, ClassEdge> hierarchy,
            MethodNode methodNode,
            String callerId,
            int callerSaltSlot,
            boolean callerIsSalted
    ) {
        InsnUtil.loop(methodNode.instructions, insn -> {
            if (rewrittenCallees.contains(insn)) {
                return;
            }

            switch (insn) {
                case MethodInsnNode callInsn -> {
                    if (!hierarchy.containsKey(callInsn.owner)) {
                        return;
                    }

                    AtomicReference<String> calleeId = new AtomicReference<>(null);
                    hierarchy.get(callInsn.owner).findNearestMethod(callInsn.name, callInsn.desc).ifPresent(methodEdge -> {
                        String id = methodEdge.owner().classNode.name + callInsn.name + callInsn.desc;
                        if (SALT_BY_METHOD.containsKey(id) && calleeId.get() == null) {
                            calleeId.set(id);
                        }
                    });

                    if (calleeId.get() == null) {
                        return;
                    }

                    // insert insn pushing salt onto stack before call
                    methodNode.instructions.insertBefore(
                            callInsn,
                            InsnUtil.getIntPushSalted(
                                    SALT_BY_METHOD.get(calleeId.get()),
                                    callerIsSalted,
                                    SALT_BY_METHOD.getOrDefault(callerId, 0),
                                    callerSaltSlot
                            )
                    );
                    // update call description to match salted descriptor
                    callInsn.desc = DescriptorParser.parseMethodDesc(callInsn.desc)
                            .addParam(SALT_PARAM)
                            .toDesc();

                    // mark callInsn as rewritten, to avoid adding another salt when emitted
                    rewrittenCallees.add(insn);
                }
                case InvokeDynamicInsnNode indy -> {
                    if (!hierarchy.containsKey(indy.bsm.getOwner())) {
                        return;
                    }

                    AtomicReference<String> calleeId = new AtomicReference<>(null);
                    hierarchy.get(indy.bsm.getOwner()).findNearestMethod(indy.bsm.getName(), indy.bsm.getDesc()).ifPresent(methodEdge -> {
                        String id = methodEdge.owner().classNode.name + indy.bsm.getName() + indy.bsm.getDesc();
                        if (SALT_BY_METHOD.containsKey(id) && calleeId.get() == null) {
                            calleeId.set(id);
                        }
                    });

                    if (calleeId.get() == null) {
                        return;
                    }

                    indy.bsm = new Handle(
                            indy.bsm.getTag(),
                            indy.bsm.getOwner(),
                            indy.bsm.getName(),
                            DescriptorParser.parseMethodDesc(indy.bsm.getDesc())
                                    .addParam(SALT_PARAM)
                                    .toDesc(),
                            indy.bsm.isInterface()
                    );
                    Object[] newBsmArgs = new Object[indy.bsmArgs.length + 1];
                    System.arraycopy(indy.bsmArgs, 0, newBsmArgs, 0, indy.bsmArgs.length);
                    newBsmArgs[newBsmArgs.length - 1] = SALT_BY_METHOD.get(calleeId.get());

                    rewrittenCallees.add(insn);
                }
                default -> {
                }
            }
        });
    }

    /**
     * Assigns a random salt value to every eligible method in the jar.
     *
     * @return a map of {@code methodId -> salt}
     */
    private Map<String, Integer> buildSaltMap(JarContext context) {
        Map<String, Integer> methodSaltMap = new HashMap<>();

        Set<ClassNode> excludedClass = new HashSet<>();
        Set<MethodNode> excludedMethods = new HashSet<>();
        buildExclusions(context.jar(), context.getExtension().getHierarchy(), excludedClass, excludedMethods);

        for (ClassNode classNode : context.jar().getClasses().values()) {
            if (ClassUtil.isEnum(classNode) || excludedClass.contains(classNode)) {
                continue;
            }

            for (MethodNode methodNode : classNode.methods) {
                if (!context.pipeline().getExtension(methodNode).saltInfo.hasSalt()
                        && !MethodUtil.isSpecial(methodNode)
                        && !excludedMethods.contains(methodNode)
                ) {
                    methodSaltMap.put(classNode.name + methodNode.name + methodNode.desc, RandomUtil.getInt(SALT_MIN, SALT_MAX));
                }
            }
        }

        return methodSaltMap;
    }

    private void buildExclusions(JarArchive jar, Map<ClassNode, ClassEdge> hierarchy, Set<ClassNode> excludedClasses, Set<MethodNode> excludedMethods) {
        Map<ClassNode, Set<MethodNode>> indyTargets = new HashMap<>();
        for (ClassNode classNode : jar.getClasses().values()) {
            // TODO: dont exclude all interfaces
            if (AsmUtil.hasAccess(classNode.access, ACC_INTERFACE) || AsmUtil.hasAccess(classNode.access, ACC_ENUM)) {
                excludedClasses.add(classNode);
            }

            for (String anInterface : classNode.interfaces) {
                if (jar.isDependency(anInterface)) {
                    jar.getClassNode(anInterface).ifPresent(iNode -> {
                        indyTargets.computeIfAbsent(iNode, k -> new HashSet<>()).addAll(iNode.methods);
                    });
                } else if (jar.getClassNode(anInterface).isEmpty()) {
                    excludedClasses.add(classNode);
                }
            }

            if (!classNode.superName.equals("java/lang/Object")) {
                if (jar.isDependency(classNode.superName)) {
                    jar.getClassNode(classNode.superName).ifPresent(superNode -> {
                        indyTargets.computeIfAbsent(superNode, k -> new HashSet<>()).addAll(superNode.methods);
                    });
                } else if (jar.getClassNode(classNode.superName).isEmpty()) {
                    excludedClasses.add(classNode);
                }
            }

            classNode.methods.forEach(methodNode -> {
                Map<ClassNode, Set<MethodNode>> targets = MethodUtil.getInvokeDynamicTargets(jar, methodNode);

                targets.forEach((node, methods) -> {
                    indyTargets.computeIfAbsent(node, k -> new HashSet<>()).addAll(methods);
                    excludedMethods.addAll(methods);
                });
            });
        }

        for (Map.Entry<ClassNode, Set<MethodNode>> entry : indyTargets.entrySet()) {
            ClassNode owner = entry.getKey();

            for (MethodNode excludedMethod : entry.getValue()) {
                hierarchy.get(owner).getMethod(excludedMethod).ifPresent(excludedEdge -> {
                    MethodEdge root = excludedEdge.getRoot();
                    for (MethodEdge override : root.getOverriders()) {
                        excludedMethods.add(override.methodNode());
                    }
                    excludedMethods.add(root.methodNode());
                });
            }
        }

        for (ClassNode excludedClass : excludedClasses) {
            for (MethodNode methodNode : excludedClass.methods) {
                hierarchy.get(excludedClass).getMethod(methodNode).ifPresent(excludedEdge -> {
                    MethodEdge root = excludedEdge.getRoot();
                    for (MethodEdge override : root.getOverriders()) {
                        excludedMethods.add(override.methodNode());
                    }
                    excludedMethods.add(root.methodNode());
                });
            }
        }

        // exclude entry points
        if (config.entryPoints.fromManifest()) {
            JarUtil.getMainClass(jar).ifPresent(mainClass -> {
                mainClass.methods.stream().filter(methodNode ->
                        methodNode.name.equals("main")
                                && methodNode.desc.equals("([Ljava/lang/String;)V")
                                && MethodUtil.hasAccess(methodNode, ACC_PUBLIC)
                                && MethodUtil.hasAccess(methodNode, ACC_STATIC)).forEach(excludedMethods::add);
            });
        }
        config.entryPoints.custom().forEach((className, methodName) -> {
            JarUtil.findClass(jar, className).flatMap(classNode -> ClassUtil.findMethod(classNode, methodName, "([Ljava/lang/String;)V")).ifPresent(excludedMethods::add);
        });
    }
}
