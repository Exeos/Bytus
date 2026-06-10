package me.exeos.bytus.core.transformer.impl.flow.data;

import me.exeos.bytus.asmplus.descriptor.DescriptorMember;
import me.exeos.bytus.asmplus.descriptor.DescriptorParser;
import me.exeos.bytus.asmplus.descriptor.descriptors.method.MethodDescriptor;
import me.exeos.bytus.asmplus.jar.JarArchive;
import me.exeos.bytus.asmplus.utils.*;
import me.exeos.bytus.core.config.BytusConfig;
import me.exeos.bytus.core.transformer.AbstractTransformer;
import me.exeos.bytus.core.transformer.Priority;
import me.exeos.bytus.core.transformer.context.JarContext;
import me.exeos.bytus.core.transformer.extensions.MethodExtension;
import org.objectweb.asm.tree.*;

import java.util.*;

/**
 * Transforms argument passing to Object[]
 * Pure aids to implement because of unlimited edge cases.
 * TODO: handle interfaces better than just excluding them
 * TODO: use Pipeline methods better so emitted methods can be obfuscated
 */
public class ParamGenerifier extends AbstractTransformer {

    public ParamGenerifier(BytusConfig config) {
        super(config);
    }

    private void buildExclusions(JarArchive jar, Set<String> exclusionsByDesc, Set<String> exclusionsByName) {
        for (ClassNode classNode : jar.getClasses().values()) {
            // map tracking amount of methods declared by their ower + name
            Map<String, Integer> methodDeclarationMap = new HashMap<>();
            for (MethodNode methodNode : classNode.methods) {
                // exclude methods in same class with same name as signature would be the same after transformation
                methodDeclarationMap.merge(classNode.name + methodNode.name, 1, (oldVal, _) -> oldVal + 1);
                if (methodDeclarationMap.get(classNode.name + methodNode.name) > 1) {
                    exclusionsByName.add(classNode.name + methodNode.name);
                }

                // exclude all methods declared in interfaces
                if ((classNode.access & ACC_INTERFACE) != 0) {
                    exclusionsByName.add(classNode.name + methodNode.name);
                }

                // exclude all methods invoked by InvokeDynamic
                exclusionsByDesc.addAll(MethodUtil.getInvokeDynamicTargets(methodNode));
            }
        }

        HierarchyUtil.expandExclusions(jar, exclusionsByDesc, exclusionsByName);

        // exclude entry points
        if (config.entryPoints.fromManifest())
            JarUtil.getMainMethodFromManifest(jar).ifPresent(exclusionsByDesc::add);

        config.entryPoints.custom().forEach((className, methodName) -> {
            exclusionsByName.add(className.replace(".", "/") + methodName);
        });
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
        // ownerCtx + name + desc
        Set<String> exclusionsByDesc = new HashSet<>();
        // ownerCtx + name
        Set<String> exclusionsByName = new HashSet<>();

        buildExclusions(context.jar(), exclusionsByDesc, exclusionsByName);

        for (ClassNode classNode : context.jar().getClasses().values()) {
            for (MethodNode methodNode : classNode.methods) {
                convertParamPassing(context.jar(), methodNode, context.pipeline().getExtension(methodNode), exclusionsByDesc, exclusionsByName);
            }

            for (MethodNode methodNode : classNode.methods) {
                convertParamUsage(context, classNode, methodNode, exclusionsByDesc, exclusionsByName);
            }
        }
    }

    /**
     * Converts the way params are passed to Methods from normal passing to Object[] passing
     */
    private void convertParamPassing(JarArchive jar, MethodNode methodNode, MethodExtension methodExtension, Set<String> exclusionsByDesc, Set<String> exclusionsByName) {
        int paramArrVarIndex = methodNode.maxLocals++;
        InsnUtil.loop(methodNode.instructions, insnNode -> {
            if (!(insnNode instanceof MethodInsnNode methodInsnNode)) {
                return;
            }

            // check if target method is included and if ownerCtx of target method belongs to jarCtx
            if (exclusionsByDesc.contains(methodInsnNode.owner + methodInsnNode.name + methodInsnNode.desc)
                    || exclusionsByName.contains(methodInsnNode.owner + methodInsnNode.name)
                    || !jar.getClasses().containsKey(methodInsnNode.owner)
            ) {
                return;
            }

            MethodDescriptor descriptor = DescriptorParser.parseMethodDesc(methodInsnNode.desc);
            int paramLength = descriptor.getParams().size();
            if (paramLength == 0) {
                return;
            }

            // tmp variables to avoid stack swapping as it can lead to issues with doubles and longs
            // each param gets a local assigned
            int[] tmpLocal = new int[paramLength];
            for (int i = 0; i < paramLength; i++) {
                tmpLocal[i] = methodNode.maxLocals;
                methodNode.maxLocals += descriptor.getParams().get(i).getSlotWidth();
            }

            InsnList arrBuilder = new InsnList();

            // store params stored on stack into temps in reverse (stack top is last argument)
            for (int i = paramLength - 1; i >= 0; i--) {
                arrBuilder.add(new VarInsnNode(TypeUtil.storeOpcodeForType(descriptor.getParams().get(i)), tmpLocal[i]));
            }

            // create Object[] and store at paramArrVarIndex
            arrBuilder.add(methodExtension.getObfuscatedIntPush(paramLength));
            arrBuilder.add(new TypeInsnNode(ANEWARRAY, "java/lang/Object"));
            arrBuilder.add(new VarInsnNode(ASTORE, paramArrVarIndex));

            // store each param in the Object[]
            for (int i = 0; i < paramLength; i++) {
                DescriptorMember param = descriptor.getParams().get(i);

                // load Object[]
                arrBuilder.add(new VarInsnNode(ALOAD, paramArrVarIndex));
                // param index for Object[]
                arrBuilder.add(methodExtension.getObfuscatedIntPush(i));
                // load actual param from its tempLocal
                arrBuilder.add(new VarInsnNode(TypeUtil.loadOpcodeForType(param), tmpLocal[i]));
                // convert to object if it's a primitive
                if (param.isPrimitive() && !param.isArray()) {
                    String primClassName = TypeUtil.primitiveToClass(param.getValue().charAt(0));

                    arrBuilder.add(new MethodInsnNode(INVOKESTATIC, primClassName, "valueOf", "(" + param.getValue() + ")L" + primClassName + ";"));
                }
                // store in Object[]
                arrBuilder.add(new InsnNode(AASTORE));
            }
            // load finalized Object[] for passing to method
            arrBuilder.add(new VarInsnNode(ALOAD, paramArrVarIndex));

            methodNode.instructions.insertBefore(insnNode, arrBuilder);
            methodInsnNode.desc = "([Ljava/lang/Object;)" + descriptor.getReturnType().toDesc();
        });
    }

    private void convertParamUsage(JarContext context, ClassNode ownerNode, MethodNode methodNode, Set<String> exclusionsByDesc, Set<String> exclusionsByName) {
        if (exclusionsByDesc.contains(ownerNode.name + methodNode.name + methodNode.desc) || exclusionsByName.contains(ownerNode.name + methodNode.name)) {
            return;
        }

        MethodDescriptor descriptor = DescriptorParser.parseMethodDesc(methodNode.desc);
        if (descriptor.getParams().isEmpty()) {
            return;
        }

        // maps original slot -> parameter
        Map<Integer, DescriptorMember> paramBySlot = new HashMap<>();
        // maps original slot-> index in Object[]
        Map<Integer, Integer> paramArrayIndexBySlot = new HashMap<>();
        // maps original slot -> newly allocated local var slot (for params that are written to)
        Map<Integer, Integer> localBySlot = new HashMap<>();
        // set of original slots that are written to
        Set<Integer> writtenSlots = new HashSet<>();

        int paramsStartIndex = MethodUtil.getParamSlotStart(methodNode);

        // slot of param
        int paramSlot = paramsStartIndex;
        // index of param in Object[]
        int paramArrayIndex = 0;
        // map slots to (params and index of param in Object[])
        for (DescriptorMember param : descriptor.getParams()) {
            paramBySlot.put(paramSlot, param);
            paramArrayIndexBySlot.put(paramSlot, paramArrayIndex);

            paramSlot += param.getSlotWidth();
            paramArrayIndex++;
        }

        // map what slots are mutated
        for (AbstractInsnNode insnNode : methodNode.instructions) {
            if (insnNode instanceof VarInsnNode varInsnNode && InsnUtil.isStore(insnNode)) {
                if (paramBySlot.containsKey(varInsnNode.var)) {
                    writtenSlots.add(varInsnNode.var);
                }
            } else if (insnNode instanceof IincInsnNode iincInsnNode && paramBySlot.containsKey(iincInsnNode.var)) {
                writtenSlots.add(iincInsnNode.var);
            }
        }

        // map slots that are written to, to new local variable
        paramSlot = paramsStartIndex;
        for (DescriptorMember param : descriptor.getParams()) {
            if (writtenSlots.contains(paramSlot)) {
                localBySlot.put(paramSlot, methodNode.maxLocals);
                methodNode.maxLocals += param.getSlotWidth();
            }

            paramSlot += param.getSlotWidth();
        }

        // prologue stores params that are written to in local vars
        InsnList prologue = new InsnList();
        for (Map.Entry<Integer, Integer> entry : localBySlot.entrySet()) {
            DescriptorMember param = paramBySlot.get(entry.getKey());

            prologue.add(new VarInsnNode(ALOAD, paramsStartIndex));
            prologue.add(InsnUtil.getIntPush(paramArrayIndexBySlot.get(entry.getKey())));
            prologue.add(new InsnNode(AALOAD));
            prologue.add(new TypeInsnNode(CHECKCAST, (param.isArray() ? param : param.toNonePrimitive()).toType()));
            // if param was primitive convert it to primitive
            if (param.isPrimitive() && !param.isArray()) {
                char primitive = param.getValue().charAt(0);

                prologue.add(new MethodInsnNode(
                        INVOKEVIRTUAL,
                        TypeUtil.primitiveToClass(primitive),
                        TypeUtil.clsInstanceToPrimMethodName(primitive),
                        "()" + primitive
                ));
            }
            prologue.add(new VarInsnNode(TypeUtil.storeOpcodeForType(param), entry.getValue()));
        }

        InsnUtil.loop(methodNode.instructions, insnNode -> {
            if (insnNode instanceof VarInsnNode loadInsn && InsnUtil.isLoad(insnNode)) {
                // check if param is written to, if yes: load from local, else load from Object[]
                if (writtenSlots.contains(loadInsn.var)) {
                    DescriptorMember param = paramBySlot.get(loadInsn.var);
                    methodNode.instructions.insertBefore(insnNode, new VarInsnNode(TypeUtil.loadOpcodeForType(param), localBySlot.get(loadInsn.var)));
                    methodNode.instructions.remove(insnNode);
                } else {
                    DescriptorMember param = paramBySlot.get(loadInsn.var);
                    if (param == null) {
                        return;
                    }

                    InsnList loadFromArr = new InsnList();

                    // load Object[] containing params
                    loadFromArr.add(new VarInsnNode(ALOAD, paramsStartIndex));
                    // push index of param (-paramsStartIndex because first param will always be arr[0] but loadInsn.var will be 1 for virtual methods)
                    loadFromArr.add(InsnUtil.getIntPush(paramArrayIndexBySlot.get(loadInsn.var)));
                    // load param from array
                    loadFromArr.add(new InsnNode(AALOAD));
                    // cast to correct type
                    loadFromArr.add(new TypeInsnNode(CHECKCAST, (param.isArray() ? param : param.toNonePrimitive()).toType()));
                    // if param was primitive convert it to primitive
                    if (param.isPrimitive() && !param.isArray()) {
                        char primitive = param.getValue().charAt(0);

                        loadFromArr.add(new MethodInsnNode(
                                INVOKEVIRTUAL,
                                TypeUtil.primitiveToClass(primitive),
                                TypeUtil.clsInstanceToPrimMethodName(primitive),
                                "()" + primitive
                        ));
                    }

                    methodNode.instructions.insertBefore(insnNode, loadFromArr);
                    methodNode.instructions.remove(insnNode);
                }
            }
            // update each insn that writes to param to write to corresponding local
            else if (insnNode instanceof VarInsnNode varInsnNode && InsnUtil.isStore(insnNode)) {
                if (writtenSlots.contains(varInsnNode.var)) {
                    varInsnNode.var = localBySlot.get(varInsnNode.var);
                }
            } else if (insnNode instanceof IincInsnNode iincInsnNode && writtenSlots.contains(iincInsnNode.var)) {
                iincInsnNode.var = localBySlot.get(iincInsnNode.var);
            }
        });

        // insert prologue (storing params in locals if required) and update this methods descriptor
        methodNode.instructions.insertBefore(methodNode.instructions.getFirst(), prologue);
        methodNode.desc = "([Ljava/lang/Object;)" + descriptor.getReturnType().toDesc();
        context.pipeline().getExtension(methodNode).paramObfInfo.setParamObf(paramsStartIndex, paramArrayIndexBySlot);
    }
}
