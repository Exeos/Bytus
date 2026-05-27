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
        // owner name desc
        Set<String> exclusionsByDesc = new HashSet<>();
        // owner name
        Set<String> excludedAncestorByName = new HashSet<>();

        for (ClassNode classNode : getIncludedClasses()) {
            Map<String, Integer> declarationMap = new HashMap<>();
            for (MethodNode methodNode : classNode.methods) {
                // exclude methods in same class with same name as signature would be the same after transformation
                declarationMap.merge(classNode.name + methodNode.name, 1, (oldVal, _) -> oldVal + 1);
                if (declarationMap.get(classNode.name + methodNode.name) > 1) {
                    excludedAncestorByName.add(classNode.name + methodNode.name);
                }

                // exclude all interfaces
                if ((classNode.access & ACC_INTERFACE) != 0) {
                    excludedAncestorByName.add(classNode.name + methodNode.name);
                }

                exclusionsByDesc.addAll(getMethodsTargetedByInvokedynamic(methodNode));
            }
        }

        for (ClassNode classNode : getIncludedClasses()) {
            expandExclusions(exclusionsByDesc, excludedAncestorByName, classNode);
        }

        for (ClassNode classNode : getIncludedClasses()) {
            for (MethodNode methodNode : classNode.methods) {
                convertParamPassing(classNode, methodNode, exclusionsByDesc, excludedAncestorByName);
            }

            for (MethodNode methodNode : classNode.methods) {
                convertParamUsage(classNode, methodNode, exclusionsByDesc, excludedAncestorByName);
            }
        }
    }


    private void expandExclusions(Set<String> exclusionsByDesc, Set<String> exclusionsByName, ClassNode classNode) {
        // Collect all ancestor methods that are excluded, so we can exclude overrides in this class.
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

        // Apply exclusions to this class if it overrides an excluded ancestor method.
        for (MethodNode m : classNode.methods) {
            if (excludedAncestorByDesc.contains(m.name + m.desc)) {
                exclusionsByDesc.add(classNode.name + m.name + m.desc);
            }
            if (excludedAncestorByName.contains(m.name)) {
                exclusionsByName.add(classNode.name + m.name);
            }

            // not override but calls method in super class
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

    private void convertParamPassing(ClassNode classNode, MethodNode methodNode, Set<String> excludedMethods, Set<String> excludedMethods2) {
        int paramArrVarIndex = methodNode.maxLocals++;
        for (AbstractInsnNode insnNode : methodNode.instructions) {
            if (insnNode instanceof MethodInsnNode methodInsnNode) {
                if (excludedMethods.contains(methodInsnNode.owner + methodInsnNode.name + methodInsnNode.desc)
                        || excludedMethods2.contains(methodInsnNode.owner + methodInsnNode.name)
                        || !getJar().classes().containsKey(methodInsnNode.owner)
                ) {
                    continue;
                }

                MethodDescriptor descriptor = DescriptorParser.parseMethodDesc(methodInsnNode.desc);
                if (descriptor.params.isEmpty()) {
                    continue;
                }

                int[] tmpLocal = new int[descriptor.params.size()];
                for (int i = 0; i < descriptor.params.size(); i++) {
                    DescriptorMember p = descriptor.params.get(i);
                    tmpLocal[i] = methodNode.maxLocals;
                    methodNode.maxLocals += p.getSlotWidth();
                }

                int paramArrLength = descriptor.params.size();
                InsnList arrBuilder = new InsnList();

                // store args into temps in reverse (stack top is last argument)
                for (int i = descriptor.params.size() - 1; i >= 0; i--) {
                    DescriptorMember p = descriptor.params.get(i);
                    arrBuilder.add(new VarInsnNode(TypeUtil.storeOpcodeForType(p), tmpLocal[i]));
                }


                arrBuilder.add(InsnUtil.getIntPush(paramArrLength));
                arrBuilder.add(new TypeInsnNode(ANEWARRAY, "java/lang/Object"));
                arrBuilder.add(new VarInsnNode(ASTORE, paramArrVarIndex));

                for (int i = 0; i < descriptor.params.size(); i++) {
                    DescriptorMember param = descriptor.params.get(i);

                    arrBuilder.add(new VarInsnNode(ALOAD, paramArrVarIndex));
                    arrBuilder.add(InsnUtil.getIntPush(i));
                    arrBuilder.add(new VarInsnNode(TypeUtil.loadOpcodeForType(param), tmpLocal[i]));
                    if (param.isPrimitive && !param.isArray) {
                        String primClassName = TypeUtil.primitiveToClass(param.value.toCharArray()[0]);

                        arrBuilder.add(new MethodInsnNode(INVOKESTATIC, primClassName, "valueOf", "(" + param.value + ")L" + primClassName + ";"));
                    }
                    arrBuilder.add(new InsnNode(AASTORE));
                }
                arrBuilder.add(new VarInsnNode(ALOAD, paramArrVarIndex));

                methodNode.instructions.insertBefore(insnNode, arrBuilder);
                methodInsnNode.desc = "([Ljava/lang/Object;)" + descriptor.returnType.toDesc();
            }
        }
    }

    private void convertParamUsage(ClassNode ownerNode, MethodNode methodNode, Set<String> excludedMethods, Set<String> excludedMethods2) {
        if (excludedMethods.contains(ownerNode.name + methodNode.name + methodNode.desc)
                || excludedMethods2.contains(ownerNode.name + methodNode.name)
        ) {
            return;
        }

        MethodDescriptor descriptor = DescriptorParser.parseMethodDesc(methodNode.desc);
        if (descriptor.params.isEmpty()) {
            return;
        }

        if (!(ownerNode.name.equals(mainClassName) && methodNode.name.equals("main") && methodNode.desc.equals("([Ljava/lang/String;)V"))) {
            methodNode.desc = "([Ljava/lang/Object;)" + descriptor.returnType.toDesc();
        }

        int paramsStartIndex = MethodUtil.hasAccess(methodNode, ACC_STATIC) ? 0 : 1;

        Map<Integer, DescriptorMember> paramIndexMap = new HashMap<>();
        Map<Integer, Integer> slotArrayIndexMap = new HashMap<>();
        Map<Integer, Integer> oldSlotNewLocalMap = new HashMap<>();

        Set<Integer> writeParams = new HashSet<>();

        int paramSlot = paramsStartIndex;
        int arrayIndex = 0;
        for (DescriptorMember param : descriptor.params) {
            paramIndexMap.put(paramSlot, param);
            slotArrayIndexMap.put(paramSlot, arrayIndex);

            paramSlot += param.getSlotWidth();
            arrayIndex++;
        }

        for (AbstractInsnNode insnNode : methodNode.instructions) {
            if (insnNode instanceof VarInsnNode varInsnNode && InsnUtil.isStore(insnNode)) {
                if (slotArrayIndexMap.containsKey(varInsnNode.var)) {
                    writeParams.add(varInsnNode.var);
                }
            } else if (insnNode instanceof IincInsnNode iincInsnNode && slotArrayIndexMap.containsKey(iincInsnNode.var)) {
                writeParams.add(iincInsnNode.var);
            }
        }

        paramSlot = paramsStartIndex;
        for (DescriptorMember param : descriptor.params) {
            if (writeParams.contains(paramSlot)) {
                oldSlotNewLocalMap.put(paramSlot, methodNode.maxLocals);
                methodNode.maxLocals += param.getSlotWidth();
            }

            paramSlot += param.getSlotWidth();
        }

        InsnList prologue = new InsnList();
        for (Map.Entry<Integer, Integer> entry : oldSlotNewLocalMap.entrySet()) {
            DescriptorMember param = paramIndexMap.get(entry.getKey());

            prologue.add(new VarInsnNode(ALOAD, paramsStartIndex));
            prologue.add(InsnUtil.getIntPush(slotArrayIndexMap.get(entry.getKey())));
            prologue.add(new InsnNode(AALOAD));
            prologue.add(new TypeInsnNode(CHECKCAST, (param.isArray ? param : param.toNonePrimitive()).getType()));
            // if param was primitive convert it to primitive
            if (param.isPrimitive && !param.isArray) {
                prologue.add(new MethodInsnNode(
                        INVOKEVIRTUAL,
                        TypeUtil.primitiveToClass(param.value.toCharArray()[0]),
                        TypeUtil.clsInstanceToPrimMethodName(param.value.toCharArray()[0]),
                        "()" + param.value
                ));
            }
            prologue.add(new VarInsnNode(TypeUtil.storeOpcodeForType(param), entry.getValue()));
        }

        for (ListIterator<AbstractInsnNode> it = methodNode.instructions.iterator(); it.hasNext(); ) {
            AbstractInsnNode insnNode = it.next();
            if (insnNode instanceof VarInsnNode loadInsn && InsnUtil.isLoad(insnNode)) {
                if (writeParams.contains(loadInsn.var)) {
                    DescriptorMember param = paramIndexMap.get(loadInsn.var);
                    methodNode.instructions.insertBefore(insnNode, new VarInsnNode(TypeUtil.loadOpcodeForType(param), oldSlotNewLocalMap.get(loadInsn.var)));
                    it.remove();
                } else {
                    DescriptorMember param = paramIndexMap.get(loadInsn.var);
                    if (param == null) {
                        continue;
                    }

                    InsnList loadFromArr = new InsnList();

                    // load Object[] containing params
                    loadFromArr.add(new VarInsnNode(ALOAD, paramsStartIndex));
                    // push index of param (-paramsStartIndex because first param will always be arr[0] but loadInsn.var will be 1 for virtual methods)
                    loadFromArr.add(InsnUtil.getIntPush(slotArrayIndexMap.get(loadInsn.var)));
                    // load param from array
                    loadFromArr.add(new InsnNode(AALOAD));
                    // cast to correct type
                    loadFromArr.add(new TypeInsnNode(CHECKCAST, (param.isArray ? param : param.toNonePrimitive()).getType()));
                    // if param was primitive convert it to primitive
                    if (param.isPrimitive && !param.isArray) {
                        loadFromArr.add(new MethodInsnNode(
                                INVOKEVIRTUAL,
                                TypeUtil.primitiveToClass(param.value.toCharArray()[0]),
                                TypeUtil.clsInstanceToPrimMethodName(param.value.toCharArray()[0]),
                                "()" + param.value
                        ));
                    }

                    methodNode.instructions.insertBefore(insnNode, loadFromArr);
                    it.remove();
                }
            } else if (insnNode instanceof VarInsnNode varInsnNode && InsnUtil.isStore(insnNode)) {
                if (writeParams.contains(varInsnNode.var)) {
                    varInsnNode.var = oldSlotNewLocalMap.get(varInsnNode.var);
                }
            } else if (insnNode instanceof IincInsnNode iincInsnNode && writeParams.contains(iincInsnNode.var)) {
                iincInsnNode.var = oldSlotNewLocalMap.get(iincInsnNode.var);
            }
        }
        methodNode.instructions.insertBefore(methodNode.instructions.getFirst(), prologue);
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
