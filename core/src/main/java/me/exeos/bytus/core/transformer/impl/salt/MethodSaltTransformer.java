package me.exeos.bytus.core.transformer.impl.salt;

import me.exeos.bytus.asmplus.analysis.hierarchy.edge.ClassEdge;
import me.exeos.bytus.asmplus.analysis.hierarchy.edge.MethodEdge;
import me.exeos.bytus.asmplus.descriptor.DescriptorMember;
import me.exeos.bytus.asmplus.descriptor.DescriptorParser;
import me.exeos.bytus.asmplus.descriptor.descriptors.method.MethodDescriptor;
import me.exeos.bytus.asmplus.jar.JarArchive;
import me.exeos.bytus.asmplus.matcher.method.MethodMatchEntry;
import me.exeos.bytus.asmplus.matcher.method.MethodMatcher;
import me.exeos.bytus.asmplus.utils.*;
import me.exeos.bytus.core.asm.ObfCodenGen;
import me.exeos.bytus.core.config.BytusConfig;
import me.exeos.bytus.core.transformer.AbstractTransformer;
import me.exeos.bytus.core.transformer.Pipeline;
import me.exeos.bytus.core.transformer.Priority;
import me.exeos.bytus.core.transformer.context.JarContext;
import me.exeos.bytus.core.transformer.context.MethodContext;
import me.exeos.bytus.core.transformer.extensions.MethodExtension;
import me.exeos.bytus.core.utils.RandomUtil;
import org.objectweb.asm.Handle;
import org.objectweb.asm.tree.*;

import java.util.*;

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
        return Priority.SALT_METHOD;
    }

    @Override
    public void transform(JarContext context) {
        if (SALT_BY_METHOD == null) {
            SALT_BY_METHOD = buildSaltMap(context);
        }

        MethodMatcher exclusions = buildExclusions(context);
        Map<String, ClassEdge> hierarchy = context.getExtension().getHierarchyNameMapped();
        for (ClassNode classNode : context.jar().getClasses().values()) {
            for (MethodNode methodNode : classNode.methods) {
                processMethod(context, hierarchy, exclusions, classNode, methodNode);
            }
        }

        for (ClassNode classNode : context.jar().getClasses().values()) {
            for (MethodNode methodNode : classNode.methods) {
                if (classNode.name.contains("SimpleTestRepositoryFactory") && methodNode.name.equals("build")) {
                    System.out.println();
                }
                String methodId = classNode.name + methodNode.name + methodNode.desc;
                boolean isSalted = SALT_BY_METHOD.containsKey(methodId);

                if (isSalted) {
                    injectSaltParam(context, classNode, methodNode, SALT_BY_METHOD.get(methodId));
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
    private void processMethod(JarContext context, Map<String, ClassEdge> hierarchy, MethodMatcher exclusions, ClassNode classNode, MethodNode methodNode) {
        String methodId = classNode.name + methodNode.name + methodNode.desc;
        boolean isSalted = SALT_BY_METHOD.containsKey(methodId);
        int saltSlot = isSalted
                ? MethodUtil.getNewParamSlot(methodNode, SALT_PARAM)
                : 0;

        if (isSalted)
            MethodUtil.remapLocals(methodNode.instructions, saltSlot);

        rewriteCallSites(context.pipeline(), context.jar(), classNode, hierarchy, exclusions, methodNode, methodId, saltSlot, isSalted);
    }

    /**
     * Registers the salt metadata in the pipeline extension for this method.
     */
    private void injectSaltParam(JarContext context, ClassNode classNode, MethodNode methodNode, int salt) {
        MethodExtension extension = context.pipeline().getExtension(classNode, methodNode);
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
            Pipeline pipeline,
            JarArchive jar,
            ClassNode classNode,
            Map<String, ClassEdge> hierarchy,
            MethodMatcher exclusions,
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
                    if (exclusions.match(MethodMatchEntry.of(callInsn)) || !hierarchy.containsKey(callInsn.owner)) {
                        return;
                    }

                    Optional<MethodEdge> nearestMethod = hierarchy.get(callInsn.owner).findNearestMethod(callInsn.name, callInsn.desc);
                    if (nearestMethod.isEmpty()) {
                        return;
                    }

                    String ownerName = nearestMethod.get().owner().classNode.name;
                    String calleeId = ownerName + callInsn.name + callInsn.desc;

                    if (!SALT_BY_METHOD.containsKey(calleeId)) {
                        return;
                    }

                    Optional<ClassNode> calleeOwner = jar.getClassNode(ownerName, false);
                    Optional<MethodNode> calleeMethod = Optional.empty();
                    if (calleeOwner.isPresent()) {
                        calleeMethod = ClassUtil.findMethod(calleeOwner.get(), callInsn.name, callInsn.desc);
                    }

                    MethodExtension.MethodSaltInfo msi = new MethodExtension.MethodSaltInfo();
                    if (callerIsSalted) {
                        msi = new MethodExtension.MethodSaltInfo(SALT_BY_METHOD.get(callerId), callerSaltSlot);
                    }

                    // insert insn pushing salt onto stack before call
                    methodNode.instructions.insertBefore(
                            callInsn,
                            ObfCodenGen.getObfuscatedIntPush(
                                    SALT_BY_METHOD.get(calleeId),
                                    methodNode.name.equals("<clinit>"),
                                    pipeline.getExtension(classNode).saltInfo(),
                                    msi,
                                    calleeMethod.isPresent()
                                            ? pipeline.getExtension(calleeOwner.get(), calleeMethod.get()).paramObfInfo
                                            : new MethodExtension.ParamObfInfo()
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
                    if (indy.bsmArgs.length < 3 || !(indy.bsmArgs[1] instanceof Handle handle)) return;

                    String owner = handle.getOwner();
                    String name = handle.getName();
                    String desc = handle.getDesc();

                    if (!jar.getClasses().containsKey(owner) || exclusions.match(MethodMatchEntry.of(owner, name, desc)))
                        return;

                    String calleeId = owner + name + desc;
                    if (!SALT_BY_METHOD.containsKey(calleeId)) return;

                    methodNode.instructions.insertBefore(indy, InsnUtil.getIntPush(SALT_BY_METHOD.get(calleeId)));

                    // captured salt at factory
                    indy.desc = DescriptorParser.parseMethodDesc(indy.desc).addParam(SALT_PARAM).toDesc();

                    // impl handle salt with tag-aware parameter placement
                    indy.bsmArgs[1] = new Handle(
                            handle.getTag(),
                            owner,
                            name,
                            addSaltToImplHandleDesc(handle),
                            handle.isInterface()
                    );

                    rewrittenCallees.add(insn);
                }
                default -> {
                }
            }
        });
    }

    private String addSaltToImplHandleDesc(Handle handle) {
        MethodDescriptor md = DescriptorParser.parseMethodDesc(handle.getDesc());

        return switch (handle.getTag()) {
            case H_INVOKEVIRTUAL, H_INVOKEINTERFACE, H_INVOKESPECIAL -> md.insertParam(0, SALT_PARAM).toDesc();
            default -> md.addParam(SALT_PARAM).toDesc();
        };
    }

    /**
     * Assigns a random salt value to every eligible method in the jar.
     *
     * @return a map of {@code methodId -> salt}
     */
    private Map<String, Integer> buildSaltMap(JarContext context) {
        Map<String, Integer> methodSaltMap = new HashMap<>();

        MethodMatcher exclusions = buildExclusions(context);

        for (ClassNode classNode : context.jar().getClasses().values()) {
            for (MethodNode methodNode : classNode.methods) {
                if (!context.pipeline().getExtension(classNode, methodNode).saltInfo.hasSalt()
                        && !exclusions.match(MethodMatchEntry.of(classNode.name, methodNode))
                ) {
                    methodSaltMap.put(classNode.name + methodNode.name + methodNode.desc, RandomUtil.getInt(SALT_MIN, SALT_MAX));
                }
            }
        }

        return methodSaltMap;
    }

    private MethodMatcher buildExclusions(JarContext context) {
        MethodMatcher exclusions = new MethodMatcher(config.getEntryPoints(context.jar()));
        exclusions.add(MethodMatchEntry.of("<clinit>"));

        for (ClassNode classNode : context.jar().getClasses().values()) {
            if (AsmUtil.hasAccess(classNode.access, ACC_ANNOTATION) || ClassUtil.isEnum(classNode)) {
                for (MethodNode methodNode : classNode.methods) {
                    exclusions.add(MethodMatchEntry.of(classNode.name, methodNode));
                }
            }

            ClassEdge classEdge = context.getExtension().getHierarchy().get(classNode);
            if (classEdge == null || classEdge.hasUnresolved()) {
                classNode.methods.forEach(methodNode -> exclusions.add(MethodMatchEntry.of(classNode.name, methodNode)));
                continue;
            }

            // exclude all methods declared outside of jar
            for (MethodEdge method : classEdge.getMethods()) {
                if (context.jar().isDependency(method.getRoot().getOwnerName())) {
                    exclusions.add(MethodMatchEntry.of(classNode.name, method.methodNode()));
                }
            }
        }

        MethodUtil.excludeUnrewritableIndyTargets(context.jar(), exclusions);
        HierarchyUtil.hierarchyExpandMethodMatcher(exclusions, context.getExtension().getHierarchyNameMapped());
        exclusions.add(excludeCollidingSignatures(context, exclusions));

        return exclusions;
    }

    private MethodMatcher excludeCollidingSignatures(JarContext context, MethodMatcher excluded) {
        MethodMatcher exclusions = new MethodMatcher();

        for (ClassNode classNode : context.jar().getClasses().values()) {
            ClassEdge classEdge = context.getExtension().getHierarchy().get(classNode);
            if (classEdge == null) {
                continue;
            }

            for (MethodEdge baseMethod : classEdge.getMethods()) {
                // baseMethod is already excluded, no need to re-exclude it
                if (excluded.match(MethodMatchEntry.of(classNode.name, baseMethod.methodNode()))) {
                    continue;
                }

                // baseMethod descriptor after salting param has been added
                String saltedDesc = DescriptorParser.parseMethodDesc(baseMethod.getDesc())
                        .addParam(SALT_PARAM)
                        .toDesc();

                // loop through all methods with the same name as baseMethod in the hierarchy
                for (MethodEdge edge : classEdge.findMethods(baseMethod.getName())) {
                    if (baseMethod.equals(edge) || !AsmUtil.bothHaveOrLackAccess(baseMethod.getAccess(), edge.getAccess(), ACC_STATIC)) {
                        continue;
                    }

                    String edgeDesc;
                    if (excluded.match(MethodMatchEntry.of(edge.getOwnerName(), edge.methodNode()))) {
                        edgeDesc = DescriptorParser.parseMethodDesc(edge.getDesc()).toDesc();
                    } else {
                        edgeDesc = DescriptorParser.parseMethodDesc(edge.getDesc())
                                .addParam(SALT_PARAM)
                                .toDesc();
                    }

                    if (saltedDesc.equals(edgeDesc)) {
                        exclusions.add(MethodMatchEntry.of(classNode.name, baseMethod.methodNode()));
                    }
                }
            }
        }

        HierarchyUtil.hierarchyExpandMethodMatcher(exclusions, context.getExtension().getHierarchyNameMapped());
        return exclusions;
    }
}
