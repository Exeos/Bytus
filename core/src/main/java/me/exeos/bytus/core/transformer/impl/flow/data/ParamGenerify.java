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
        Set<String> rewritableIdx = collectRewritable(context);
        Map<String, String> antiCollideAdditions = avoidCollisions(context, rewritableIdx);
        rewriteCallsites(context, rewritableIdx, antiCollideAdditions);
        rewriteLocalUsage(context, rewritableIdx);
        rewriteDescriptors(context.jar(), rewritableIdx, antiCollideAdditions);
    }

    private Map<String, String> avoidCollisions(JarContext context, Set<String> rewritableIdx) {
        var hierarchy = context.getExtension().getHierarchy();
        Map<String, String> idxAntiCollideDescMap = new HashMap<>();
        Map<String, Set<String>> additionalDescMap = new HashMap<>();

        for (ClassNode classNode : context.jar().getClasses().values()) {
            ClassEdge classEdge = hierarchy.get(classNode);
            if (classEdge == null) {
                continue;
            }

            for (MethodEdge methodEdge : classEdge.methods) {
                if (!rewritableIdx.contains(classNode.name + methodEdge.getName() + methodEdge.getDesc())) {
                    continue;
                }

                MethodDescriptor md = DescriptorParser.parseMethodDesc(methodEdge.getDesc());
                String additional = "";
                while (groupContainsCollides(methodEdge.getOverrideGroup(), md, additional, additionalDescMap)) {
                    additional += "I";
                }

                for (MethodEdge oge : methodEdge.getOverrideGroup()) {
                    idxAntiCollideDescMap.put(oge.getOwnerName() + oge.getName() + oge.getDesc(), additional);
                    additionalDescMap.computeIfAbsent(oge.getOwnerName() + oge.getName(), _ -> new HashSet<>()).add(additional);
                }
            }
        }

        return idxAntiCollideDescMap;
    }

    private boolean groupContainsCollides(Set<MethodEdge> group, MethodDescriptor md, String additional, Map<String, Set<String>> usedMap) {
        for (MethodEdge methodEdge : group) {
            Set<String> used = usedMap.get(methodEdge.getOwnerName() + methodEdge.getName());
            if (used != null && used.contains(additional)) {
                return true;
            }

            MethodDescriptor mdWithAdditional = new MethodDescriptor(new ArrayList<>(List.of(new DescriptorMember("java/lang/Object", false, true, 1))), md.getReturnType());
            for (char c : additional.toCharArray()) {
                mdWithAdditional.addParam(new DescriptorMember(String.valueOf(c), true, false, 0));
            }
            Set<MethodEdge> same = methodEdge.owner().findAllMethods(methodEdge.getName(), mdWithAdditional.toDesc());
            return same.size() > 1;
        }

        return false;
    }


    private Set<String> collectRewritable(JarContext context) {
        Set<String> rewritableIdx = new HashSet<>();
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
                    rewritableIdx.add(classNode.name + oge.getName() + oge.getDesc());
                }
            }
        }

        return rewritableIdx;
    }

    private void rewriteDescriptors(JarArchive jar, Set<String> rewritableIdx, Map<String, String> idxAntiCollideDescMap) {
        for (ClassNode classNode : jar.getClasses().values()) {
            for (MethodNode methodNode : classNode.methods) {
                if (rewritableIdx.contains(classNode.name + methodNode.name + methodNode.desc)) {
                    String added = idxAntiCollideDescMap.get(classNode.name + methodNode.name + methodNode.desc);
                    methodNode.desc = generifyDesc(methodNode.desc);
                    if (added != null) {
                        for (char c : added.toCharArray()) {
                            MethodUtil.addParam(methodNode, new DescriptorMember(String.valueOf(c), true, false, 0));
                        }
                    }
                }
            }
        }
    }

    private void rewriteCallsites(JarContext context, Set<String> rewritableIdx, Map<String, String> idxAntiCollideDescMap) {
        for (ClassNode classNode : context.jar().getClasses().values()) {
            for (MethodNode methodNode : classNode.methods) {
                int arrLocal = methodNode.maxLocals++;
                int tempStore = methodNode.maxLocals++;
                InsnUtil.loop(methodNode.instructions, insnNode -> {
                    if (insnNode instanceof MethodInsnNode methodInsnNode) {
                        String root = HierarchyUtil.findRoot(context.getExtension().getHierarchyNameMapped(), methodInsnNode.owner, methodInsnNode.name, methodInsnNode.desc);
                        if (!rewritableIdx.contains(root + methodInsnNode.name + methodInsnNode.desc)) {
                            return;
                        }

                        MethodExtension extension = context.pipeline().getExtension(classNode, methodNode);
                        MethodDescriptor methodDescriptor = DescriptorParser.parseMethodDesc(methodInsnNode.desc);
                        InsnList packInsn = new InsnList();

                        packInsn.add(extension.getObfuscatedIntPush(methodDescriptor.getParams().size()));
                        packInsn.add(new TypeInsnNode(ANEWARRAY, "java/lang/Object"));
                        packInsn.add(new VarInsnNode(ASTORE, arrLocal));

                        for (int i = methodDescriptor.getParams().size() - 1; i >= 0; i--) {
                            packInsn.add(storeToObjectArray(extension, methodDescriptor, i, arrLocal, tempStore));
                        }

                        packInsn.add(new VarInsnNode(ALOAD, arrLocal));
                        methodNode.instructions.insertBefore(methodInsnNode, packInsn);
                        String addedForInsn = idxAntiCollideDescMap.get(root + methodInsnNode.name + methodInsnNode.desc);
                        methodInsnNode.desc = generifyDesc(methodInsnNode.desc);
                        if (addedForInsn != null) {
                            for (int i = 0; i < addedForInsn.length(); i++) {
                                methodNode.instructions.insertBefore(insnNode, ConstantPusher.getIntPush(RandomUtil.getInt()));
                            }
                            methodInsnNode.desc = methodInsnNode.desc.replace(")", addedForInsn + ")");
                        }
                    }
                });
            }
        }
    }

    private void rewriteLocalUsage(JarContext context, Set<String> rewritableIdx) {
        for (ClassNode classNode : context.jar().getClasses().values()) {
            for (MethodNode methodNode : classNode.methods) {
                String idx = classNode.name + methodNode.name + methodNode.desc;
                if (!rewritableIdx.contains(idx)) {
                    continue;
                }

                MethodExtension extension = context.pipeline().getExtension(classNode, methodNode);
                MethodDescriptor originalDesc = DescriptorParser.parseMethodDesc(idx);
                int localStart = AsmUtil.hasAccess(methodNode.access, ACC_STATIC) ? 0 : 1;
                AtomicBoolean tempLocalUsed = new AtomicBoolean(false);
                int tempLocal = methodNode.maxLocals;

                Map<Integer, Integer> localArrayIndexMap = new HashMap<>();
                int local = localStart;
                for (int i = 0; i < originalDesc.getParams().size(); i++) {
                    localArrayIndexMap.put(local, i);
                    local += originalDesc.getParams().get(i).getSlotWidth();
                }

                extension.paramObfInfo.setParamObf(localStart, localArrayIndexMap);
                InsnUtil.loop(methodNode.instructions, insnNode -> {
                    InsnList replacement = new InsnList();

                    if (insnNode instanceof VarInsnNode varInsnNode) {
                        Integer localArrayIndex = localArrayIndexMap.get(varInsnNode.var);
                        if (localArrayIndex == null) {
                            return;
                        }

                        if (InsnUtil.isLoad(insnNode)) {
                            replacement.add(loadFromObjectArray(extension, originalDesc, localArrayIndex, localStart));
                        } else {
                            replacement.add(storeToObjectArray(extension, originalDesc, localArrayIndex, localStart, tempLocal));
                            tempLocalUsed.set(true);
                        }
                    } else if (insnNode instanceof IincInsnNode iincInsnNode) {
                        Integer localArrayIndex = localArrayIndexMap.get(iincInsnNode.var);
                        if (localArrayIndex == null) {
                            return;
                        }

                        replacement.add(loadFromObjectArray(extension, originalDesc, localArrayIndex, localStart));
                        replacement.add(extension.getObfuscatedIntPush(iincInsnNode.incr));
                        replacement.add(new InsnNode(IADD));
                        replacement.add(storeToObjectArray(extension, originalDesc, localArrayIndex, localStart, tempLocal));
                        tempLocalUsed.set(true);
                    } else {
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

    private InsnList loadFromObjectArray(MethodExtension extension, MethodDescriptor methodDescriptor, int paramIndex, int arrayLocal) {
        InsnList loadInsn = new InsnList();
        loadInsn.add(new VarInsnNode(ALOAD, arrayLocal));
        loadInsn.add(extension.getObfuscatedIntPush(paramIndex));
        loadInsn.add(new InsnNode(AALOAD));
        loadInsn.add(unbox(methodDescriptor, paramIndex));
        return loadInsn;
    }

    private InsnList storeToObjectArray(MethodExtension extension, MethodDescriptor methodDescriptor, int paramIndex, int arrayLocal, int tempLocal) {
        InsnList storeInsn = new InsnList();
        storeInsn.add(box(methodDescriptor, paramIndex));
        storeInsn.add(new VarInsnNode(ASTORE, tempLocal));
        storeInsn.add(new VarInsnNode(ALOAD, arrayLocal));
        storeInsn.add(extension.getObfuscatedIntPush(paramIndex));
        storeInsn.add(new VarInsnNode(ALOAD, tempLocal));
        storeInsn.add(new InsnNode(AASTORE));
        return storeInsn;
    }

    private InsnList unbox(MethodDescriptor methodDescriptor, int index) {
        InsnList unboxInsn = new InsnList();
        DescriptorMember param = methodDescriptor.getParams().get(index);

        unboxInsn.add(new TypeInsnNode(CHECKCAST, (param.isArray() ? param : param.toNonePrimitive()).toType()));
        if (param.isPrimitive() && !param.isArray()) {
            char primitive = param.getValue().charAt(0);
            unboxInsn.add(new MethodInsnNode(
                    INVOKEVIRTUAL,
                    TypeUtil.primitiveToClass(primitive),
                    TypeUtil.clsInstanceToPrimMethodName(primitive),
                    "()" + primitive
            ));
        }

        return unboxInsn;
    }

    private InsnList box(MethodDescriptor methodDescriptor, int paramIndex) {
        DescriptorMember param = methodDescriptor.getParams().get(paramIndex);
        InsnList boxInsn = new InsnList();
        if (param.isPrimitive() && !param.isArray()) {
            String primClassName = TypeUtil.primitiveToClass(param.getValue().charAt(0));
            boxInsn.add(new MethodInsnNode(INVOKESTATIC, primClassName, "valueOf", "(" + param.getValue() + ")L" + primClassName + ";"));
        }

        return boxInsn;
    }

    private String generifyDesc(String desc) {
        String returnDesc = DescriptorParser.parseMethodDesc(desc).getReturnType().toDesc();
        return OBJ_ARR_DESC + returnDesc;
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
}
