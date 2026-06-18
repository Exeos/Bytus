package me.exeos.bytus.core.transformer.impl.flow.data;

import me.exeos.bytus.asmplus.descriptor.DescriptorMember;
import me.exeos.bytus.asmplus.descriptor.DescriptorParser;
import me.exeos.bytus.asmplus.descriptor.descriptors.method.MethodDescriptor;
import me.exeos.bytus.asmplus.idkhowtonamethisyet.MWList;
import me.exeos.bytus.asmplus.idkhowtonamethisyet.MethodWrapper;
import me.exeos.bytus.asmplus.jar.JarArchive;
import me.exeos.bytus.asmplus.utils.*;
import me.exeos.bytus.core.config.BytusConfig;
import me.exeos.bytus.core.transformer.AbstractTransformer;
import me.exeos.bytus.core.transformer.Priority;
import me.exeos.bytus.core.transformer.context.JarContext;
import me.exeos.bytus.core.transformer.extensions.MethodExtension;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Transforms argument passing to Object[]
 * Pure aids to implement because of unlimited edge cases.
 * TODO: use Pipeline methods better so emitted methods can be obfuscated
 */
public class ParamGenerifier extends AbstractTransformer {

    public ParamGenerifier(BytusConfig config) {
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
        MWList exclusions = buildExclusions(context);

        for (ClassNode classNode : context.jar().getClasses().values()) {
            for (MethodNode methodNode : classNode.methods) {
                convertParamPassing(context.jar(), methodNode, context.pipeline().getExtension(methodNode), exclusions);
            }

            for (MethodNode methodNode : classNode.methods) {
                convertParamUsage(context, classNode, methodNode, exclusions);
            }
        }
    }

    private MWList buildExclusions(JarContext context) {
        MWList exclusions = new MWList(config.getEntryPoints(context.jar()));

        exclusions.add(MethodWrapper.of("<clinit>"));
        exclusions.add(MethodWrapper.of("<init>"));


        for (ClassNode classNode : context.jar().getClasses().values()) {
            Map<String, Integer> methodDeclarationMap = new HashMap<>();
            var x = classNode.methods.stream().collect(Collectors.toMap(methodNode -> methodNode.name, e -> 1, Math::addExact));
            for (MethodNode methodNode : classNode.methods) {
                if (x.get(methodNode.name) > 1) {
                    exclusions.add(new MethodWrapper(classNode.name, methodNode.name, methodNode.desc, 1));
                }
            }
        }

        for (ClassNode classNode : context.jar().getClasses().values()) {
            for (MethodNode methodNode : classNode.methods) {
                for (AbstractInsnNode insnNode : methodNode.instructions) {
                    if (!(insnNode instanceof InvokeDynamicInsnNode indy)) {
                        continue;
                    }

                    DescriptorMember indyRet = DescriptorParser.parseMethodDesc(indy.desc).getReturnType();
                    if (indyRet.isPrimitive() || indyRet.isArray() || indy.bsmArgs.length < 2 || !(indy.bsmArgs[0] instanceof Type normalType) || !(indy.bsmArgs[1] instanceof Handle handle)) {
                        continue;
                    }

                    String owner = indyRet.getValue();
                    String name = indy.name;
                    String desc = normalType.getDescriptor();

                    // exclude lambdas methods invoked by members not in jar as their sigs cant be changed
                    if (!context.jar().getClasses().containsKey(owner) || exclusions.contains(MethodWrapper.of(handle.getOwner(), handle.getName(), handle.getDesc()))) {
                        exclusions.add(MethodWrapper.of(owner, name, desc));
                        exclusions.add(MethodWrapper.of(handle.getOwner(), handle.getName(), handle.getDesc()));
                    }
                }
            }
        }

        var hierarchy = context.getExtension().getHierarchyNameMapped();
        for (MethodWrapper wrapper : exclusions.get().toArray(new MethodWrapper[0])) {
            if (!hierarchy.containsKey(wrapper.owner())) {
                continue;
            }

            hierarchy.get(wrapper.owner()).findMethodRoot(wrapper.name(), wrapper.desc()).ifPresent(rootEdge -> {
                if (wrapper.owner().endsWith("StatisticFunction") && wrapper.name().equals("evaluate")) {
                    System.out.println();
                }
                rootEdge.getOverriders().forEach(ov -> {
                    exclusions.add(MethodWrapper.of(ov.owner().classNode.name, ov.methodNode().name, ov.methodNode().desc));
                });
            });
        }

        return exclusions;
    }

    /**
     * Converts the way params are passed to Methods from normal passing to Object[] passing
     */
    private void convertParamPassing(JarArchive jar, MethodNode methodNode, MethodExtension methodExtension, MWList exclusions) {
        int paramArrVarIndex = methodNode.maxLocals;
        AtomicBoolean updatedInsn = new AtomicBoolean(false);

        InsnUtil.loop(methodNode.instructions, insnNode -> {
            switch (insnNode) {
                case MethodInsnNode methodInsnNode -> {
                    // check if invocation target is in the jar and not excluded
                    if (exclusions.contains(MethodWrapper.of(methodInsnNode)) || !jar.getClasses().containsKey(methodInsnNode.owner)) {
                        return;
                    }

                    MethodDescriptor descriptor = DescriptorParser.parseMethodDesc(methodInsnNode.desc);
                    int paramLength = descriptor.getParams().size();
                    if (paramLength == 0) {
                        return;
                    }

                    if (!updatedInsn.get()) {
                        methodNode.maxLocals++;
                        updatedInsn.set(true);
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
                }
                case InvokeDynamicInsnNode indy -> {
                    DescriptorMember indyRet = DescriptorParser.parseMethodDesc(indy.desc).getReturnType();
                    if (indyRet.isPrimitive() || indyRet.isArray()
                            || indy.bsmArgs.length < 3
                            || !(indy.bsmArgs[0] instanceof Type normalType)
                            || !(indy.bsmArgs[1] instanceof Handle handle)
                            || !(indy.bsmArgs[2] instanceof Type erasedType)
                    ) {
                        return;
                    }

                    String owner = indyRet.getValue();
                    String name = indy.name;
                    String desc = normalType.getDescriptor();

                    if (exclusions.contains(MethodWrapper.of(owner, name, desc)) || !jar.getClasses().containsKey(owner)) {
                        return;
                    }

                    MethodDescriptor targetDesc = DescriptorParser.parseMethodDesc(normalType.getDescriptor());
                    if (targetDesc.getParams().isEmpty()) {
                        return;
                    }

                    DescriptorMember typeRet = targetDesc.getReturnType();

                    indy.bsmArgs[0] = Type.getType("([Ljava/lang/Object;)" + typeRet.toDesc());
                    indy.bsmArgs[1] = new Handle(
                            handle.getTag(),
                            handle.getOwner(),
                            handle.getName(),
                            "(L[java/lang/Object;)" + DescriptorParser.parseMethodDesc(handle.getDesc()).getReturnType().toDesc(),
                            handle.isInterface()
                    );
                    indy.bsmArgs[2] = Type.getType("([Ljava/lang/Object;)" + typeRet.erase().toDesc());
                }
                default -> {}
            }
        });
    }

    private void convertParamUsage(JarContext context, ClassNode ownerNode, MethodNode methodNode, MWList exclusions) {
        if (exclusions.contains(MethodWrapper.of(ownerNode.name, methodNode))) {
            return;
        }

        MethodDescriptor descriptor = DescriptorParser.parseMethodDesc(methodNode.desc);
        if (descriptor.getParams().isEmpty()) {
            return;
        }

        MethodExtension methodExtension = context.pipeline().getExtension(methodNode);

        // maps original slot -> parameter
        Map<Integer, DescriptorMember> paramBySlot = new HashMap<>();
        // maps original slot-> index in Object[]
        Map<Integer, Integer> paramArrayIndexBySlot = new HashMap<>();
        // maps original slot -> newly allocated local var slot (for params that are written to)
        Map<Integer, Integer> localBySlot = new HashMap<>();
        // set of original slots that are written to
        Set<Integer> writtenSlots = new HashSet<>();

        int paramsStartIndex = MethodUtil.getLocalsOffset(methodNode);

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

        context.pipeline().getExtension(methodNode).paramObfInfo.setParamObf(paramsStartIndex, paramArrayIndexBySlot);

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
            prologue.add(methodExtension.getObfuscatedIntPush(paramArrayIndexBySlot.get(entry.getKey())));
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
                    loadFromArr.add(methodExtension.getObfuscatedIntPush(paramArrayIndexBySlot.get(loadInsn.var)));
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
    }
}
