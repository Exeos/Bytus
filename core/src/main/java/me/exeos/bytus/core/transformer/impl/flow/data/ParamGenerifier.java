package me.exeos.bytus.core.transformer.impl.flow.data;

import me.exeos.bytus.asmplus.analysis.hierarchy.edge.ClassEdge;
import me.exeos.bytus.asmplus.analysis.hierarchy.edge.MethodEdge;
import me.exeos.bytus.asmplus.descriptor.DescriptorMember;
import me.exeos.bytus.asmplus.descriptor.DescriptorParser;
import me.exeos.bytus.asmplus.descriptor.descriptors.method.MethodDescriptor;
import me.exeos.bytus.asmplus.matcher.method.MethodMatcher;
import me.exeos.bytus.asmplus.matcher.method.MethodMatchEntry;
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

/**
 * Changes the regular way that params are passed to methods, to pass a single Object[]
 * TODO: use Pipeline methods better so emitted methods can be obfuscated
 */
public class ParamGenerifier extends AbstractTransformer {

    private final static String OBJ_ARR_DESC = "([Ljava/lang/Object;)";
    
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
        MethodMatcher exclusions = buildExclusions(context);

        for (ClassNode classNode : context.jar().getClasses().values()) {
            for (MethodNode methodNode : classNode.methods) {
                convertParamPassing(context.jar(), methodNode, context.pipeline().getExtension(methodNode), exclusions);
            }

            for (MethodNode methodNode : classNode.methods) {
                convertParamUsage(context, classNode, methodNode, exclusions);
            }
        }
    }

    private MethodMatcher buildExclusions(JarContext context) {
        MethodMatcher exclusions = new MethodMatcher(config.getEntryPoints(context.jar()));
        exclusions.add(MethodMatchEntry.of("<clinit>"));

        for (ClassNode classNode : context.jar().getClasses().values()) {
            ClassEdge classEdge = context.getExtension().getHierarchy().get(classNode);
            if (classEdge == null || classEdge.hasUnresolved()) {
                classNode.methods.forEach(methodNode -> exclusions.add(MethodMatchEntry.of(classNode.name, methodNode)));
            } else {
                excludeMethodsWithCollidingSignatures(classEdge, exclusions);
            }
        }

        excludeProblematicIndy(context.jar(), exclusions);

        HierarchyUtil.hierarchyExpandMethodMatcher(exclusions, context.getExtension().getHierarchyNameMapped());

        return exclusions;
    }

    /**
     * Exclude all methods that would collide, if they had the same signature
     * @param classEdge
     * @param exclusions
     */
    private void excludeMethodsWithCollidingSignatures(ClassEdge classEdge, MethodMatcher exclusions) {
        for (MethodEdge methodEdge : classEdge.getMethods()) {
            int sameMethodCount = 0;
            boolean isStatic = MethodUtil.hasAccess(methodEdge.methodNode(), ACC_STATIC);
            Set<String> affectedOwners = new HashSet<>();

            for (MethodEdge foundEdge : classEdge.findMethods(methodEdge.getName())) {
                // only collide if both methods are static, or both aren't
                if (isStatic == MethodUtil.hasAccess(foundEdge.methodNode(), ACC_STATIC)) {
                    affectedOwners.add(foundEdge.getOwner());
                    sameMethodCount++;
                }
            }

            if (sameMethodCount > 1) {
                for (String owner : affectedOwners) {
                    exclusions.add(MethodMatchEntry.of(owner, methodEdge.methodNode(), MethodMatcher.Mode.OWNER_NAME));
                }
            }
        }
    }

    /**
     * Handles indy. TODO: document this with claude
     * @param jar
     * @param exclusions
     */
    private void excludeProblematicIndy(JarArchive jar, MethodMatcher exclusions) {
        for (ClassNode classNode : jar.getClasses().values()) {
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
                    // I don't remember why I check and exclude handle.* but works
                    if (!jar.getClasses().containsKey(owner)
                            || exclusions.match(
                                    MethodMatchEntry.of(handle.getOwner(), handle.getName(), handle.getDesc())
                            )
                    ) {
                        exclusions.add(MethodMatchEntry.of(owner, name, desc));
                        exclusions.add(MethodMatchEntry.of(handle.getOwner(), handle.getName(), handle.getDesc()));
                    }
                }
            }
        }
    }

    /**
     * Converts the way params are passed to Methods from normal passing to Object[] passing
     */
    private void convertParamPassing(JarArchive jar, MethodNode methodNode, MethodExtension methodExtension, MethodMatcher exclusions) {
        int paramArrVarIndex = methodNode.maxLocals;
        AtomicBoolean updatedInsn = new AtomicBoolean(false);

        InsnUtil.loop(methodNode.instructions, insnNode -> {
            switch (insnNode) {
                case MethodInsnNode methodInsnNode -> {
                    // check if invocation target is in the jar and not excluded
                    if (exclusions.match(MethodMatchEntry.of(methodInsnNode)) || !jar.getClasses().containsKey(methodInsnNode.owner)) {
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
                    methodInsnNode.desc = OBJ_ARR_DESC + descriptor.getReturnType().toDesc();
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

                    if (exclusions.match(MethodMatchEntry.of(owner, name, desc)) || !jar.getClasses().containsKey(owner)) {
                        return;
                    }

                    MethodDescriptor targetDesc = DescriptorParser.parseMethodDesc(normalType.getDescriptor());
                    if (targetDesc.getParams().isEmpty()) {
                        return;
                    }

                    DescriptorMember typeRet = targetDesc.getReturnType();

                    indy.bsmArgs[0] = Type.getType(OBJ_ARR_DESC + typeRet.toDesc());
                    indy.bsmArgs[1] = new Handle(
                            handle.getTag(),
                            handle.getOwner(),
                            handle.getName(),
                            OBJ_ARR_DESC + DescriptorParser.parseMethodDesc(handle.getDesc()).getReturnType().toDesc(),
                            handle.isInterface()
                    );
                    indy.bsmArgs[2] = Type.getType(OBJ_ARR_DESC + typeRet.erase().toDesc());
                }
                default -> {}
            }
        });
    }

    private void convertParamUsage(JarContext context, ClassNode ownerNode, MethodNode methodNode, MethodMatcher exclusions) {
        if (exclusions.match(MethodMatchEntry.of(ownerNode.name, methodNode))) {
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
        methodNode.desc = OBJ_ARR_DESC + descriptor.getReturnType().toDesc();
    }
}
