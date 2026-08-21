package me.exeos.bytus.core.transformer.impl.flow.data;

import me.exeos.asmplus.analysis.hierarchy.edge.ClassEdge;
import me.exeos.asmplus.analysis.hierarchy.edge.MethodEdge;
import me.exeos.asmplus.codegen.value.impl.ConstantPusher;
import me.exeos.asmplus.descriptor.DescriptorMember;
import me.exeos.asmplus.descriptor.DescriptorParser;
import me.exeos.asmplus.descriptor.descriptors.method.MethodDescriptor;
import me.exeos.asmplus.jar.JarArchive;
import me.exeos.asmplus.matcher.method.MethodMatchEntry;
import me.exeos.asmplus.matcher.method.MethodMatcher;
import me.exeos.asmplus.remapper.mapper.MemberKey;
import me.exeos.asmplus.utils.*;
import me.exeos.bytus.core.config.BytusConfig;
import me.exeos.bytus.core.transformer.AbstractTransformer;
import me.exeos.bytus.core.transformer.Priority;
import me.exeos.bytus.core.transformer.context.JarContext;
import me.exeos.bytus.core.transformer.extensions.MethodExtension;
import me.exeos.bytus.core.utils.RandomUtil;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

// todo handle <init> it should work in theory I think hierarchy is fucked for it
public class ParamGenerify extends AbstractTransformer {

    private final static String OBJ_ARR_DESC = "([Ljava/lang/Object;)";

    public ParamGenerify(BytusConfig config) {
        super(config);
    }

    @Override
    public boolean applies() {
        return config.flow.dataFlow();
    }

    @Override
    public int priority() {
        return Priority.FLOW_PARAM_GENERIFY;
    }

    @Override
    public void transform(JarContext context) {
        Set<MemberKey> rewritableIdx = collectRewritable(context);
        Map<MemberKey, String> antiCollideMap = mapAntiCollideParams(context, rewritableIdx);
        rewriteCallsites(context, rewritableIdx, antiCollideMap);
        rewriteLocalUsage(context, rewritableIdx);
        rewriteDescriptors(context.jar(), rewritableIdx, antiCollideMap);
    }

    /**
     * Analyze the jar and collect methods that be generified.
     *
     * @return A set of idx composed of className + methodName + methoDesc
     */
    private Set<MemberKey> collectRewritable(JarContext context) {
        Set<MemberKey> rewritableIdx = new HashSet<>();
        MethodMatcher excludedMethods = excludedIndyTargets(context.jar());

        for (ClassNode classNode : context.jar().getClasses().values()) {
            ClassEdge classEdge = context.getExtension().getHierarchy().get(classNode);
            if (classEdge == null || classEdge.hasUnresolved()) {
                continue;
            }

            for (MethodEdge methodEdge : classEdge.methods) {
                if (!isValidGroup(context.jar(), methodEdge, excludedMethods)) {
                    continue;
                }

                for (MethodEdge oge : methodEdge.getOverrideGroup()) {
                    rewritableIdx.add(new MemberKey(classNode.name, oge.getName(), oge.getDesc()));
                }
            }
        }

        return rewritableIdx;
    }

    /**
     * Creates a map mapping each method idx that can be generified to a descriptor string that should be appended to that method to avoid collisions
     *
     * @param rewritableIdx Set of method idx that can be generified. See {@link #collectRewritable(JarContext) collectRewritable}
     * @return a map mapping each method idx that can be generified to a descriptor string that should be appended to that method to avoid collisions
     */
    private Map<MemberKey, String> mapAntiCollideParams(JarContext context, Set<MemberKey> rewritableIdx) {
        var hierarchy = context.getExtension().getHierarchy();
        Map<MemberKey, String> idxAntiCollideParamMap = new HashMap<>();
        Map<MemberKey, Set<String>> usedAntiCollideMap = new HashMap<>();

        for (ClassNode classNode : context.jar().getClasses().values()) {
            ClassEdge classEdge = hierarchy.get(classNode);
            if (classEdge == null) {
                continue;
            }

            for (MethodEdge methodEdge : classEdge.methods) {
                if (!rewritableIdx.contains(new MemberKey(classNode.name, methodEdge.getName(), methodEdge.getDesc()))) {
                    continue;
                }

                String antiCollideAddition = "";
                while (groupContainsCollides(methodEdge.getOverrideGroup(), methodEdge.getDesc(), antiCollideAddition, usedAntiCollideMap)) {
                    antiCollideAddition += "I";
                }

                for (MethodEdge oge : methodEdge.getOverrideGroup()) {
                    idxAntiCollideParamMap.put(new MemberKey(oge.getOwnerName(), oge.getName(), oge.getDesc()), antiCollideAddition);
                    usedAntiCollideMap.computeIfAbsent(new MemberKey(oge.getOwnerName(), oge.getName(), ""), _ -> new HashSet<>()).add(antiCollideAddition);
                }
            }
        }

        return idxAntiCollideParamMap;
    }

    /**
     * Check weather each method int the provided overrideGroup collides after generifying and adding the anti collision arguments to the generified desc
     * Also the used anti collision map for each method idx so we don't create collisions ourselves
     *
     * @param overrideGroup The overrideGroup to check for collisions
     * @param desc          Parsed method desc of the desc for provided overrideGroup
     * @param acAddition    Descriptor to append to the generified descriptor params
     * @param usedACMap     A map mapping method idx (owner and name) to the anti collision desc that's already been assigned
     * @return true if any of the methods have collisions using the provided params, false if no collision is found
     */
    private boolean groupContainsCollides(Set<MethodEdge> overrideGroup, String desc, String acAddition, Map<MemberKey, Set<String>> usedACMap) {
        for (MethodEdge methodEdge : overrideGroup) {
            Set<String> usedAntiCollides = usedACMap.get(new MemberKey(methodEdge.getOwnerName(), methodEdge.getName(), ""));
            if (usedAntiCollides != null && usedAntiCollides.contains(acAddition)) {
                return true;
            }

            // build pseudo after transformation method desc (eg. Object[], int(ac), ..)
            String pseudo = "([Ljava/lang/Object;" + acAddition + desc.substring(desc.indexOf(")"));

            // check if override group contains pseudo after desc
            Set<MethodEdge> same = methodEdge.owner().findAllMethods(methodEdge.getName(), pseudo);
            return same.size() > 1;
        }

        return false;
    }

    /**
     * Rewrites callsites, so that the pack the params into Object[] and add anti collide params if required. (Only rewrites invokes that match idx with rewritableIdx)
     *
     * @param rewritableIdx  Set of method idx that will be generified
     * @param antiCollideMap Maps method idx to anti collide param desc. See {@link #mapAntiCollideParams(JarContext, Set) mapAntiCollideParams}
     */
    private void rewriteCallsites(JarContext context, Set<MemberKey> rewritableIdx, Map<MemberKey, String> antiCollideMap) {
        for (ClassNode classNode : context.jar().getClasses().values()) {
            for (MethodNode methodNode : classNode.methods) {
                MethodExtension extension = context.pipeline().getExtension(classNode, methodNode);
                int arrLocal = methodNode.maxLocals++;
                int tempStore = methodNode.maxLocals++;

                InsnUtil.loop(methodNode.instructions, insnNode -> {
                    if (insnNode instanceof MethodInsnNode methodInsnNode) {
                        String invokeDefRoot = HierarchyUtil.findRoot(context.getExtension().getHierarchyNameMapped(), methodInsnNode.owner, methodInsnNode.name, methodInsnNode.desc);
                        MemberKey invokeMemberKey = new MemberKey(invokeDefRoot, methodInsnNode.name, methodInsnNode.desc);
                        if (!rewritableIdx.contains(invokeMemberKey)) {
                            return;
                        }

                        MethodDescriptor invokeDesc = DescriptorParser.parseMethodDesc(methodInsnNode.desc);
                        String invokeAntiCollideDesc = antiCollideMap.get(invokeMemberKey);
                        InsnList packInsn = new InsnList();

                        packInsn.add(extension.getObfuscatedIntPush(invokeDesc.getParams().size()));
                        packInsn.add(new TypeInsnNode(ANEWARRAY, "java/lang/Object"));
                        packInsn.add(new VarInsnNode(ASTORE, arrLocal));
                        for (int i = invokeDesc.getParams().size() - 1; i >= 0; i--) {
                            packInsn.add(storeToObjectArray(extension, invokeDesc, i, arrLocal, tempStore));
                        }
                        packInsn.add(new VarInsnNode(ALOAD, arrLocal));

                        // insert insn to pack params on stack into Object[]
                        methodNode.instructions.insertBefore(methodInsnNode, packInsn);
                        // generify invokes desc
                        methodInsnNode.desc = generifyDesc(methodInsnNode.desc);

                        // add anti collide params to invoke desc and push random values for them
                        if (invokeAntiCollideDesc != null) {
                            for (int i = 0; i < invokeAntiCollideDesc.length(); i++) {
                                methodNode.instructions.insertBefore(insnNode, ConstantPusher.getIntPush(RandomUtil.getInt()));
                            }
                            methodInsnNode.desc = methodInsnNode.desc.replace(")", invokeAntiCollideDesc + ")");
                        }
                    }
                });
            }
        }
    }

    /**
     * Replaces instructions using locals ({@link VarInsnNode} and {@link IincInsnNode}}) with generified Object[] param access and unbox / box
     *
     * @param rewritableIdx Set of Method idx that will be generified
     */
    private void rewriteLocalUsage(JarContext context, Set<MemberKey> rewritableIdx) {
        for (ClassNode classNode : context.jar().getClasses().values()) {
            for (MethodNode methodNode : classNode.methods) {
                if (!rewritableIdx.contains(new MemberKey(classNode.name, methodNode.name, methodNode.desc))) {
                    continue;
                }

                MethodExtension extension = context.pipeline().getExtension(classNode, methodNode);
                MethodDescriptor methodDesc = DescriptorParser.parseMethodDesc(methodNode.desc);
                int localStart = MethodUtil.getLocalsOffset(methodNode);
                Map<Integer, Integer> arrayIndexByLocal = methodDesc.mapLocalToParamIndex(localStart);
                int tempLocal = methodNode.maxLocals;
                AtomicBoolean tempLocalUsed = new AtomicBoolean(false);

                extension.paramObfInfo.setParamObf(localStart, arrayIndexByLocal);
                InsnUtil.loop(methodNode.instructions, insnNode -> {
                    InsnList replacement = new InsnList();

                    if (insnNode instanceof VarInsnNode varInsnNode) {
                        Integer localArrayIndex = arrayIndexByLocal.get(varInsnNode.var);
                        if (localArrayIndex == null) {
                            return;
                        }

                        if (InsnUtil.isLoad(insnNode)) {
                            replacement.add(loadFromObjectArray(extension, methodDesc, localArrayIndex, localStart));
                        } else {
                            replacement.add(storeToObjectArray(extension, methodDesc, localArrayIndex, localStart, tempLocal));
                            tempLocalUsed.set(true);
                        }
                    } else if (insnNode instanceof IincInsnNode iincInsnNode) {
                        Integer localArrayIndex = arrayIndexByLocal.get(iincInsnNode.var);
                        if (localArrayIndex == null) {
                            return;
                        }

                        replacement.add(loadFromObjectArray(extension, methodDesc, localArrayIndex, localStart));
                        replacement.add(extension.getObfuscatedIntPush(iincInsnNode.incr));
                        replacement.add(new InsnNode(IADD));
                        replacement.add(storeToObjectArray(extension, methodDesc, localArrayIndex, localStart, tempLocal));
                        tempLocalUsed.set(true);
                    } else {
                        // don't replace instruction
                        return;
                    }

                    methodNode.instructions.insertBefore(insnNode, replacement);
                    methodNode.instructions.remove(insnNode);
                });

                if (tempLocalUsed.get()) {
                    methodNode.maxLocals++;
                }
            }
        }
    }

    /**
     * Update every method descriptor that should be generified to generified descriptor + anti collide params
     *
     * @param rewritableIdx  Set of Method idx that will be generified
     * @param antiCollideMap Maps method idx to anti collide param desc. See {@link #mapAntiCollideParams(JarContext, Set) mapAntiCollideParams}
     */
    private void rewriteDescriptors(JarArchive jar, Set<MemberKey> rewritableIdx, Map<MemberKey, String> antiCollideMap) {
        for (ClassNode classNode : jar.getClasses().values()) {
            for (MethodNode methodNode : classNode.methods) {
                MemberKey memberKey = new MemberKey(classNode.name, methodNode.name, methodNode.desc);
                if (!rewritableIdx.contains(memberKey)) {
                    continue;
                }

                // generify method desc
                methodNode.desc = generifyDesc(methodNode.desc);

                // add anti collision params
                String antiCollideDesc = antiCollideMap.get(memberKey);
                if (antiCollideDesc != null) {
                    for (char c : antiCollideDesc.toCharArray()) {
                        // thank god I made this util <3
                        MethodUtil.addParam(methodNode, new DescriptorMember(String.valueOf(c), true, false, 0));
                    }
                }
            }
        }
    }

    private InsnList loadFromObjectArray(MethodExtension extension, MethodDescriptor methodDescriptor, int paramIndex, int arrayLocal) {
        InsnList loadInsn = new InsnList();
        loadInsn.add(new VarInsnNode(ALOAD, arrayLocal));
        loadInsn.add(extension.getObfuscatedIntPush(paramIndex));
        loadInsn.add(new InsnNode(AALOAD));
        loadInsn.add(methodDescriptor.getParams().get(paramIndex).unbox());
        return loadInsn;
    }

    private InsnList storeToObjectArray(MethodExtension extension, MethodDescriptor methodDescriptor, int paramIndex, int arrayLocal, int tempLocal) {
        InsnList storeInsn = new InsnList();
        storeInsn.add(methodDescriptor.getParams().get(paramIndex).box());
        storeInsn.add(new VarInsnNode(ASTORE, tempLocal));
        storeInsn.add(new VarInsnNode(ALOAD, arrayLocal));
        storeInsn.add(extension.getObfuscatedIntPush(paramIndex));
        storeInsn.add(new VarInsnNode(ALOAD, tempLocal));
        storeInsn.add(new InsnNode(AASTORE));
        return storeInsn;
    }

    private MethodMatcher excludedIndyTargets(JarArchive jar) {
        MethodMatcher matcher = new MethodMatcher();

        for (ClassNode classNode : jar.getClasses().values()) {
            for (MethodNode methodNode : classNode.methods) {
                for (AbstractInsnNode insnNode : methodNode.instructions) {
                    if (!(insnNode instanceof InvokeDynamicInsnNode indy) || !(indy.bsmArgs[0] instanceof Type normalType) || !(indy.bsmArgs[1] instanceof Handle handle)) {
                        continue;
                    }
                    DescriptorMember indyRet = DescriptorParser.parseMethodDesc(indy.desc).getReturnType();
                    matcher.add(MethodMatchEntry.of(indyRet.getValue(), indy.name, normalType.getDescriptor()));
                    matcher.add(MethodMatchEntry.of(handle.getOwner(), handle.getName(), handle.getDesc()));
                }
            }
        }

        return matcher;
    }

    private boolean isValidGroup(JarArchive jar, MethodEdge root, MethodMatcher excludedMethods) {
        for (MethodEdge methodEdge : root.getOverrideGroup()) {
            if (methodEdge.owner().hasUnresolved()
                    || config.isEntryPoint(jar, methodEdge.getOwnerName(), methodEdge.methodNode())
                    || excludedMethods.match(MethodMatchEntry.of(methodEdge))
                    || AsmUtil.hasAccess(methodEdge.owner().classNode.access, ACC_ANNOTATION)
                    || MethodUtil.isSpecial(methodEdge.methodNode())
                    || (ClassUtil.isEnum(methodEdge.owner().classNode) && List.of("values", "valueOf").contains(methodEdge.getName()))
            ) {
                return false;
            }
        }

        return true;
    }

    private String generifyDesc(String desc) {
        String returnDesc = DescriptorParser.parseMethodDesc(desc).getReturnType().toDesc();
        return OBJ_ARR_DESC + returnDesc;
    }
}
