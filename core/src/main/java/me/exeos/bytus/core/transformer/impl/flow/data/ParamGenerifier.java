package me.exeos.bytus.core.transformer.impl.flow.data;

import me.exeos.bytus.asmplus.descriptor.DescriptorMember;
import me.exeos.bytus.asmplus.descriptor.DescriptorParser;
import me.exeos.bytus.asmplus.descriptor.descriptors.method.MethodDescriptor;
import me.exeos.bytus.asmplus.jar.JarArchive;
import me.exeos.bytus.asmplus.utils.HierarchyUtil;
import me.exeos.bytus.asmplus.utils.InsnUtil;
import me.exeos.bytus.asmplus.utils.MethodUtil;
import me.exeos.bytus.asmplus.utils.TypeUtil;
import me.exeos.bytus.core.transformer.Transformer;
import me.exeos.bytus.core.transformer.TransformerPipeline;
import org.objectweb.asm.Handle;
import org.objectweb.asm.tree.*;

import java.util.*;

/**
 * Transforms argument passing to Object[]
 * Pure aids to implement because of unlimited edge cases.
 * TODO: handle interfaces better than just excluding them
 */
public class ParamGenerifier extends Transformer {

    private final String mainClassName;

    public ParamGenerifier(JarArchive jar, List<String> exclusions, List<String> inclusions, String mainClassName) {
        super(jar, exclusions, inclusions);
        this.mainClassName = mainClassName;
    }

    @Override
    public void transform(TransformerPipeline pipeline) {
        // owner + name + desc
        Set<String> exclusionsByDesc = new HashSet<>();
        // owner + name
        Set<String> exclusionsByName = new HashSet<>();

        buildExclusions(exclusionsByDesc, exclusionsByName);

        for (ClassNode classNode : getIncludedClasses()) {
            for (MethodNode methodNode : classNode.methods) {
                convertParamPassing(methodNode, exclusionsByDesc, exclusionsByName);
            }

            for (MethodNode methodNode : classNode.methods) {
                convertParamUsage(classNode, methodNode, exclusionsByDesc, exclusionsByName);
            }
        }
    }

    private void buildExclusions(Set<String> exclusionsByDesc, Set<String> exclusionsByName) {
        for (ClassNode classNode : getIncludedClasses()) {
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
                exclusionsByDesc.addAll(getMethodsTargetedByInvokedynamic(methodNode));
            }
        }

        for (ClassNode classNode : getIncludedClasses()) {
            expandExclusions(exclusionsByDesc, exclusionsByName, classNode);
        }

        // exclude main method
        exclusionsByDesc.add(mainClassName + "main" + "([Ljava/lang/String;)V");
    }


    private void expandExclusions(Set<String> exclusionsByDesc, Set<String> exclusionsByName, ClassNode classNode) {
        // Collect all ancestor methods that are excluded, so we can exclude overrides and calls in this class.
        Set<String> excludedAncestorByDesc = new HashSet<>();
        Set<String> excludedAncestorByName = new HashSet<>();

        HierarchyUtil.forEachAncestorClass(getJar(), classNode, ancestor -> {
            for (MethodNode m : ancestor.methods) {
                String keyByDesc = ancestor.name + m.name + m.desc;
                if (exclusionsByDesc.contains(keyByDesc)) {
                    excludedAncestorByDesc.add(m.name + m.desc);
                }

                String keyByName = ancestor.name + m.name;
                if (exclusionsByName.contains(keyByName)) {
                    excludedAncestorByName.add(m.name);
                }
            }
        });

        // Apply exclusions to this class if it overrides or calls an excluded ancestor method.
        for (MethodNode m : classNode.methods) {
            // overrides
            if (excludedAncestorByDesc.contains(m.name + m.desc)) {
                exclusionsByDesc.add(classNode.name + m.name + m.desc);
            }
            if (excludedAncestorByName.contains(m.name)) {
                exclusionsByName.add(classNode.name + m.name);
            }

            // calls to method in super class
            for (AbstractInsnNode insnNode : m.instructions) {
                if (insnNode instanceof MethodInsnNode methodInsnNode) {
                    if (excludedAncestorByDesc.contains(methodInsnNode.name + methodInsnNode.desc)) {
                        exclusionsByDesc.add(classNode.name + methodInsnNode.name + methodInsnNode.desc);
                    }
                    if (excludedAncestorByName.contains(methodInsnNode.name)) {
                        exclusionsByName.add(classNode.name + methodInsnNode.name);
                    }
                }
            }
        }
    }

    /**
     * Converts the way params are passed to Methods from normal passing to Object[] passing
     * @param methodNode
     * @param exclusionsByDesc
     * @param exclusionsByName
     */
    private void convertParamPassing(MethodNode methodNode, Set<String> exclusionsByDesc, Set<String> exclusionsByName) {
        int paramArrVarIndex = methodNode.maxLocals++;
        for (AbstractInsnNode insnNode : methodNode.instructions) {
            if (insnNode instanceof MethodInsnNode methodInsnNode) {
                // check if target method is included and if owner of target method belongs to jar
                if (exclusionsByDesc.contains(methodInsnNode.owner + methodInsnNode.name + methodInsnNode.desc)
                        || exclusionsByName.contains(methodInsnNode.owner + methodInsnNode.name)
                        || !getJar().classes().containsKey(methodInsnNode.owner)
                ) {
                    continue;
                }

                MethodDescriptor descriptor = DescriptorParser.parseMethodDesc(methodInsnNode.desc);
                int paramLength = descriptor.params().size();
                if (paramLength == 0) {
                    continue;
                }

                // tmp variables to avoid stack swapping as it can lead to issues with doubles and longs
                // each param gets a local assigned
                int[] tmpLocal = new int[paramLength];
                for (int i = 0; i < paramLength; i++) {
                    tmpLocal[i] = methodNode.maxLocals;
                    methodNode.maxLocals += descriptor.params().get(i).getSlotWidth();
                }

                InsnList arrBuilder = new InsnList();

                // store params stored on stack into temps in reverse (stack top is last argument)
                for (int i = paramLength - 1; i >= 0; i--) {
                    arrBuilder.add(new VarInsnNode(TypeUtil.storeOpcodeForType(descriptor.params().get(i)), tmpLocal[i]));
                }

                // create Object[] and store at paramArrVarIndex
                arrBuilder.add(InsnUtil.getIntPush(paramLength));
                arrBuilder.add(new TypeInsnNode(ANEWARRAY, "java/lang/Object"));
                arrBuilder.add(new VarInsnNode(ASTORE, paramArrVarIndex));

                // store each param in the Object[]
                for (int i = 0; i < paramLength; i++) {
                    DescriptorMember param = descriptor.params().get(i);

                    // load Object[]
                    arrBuilder.add(new VarInsnNode(ALOAD, paramArrVarIndex));
                    // param index for Object[]
                    arrBuilder.add(InsnUtil.getIntPush(i));
                    // load actual param from its tempLocal
                    arrBuilder.add(new VarInsnNode(TypeUtil.loadOpcodeForType(param), tmpLocal[i]));
                    // convert to object if it's a primitive
                    if (param.isPrimitive() && !param.isArray()) {
                        String primClassName = TypeUtil.primitiveToClass(param.value().charAt(0));

                        arrBuilder.add(new MethodInsnNode(INVOKESTATIC, primClassName, "valueOf", "(" + param.value() + ")L" + primClassName + ";"));
                    }
                    // store in Object[]
                    arrBuilder.add(new InsnNode(AASTORE));
                }
                // load finalized Object[] for passing to method
                arrBuilder.add(new VarInsnNode(ALOAD, paramArrVarIndex));

                methodNode.instructions.insertBefore(insnNode, arrBuilder);
                methodInsnNode.desc = "([Ljava/lang/Object;)" + descriptor.returnType().toDesc();
            }
        }
    }

    private void convertParamUsage(ClassNode ownerNode, MethodNode methodNode, Set<String> exclusionsByDesc, Set<String> exclusionsByName) {
        if (exclusionsByDesc.contains(ownerNode.name + methodNode.name + methodNode.desc) || exclusionsByName.contains(ownerNode.name + methodNode.name)) {
            return;
        }

        MethodDescriptor descriptor = DescriptorParser.parseMethodDesc(methodNode.desc);
        if (descriptor.params().isEmpty()) {
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

        int paramsStartIndex = MethodUtil.hasAccess(methodNode, ACC_STATIC) ? 0 : 1;

        // slot of param
        int paramSlot = paramsStartIndex;
        // index of param in Object[]
        int paramArrayIndex = 0;
        // map slots to (params and index of param in Object[])
        for (DescriptorMember param : descriptor.params()) {
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
        for (DescriptorMember param : descriptor.params()) {
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
            prologue.add(new TypeInsnNode(CHECKCAST, (param.isArray() ? param : param.toNonePrimitive()).getType()));
            // if param was primitive convert it to primitive
            if (param.isPrimitive() && !param.isArray()) {
                char primitive = param.value().charAt(0);

                prologue.add(new MethodInsnNode(
                        INVOKEVIRTUAL,
                        TypeUtil.primitiveToClass(primitive),
                        TypeUtil.clsInstanceToPrimMethodName(primitive),
                        "()" + primitive
                ));
            }
            prologue.add(new VarInsnNode(TypeUtil.storeOpcodeForType(param), entry.getValue()));
        }

        for (ListIterator<AbstractInsnNode> it = methodNode.instructions.iterator(); it.hasNext(); ) {
            AbstractInsnNode insnNode = it.next();
            if (insnNode instanceof VarInsnNode loadInsn && InsnUtil.isLoad(insnNode)) {
                // check if param is written to, if yes: load from local, else load from Object[]
                if (writtenSlots.contains(loadInsn.var)) {
                    DescriptorMember param = paramBySlot.get(loadInsn.var);
                    methodNode.instructions.insertBefore(insnNode, new VarInsnNode(TypeUtil.loadOpcodeForType(param), localBySlot.get(loadInsn.var)));
                    it.remove();
                } else {
                    DescriptorMember param = paramBySlot.get(loadInsn.var);
                    if (param == null) {
                        continue;
                    }

                    InsnList loadFromArr = new InsnList();

                    // load Object[] containing params
                    loadFromArr.add(new VarInsnNode(ALOAD, paramsStartIndex));
                    // push index of param (-paramsStartIndex because first param will always be arr[0] but loadInsn.var will be 1 for virtual methods)
                    loadFromArr.add(InsnUtil.getIntPush(paramArrayIndexBySlot.get(loadInsn.var)));
                    // load param from array
                    loadFromArr.add(new InsnNode(AALOAD));
                    // cast to correct type
                    loadFromArr.add(new TypeInsnNode(CHECKCAST, (param.isArray() ? param : param.toNonePrimitive()).getType()));
                    // if param was primitive convert it to primitive
                    if (param.isPrimitive() && !param.isArray()) {
                        char primitive = param.value().charAt(0);

                        loadFromArr.add(new MethodInsnNode(
                                INVOKEVIRTUAL,
                                TypeUtil.primitiveToClass(primitive),
                                TypeUtil.clsInstanceToPrimMethodName(primitive),
                                "()" + primitive
                        ));
                    }

                    methodNode.instructions.insertBefore(insnNode, loadFromArr);
                    it.remove();
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
        }

        // insert prologue (storing params in locals if required) and update this methods descriptor
        methodNode.instructions.insertBefore(methodNode.instructions.getFirst(), prologue);
        methodNode.desc = "([Ljava/lang/Object;)" + descriptor.returnType().toDesc();
    }

    private Set<String> getMethodsTargetedByInvokedynamic(MethodNode methodNode) {
        Set<String> targeted = new HashSet<>();
        for (AbstractInsnNode insnNode : methodNode.instructions) {
            if (insnNode instanceof InvokeDynamicInsnNode indy) {
                for (Object bsmArg : indy.bsmArgs) {
                    if (bsmArg instanceof Handle handle) {
                        targeted.add(handle.getOwner() + handle.getName() + handle.getDesc());
                    }
                }
            }
        }

        return targeted;
    }
}
