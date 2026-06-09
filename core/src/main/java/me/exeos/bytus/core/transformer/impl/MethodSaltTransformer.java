package me.exeos.bytus.core.transformer.impl;

import me.exeos.bytus.asmplus.analysis.hierarchy.edge.ClassEdge;
import me.exeos.bytus.asmplus.analysis.hierarchy.HierarchyAnalyzer;
import me.exeos.bytus.asmplus.analysis.hierarchy.edge.MethodEdge;
import me.exeos.bytus.asmplus.descriptor.DescriptorMember;
import me.exeos.bytus.asmplus.descriptor.DescriptorParser;
import me.exeos.bytus.asmplus.jar.JarArchive;
import me.exeos.bytus.asmplus.utils.*;
import me.exeos.bytus.core.config.BytusConfig;
import me.exeos.bytus.core.transformer.AbstractTransformer;
import me.exeos.bytus.core.transformer.Priority;
import me.exeos.bytus.core.transformer.context.ClassContext;
import me.exeos.bytus.core.transformer.context.JarContext;
import me.exeos.bytus.core.transformer.extensions.MethodExtension;
import me.exeos.bytus.core.utils.RandomUtil;
import org.objectweb.asm.tree.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class MethodSaltTransformer extends AbstractTransformer {

    private static final DescriptorMember SALT_PARAM = new DescriptorMember("I", true, false, 0);
    private static final int SALT_MIN = 0;
    private static final int SALT_MAX = 50000;
    private static Map<String, Integer> SALT_BY_METHOD = null;
    private static final Set<AbstractInsnNode> rewrittenCallees = new HashSet<>();

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
                processMethod(context, classNode, methodNode);
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
    public void transform(ClassContext context) {
        buildSaltMap(context.jarCtx().jar()).forEach(SALT_BY_METHOD::putIfAbsent);
        transform(context.jarCtx());
    }

    /**
     * Injects a salt parameter into {@code methodNode} (when eligible) and
     * rewrites every call site inside it to pass the appropriate salt argument.
     * Salt is then registered in the MethodExtension
     */
    private void processMethod(JarContext context, ClassNode classNode, MethodNode methodNode) {
        String methodId = classNode.name + methodNode.name + methodNode.desc;
        boolean isSalted = SALT_BY_METHOD.containsKey(methodId);
        int saltSlot = isSalted
                ? MethodUtil.getNewParamSlot(methodNode, SALT_PARAM)
                : 0;

        if (isSalted)
            MethodUtil.fixVars(methodNode.instructions, saltSlot);

        rewriteCallSites(HierarchyAnalyzer.analyzeNameMapped(context.jar()), methodNode, methodId, saltSlot, isSalted);

//        if (isSalted)
//            injectSaltParam(context, methodNode, SALT_BY_METHOD.get(methodId));
    }

    /**
     * Adds the salt parameter to {@code methodNode} and registers the salt
     * metadata in the pipeline extension for this method.
     *
     * @return the local variable slot allocated for the salt parameter
     */
    private int injectSaltParam(JarContext context, MethodNode methodNode, int salt) {
        AtomicInteger saltSlot = new AtomicInteger();

        context.pipeline()
                .getExtension(methodNode)
                .ifPresentOrElse(
                        ext -> {
                            if (!ext.saltInfo.hasSalt()) {
                                ext.saltInfo.setSalt(salt, MethodUtil.addParam(methodNode, SALT_PARAM, false));
                            }
                            saltSlot.set(ext.saltInfo.getSaltSlot());
                        },
                        () -> {
                            saltSlot.set(MethodUtil.addParam(methodNode, SALT_PARAM, false));
                            context.pipeline().assignExtension(methodNode, new MethodExtension(salt, saltSlot.get()));
                        }
                );

        return saltSlot.get();
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
            if (rewrittenCallees.contains(insn) || !(insn instanceof MethodInsnNode callInsn)) {
                return;
            }

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
        });
    }

    /**
     * Assigns a random salt value to every eligible method in the jar.
     *
     * @return a map of {@code methodId -> salt}
     */
    private Map<String, Integer> buildSaltMap(JarArchive jar) {
        Map<String, Integer> methodSaltMap = new HashMap<>();


        Set<ClassNode> excludedClass = new HashSet<>();
        Set<MethodNode> excludedMethods = new HashSet<>();
        buildExclusions(jar, excludedClass, excludedMethods);

        for (ClassNode classNode : jar.getClasses().values()) {
            if (ClassUtil.isEnum(classNode) || excludedClass.contains(classNode)) {
                continue;
            }

            for (MethodNode methodNode : classNode.methods) {
                if (!MethodUtil.isSpecial(methodNode) && !excludedMethods.contains(methodNode)) {
                    methodSaltMap.put(classNode.name + methodNode.name + methodNode.desc, RandomUtil.getInt(SALT_MIN, SALT_MAX));
                }
            }
        }

        return methodSaltMap;
    }

    private void buildExclusions(JarArchive jar, Set<ClassNode> excludedClasses, Set<MethodNode> excludedMethods) {
        Map<ClassNode, Set<MethodNode>> excludedMethodMap = new HashMap<>();
        for (ClassNode classNode : jar.getClasses().values()) {
            if (AsmUtil.hasAccess(classNode.access, ACC_ANNOTATION) || ClassUtil.isEnum(classNode)) {
                excludedClasses.add(classNode);
            }

            classNode.methods.forEach(methodNode -> {
                Map<ClassNode, Set<MethodNode>> targets = MethodUtil.getInvokeDynamicTargets(jar, methodNode);

                targets.forEach((node, methods) -> {
                    excludedMethodMap.computeIfAbsent(node, k -> new HashSet<>()).addAll(methods);
                    excludedMethods.addAll(methods);
                });
            });
        }

        Map<ClassNode, ClassEdge> hierarchy = HierarchyAnalyzer.analyze(jar);

        for (Map.Entry<ClassNode, Set<MethodNode>> entry : excludedMethodMap.entrySet()) {
            ClassNode cn = entry.getKey();
            for (MethodNode methodNode : entry.getValue()) {
                hierarchy.get(cn).getMethod(methodNode).ifPresent(methodEdge -> {
                    MethodEdge root = methodEdge.getRoot();
                    for (MethodEdge overrideEdge : root.getOverrides()) {
                        excludedMethods.add(overrideEdge.methodNode());
                    }
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
