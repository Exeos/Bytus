package me.exeos.bytus.core.transformer.impl.flow.data;

import me.exeos.asmplus.analysis.hierarchy.edge.ClassEdge;
import me.exeos.asmplus.analysis.hierarchy.edge.MethodEdge;
import me.exeos.asmplus.codegen.value.impl.ConstantPusher;
import me.exeos.asmplus.descriptor.DescriptorMember;
import me.exeos.asmplus.descriptor.DescriptorParser;
import me.exeos.asmplus.descriptor.descriptors.method.MethodDescriptor;
import me.exeos.asmplus.jar.JarArchive;
import me.exeos.asmplus.utils.AsmUtil;
import me.exeos.asmplus.utils.InsnUtil;
import me.exeos.asmplus.utils.TypeUtil;
import me.exeos.bytus.core.config.BytusConfig;
import me.exeos.bytus.core.transformer.AbstractTransformer;
import me.exeos.bytus.core.transformer.context.JarContext;
import org.objectweb.asm.tree.*;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

public class ParamGenerify extends AbstractTransformer {

    private final static String OBJ_ARR_DESC = "([Ljava/lang/Object;)";

    public ParamGenerify(BytusConfig config) {
        super(config);
    }

    @Override
    public boolean applies() {
        return true;
    }

    @Override
    public int priority() {
        return 10;
    }

    static void test(int i) {
        // locStart = 1
        // paramSize = 1

        // .var = 1

        // if (var - localStart) < paramSize
    }

    @Override
    public void transform(JarContext context) {
        RewriteResult result = rewriteDescriptorsAndCollect(context);
        rewriteCallsites(context, result.rewrittenIdx);
        rewriteLocalUsage(context, result.idxOriginalDescMap);
    }

    private RewriteResult rewriteDescriptorsAndCollect(JarContext context) {
        Set<String> rewrittenIdx = new HashSet<>();
        Map<String, String> idxOriginalDescMap = new HashMap<>();

        for (ClassNode classNode : context.jar().getClasses().values()) {
            ClassEdge classEdge = context.getExtension().getHierarchy().get(classNode);
            if (classEdge == null || classEdge.hasUnresolved()) {
                continue;
            }

            for (MethodEdge methodEdge : classEdge.methods) {
                if (!isValidGroup(context.jar(), methodEdge)) {
                    continue;
                }

                for (MethodEdge oge : methodEdge.getOverrideGroup()) {
                    String genericDesc = generifyDesc(oge.getDesc());
                    // add to set of idx that need to be refactored (original desc)
                    rewrittenIdx.add(classNode.name + oge.getName() + oge.getDesc());
                    // map generified desc idx to original desc
                    idxOriginalDescMap.put(classNode.name + oge.getName() + genericDesc, oge.getDesc());

                    // update method desc
                    oge.methodNode().desc = genericDesc;
                }
            }
        }

        return new RewriteResult(rewrittenIdx, idxOriginalDescMap);
    }

    private void rewriteCallsites(JarContext context, Set<String> needRefactoring) {
        for (ClassNode classNode : context.jar().getClasses().values()) {
            for (MethodNode methodNode : classNode.methods) {
                int arrLocal = methodNode.maxLocals++;
                int tempStore = methodNode.maxLocals++;
                InsnUtil.loop(methodNode.instructions, insnNode -> {
                    if (insnNode instanceof MethodInsnNode methodInsnNode && needRefactoring.contains(methodInsnNode.owner + methodInsnNode.name + methodInsnNode.desc)) {
                        MethodDescriptor methodDescriptor = DescriptorParser.parseMethodDesc(methodInsnNode.desc);
                        InsnList packInsn = new InsnList();

                        packInsn.add(ConstantPusher.getIntPush(methodDescriptor.getParams().size()));
                        packInsn.add(new TypeInsnNode(ANEWARRAY, "java/lang/Object"));
                        packInsn.add(new VarInsnNode(ASTORE, arrLocal));

                        for (int i = methodDescriptor.getParams().size() - 1; i >= 0; i--) {
                            packInsn.add(storeToObjectArray(methodDescriptor, i, arrLocal, tempStore));
                        }

                        packInsn.add(new VarInsnNode(ALOAD, arrLocal));
                        methodNode.instructions.insertBefore(methodInsnNode, packInsn);
                        methodInsnNode.desc = generifyDesc(methodInsnNode.desc);
                    }
                });
            }
        }
    }

    private void rewriteLocalUsage(JarContext context, Map<String, String> originalDescMap) {
        for (ClassNode classNode : context.jar().getClasses().values()) {
            for (MethodNode methodNode : classNode.methods) {
                String idx = classNode.name + methodNode.name + methodNode.desc;
                if (!originalDescMap.containsKey(idx)) {
                    continue;
                }

                MethodDescriptor originalDesc = DescriptorParser.parseMethodDesc(originalDescMap.get(idx));
                int localStart = AsmUtil.hasAccess(methodNode.access, ACC_STATIC) ? 0 : 1;
                AtomicBoolean tempLocalUsed = new AtomicBoolean(false);
                int tempLocal = methodNode.maxLocals;

                Map<Integer, Integer> localArrayIndexMap = new HashMap<>();
                int local = localStart;
                for (int i = 0; i < originalDesc.getParams().size(); i++) {
                    localArrayIndexMap.put(local, i);
                    local += originalDesc.getParams().get(i).getSlotWidth();
                }

                InsnUtil.loop(methodNode.instructions, insnNode -> {
                    InsnList replacement = new InsnList();

                    if (insnNode instanceof VarInsnNode varInsnNode) {
                        Integer localArrayIndex = localArrayIndexMap.get(varInsnNode.var);
                        if (localArrayIndex == null) {
                            return;
                        }

                        if (InsnUtil.isLoad(insnNode)) {
                            replacement.add(loadFromObjectArray(originalDesc, localArrayIndex, localStart));
                        } else {
                            replacement.add(storeToObjectArray(originalDesc, localArrayIndex, localStart, tempLocal));
                            tempLocalUsed.set(true);
                        }
                    } else if (insnNode instanceof IincInsnNode iincInsnNode) {
                        Integer localArrayIndex = localArrayIndexMap.get(iincInsnNode.var);
                        if (localArrayIndex == null) {
                            return;
                        }

                        replacement.add(loadFromObjectArray(originalDesc, localArrayIndex, localStart));
                        replacement.add(ConstantPusher.getIntPush(iincInsnNode.incr));
                        replacement.add(new InsnNode(IADD));
                        replacement.add(storeToObjectArray(originalDesc, localArrayIndex, localStart, tempLocal));
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

    private InsnList loadFromObjectArray(MethodDescriptor methodDescriptor, int paramIndex, int arrayLocal) {
        InsnList loadInsn = new InsnList();
        loadInsn.add(new VarInsnNode(ALOAD, arrayLocal));
        loadInsn.add(ConstantPusher.getIntPush(paramIndex));
        loadInsn.add(new InsnNode(AALOAD));
        loadInsn.add(unbox(methodDescriptor, paramIndex));
        return loadInsn;
    }

    private InsnList storeToObjectArray(MethodDescriptor methodDescriptor, int paramIndex, int arrayLocal, int tempLocal) {
        InsnList storeInsn = new InsnList();
        storeInsn.add(box(methodDescriptor, paramIndex));
        storeInsn.add(new VarInsnNode(ASTORE, tempLocal));
        storeInsn.add(new VarInsnNode(ALOAD, arrayLocal));
        storeInsn.add(ConstantPusher.getIntPush(paramIndex));
        storeInsn.add(new VarInsnNode(ALOAD, tempLocal));
        storeInsn.add(new InsnNode(AASTORE));
        return storeInsn;
    }

    private InsnList unbox(MethodDescriptor methodDescriptor, int index) {
        InsnList unboxInsn = new InsnList();
        DescriptorMember param = methodDescriptor.getParams().get(index);

        unboxInsn.add(new TypeInsnNode(CHECKCAST, (param.isArray() ? param : param.toNonePrimitive()).toType()));
        if (param.isPrimitive()) {
            char primitive = param.getValue().charAt(0);
            unboxInsn.add(new MethodInsnNode(
                    INVOKESTATIC,
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

    private boolean isValidGroup(JarArchive jar, MethodEdge root) {
        for (MethodEdge methodEdge : root.getOverrideGroup()) {
            if (methodEdge.owner().hasUnresolved() || nameCollides(methodEdge) || config.isEntryPoint(jar, methodEdge.getOwnerName(), methodEdge.methodNode())) {
                return false;
            }
        }

        return true;
    }

    private boolean nameCollides(MethodEdge methodEdge) {
        Set<MethodEdge> sameName = methodEdge.owner().findAllMethods(methodEdge.getName());
        return sameName.size() > 1;
    }

    private record RewriteResult(Set<String> rewrittenIdx, Map<String, String> idxOriginalDescMap) {
    }
}
