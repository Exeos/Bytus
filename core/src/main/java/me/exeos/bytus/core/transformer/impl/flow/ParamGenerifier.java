package me.exeos.bytus.core.transformer.impl.flow;

import me.exeos.bytus.asmplus.descriptor.DescriptorMember;
import me.exeos.bytus.asmplus.descriptor.DescriptorParser;
import me.exeos.bytus.asmplus.descriptor.descriptors.method.MethodDescriptor;
import me.exeos.bytus.asmplus.jar.JarArchive;
import me.exeos.bytus.asmplus.utils.InsnUtil;
import me.exeos.bytus.asmplus.utils.MethodUtil;
import me.exeos.bytus.asmplus.utils.TypeUtil;
import me.exeos.bytus.core.transformer.Transformer;
import me.exeos.bytus.core.transformer.TransformerPipeline;
import org.objectweb.asm.tree.*;

import java.util.*;

public class ParamGenerifier extends Transformer {

    public ParamGenerifier(JarArchive jar, List<String> exclusions, List<String> inclusions) {
        super(jar, exclusions, inclusions);
    }

    @Override
    public void transform(TransformerPipeline pipeline) {
        Set<String> includedMethods = new HashSet<>();
        for (ClassNode classNode : getIncludedClasses()) {
            if (classNode.name.contains("Enum")) {
                System.out.println();
            }
            for (MethodNode methodNode : classNode.methods) {
                includedMethods.add(classNode.name + methodNode.name);
            }
        }

        for (ClassNode classNode : getIncludedClasses()) {
            for (MethodNode methodNode : classNode.methods) {
                convertParamPassing(classNode, methodNode, includedMethods);
            }

            for (MethodNode methodNode : classNode.methods) {
                convertParamUsage(methodNode);
            }
        }
    }

    private void convertParamPassing(ClassNode classNode, MethodNode methodNode, Set<String> includedMethods) {
        int paramArrVarIndex = methodNode.maxLocals++;
        for (AbstractInsnNode insnNode : methodNode.instructions) {
            if (insnNode instanceof InvokeDynamicInsnNode invokeDynamicInsnNode) {
                MethodDescriptor descriptor = DescriptorParser.parseMethodDesc(invokeDynamicInsnNode.desc);
                if (descriptor.params.isEmpty()) {
                    continue;
                }

                int paramArrLength = descriptor.params.size();
                InsnList arrBuilder = new InsnList();

                arrBuilder.add(InsnUtil.getIntPush(paramArrLength));
                arrBuilder.add(new TypeInsnNode(ANEWARRAY, "java/lang/Object"));
                arrBuilder.add(new VarInsnNode(ASTORE, paramArrVarIndex));

                // reverse order because stack top is the last param
                for (int i = descriptor.params.size() - 1; i >= 0; i--) {
                    DescriptorMember param = descriptor.params.get(i);

                    arrBuilder.add(new VarInsnNode(ALOAD, paramArrVarIndex));
                    arrBuilder.add(new InsnNode(SWAP));
                    arrBuilder.add(InsnUtil.getIntPush(i));
                    arrBuilder.add(new InsnNode(SWAP));
                    if (param.isPrimitive && !param.isArray) {
                        String primClassName = TypeUtil.primitiveToClass(param.value.toCharArray()[0]);

                        arrBuilder.add(new MethodInsnNode(INVOKESTATIC, primClassName, "valueOf", "(" + param.value + ")L" + primClassName + ";"));
                    }
                    arrBuilder.add(new InsnNode(AASTORE));
                }
                arrBuilder.add(new VarInsnNode(ALOAD, paramArrVarIndex));

                methodNode.instructions.insertBefore(insnNode, arrBuilder);
                invokeDynamicInsnNode.desc = "([Ljava/lang/Object;)" + descriptor.returnType.toDesc();
            }
            if (insnNode instanceof MethodInsnNode methodInsnNode) {
                if (!includedMethods.contains(methodInsnNode.owner + methodInsnNode.name)) {
                    continue;
                }

                MethodDescriptor descriptor = DescriptorParser.parseMethodDesc(methodInsnNode.desc);
                if (descriptor.params.isEmpty()) {
                    continue;
                }

                int paramArrLength = descriptor.params.size();
                InsnList arrBuilder = new InsnList();

                arrBuilder.add(InsnUtil.getIntPush(paramArrLength));
                arrBuilder.add(new TypeInsnNode(ANEWARRAY, "java/lang/Object"));
                arrBuilder.add(new VarInsnNode(ASTORE, paramArrVarIndex));

                // reverse order because stack top is the last param
                for (int i = descriptor.params.size() - 1; i >= 0; i--) {
                    DescriptorMember param = descriptor.params.get(i);

                    arrBuilder.add(new VarInsnNode(ALOAD, paramArrVarIndex));
                    arrBuilder.add(new InsnNode(SWAP));
                    arrBuilder.add(InsnUtil.getIntPush(i));
                    arrBuilder.add(new InsnNode(SWAP));
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

    private void convertParamUsage(MethodNode methodNode) {
        MethodDescriptor descriptor = DescriptorParser.parseMethodDesc(methodNode.desc);
        if (descriptor.params.isEmpty()) {
            return;
        }

        // change this methods description to use Object[] as the only param
        if (!(methodNode.name.equals("main") && methodNode.desc.equals("([Ljava/lang/String;)V"))) {
            methodNode.desc = "([Ljava/lang/Object;)" + descriptor.returnType.toDesc();
        }

        int paramsStartIndex = MethodUtil.hasAccess(methodNode, ACC_STATIC) ? 0 : 1;

        Map<Integer, DescriptorMember> paramIndexMap = new HashMap<>();
        for (int i = 0; i < descriptor.params.size(); i++) {
            paramIndexMap.put(i + paramsStartIndex, descriptor.params.get(i));
        }

        for (ListIterator<AbstractInsnNode> it = methodNode.instructions.iterator(); it.hasNext(); ) {
            AbstractInsnNode insnNode = it.next();
            if (insnNode instanceof VarInsnNode loadInsn && InsnUtil.isLoad(insnNode)) {
                DescriptorMember param = paramIndexMap.get(loadInsn.var);
                if (param == null) {
                    continue;
                }

                InsnList loadFromArr = new InsnList();

                // load Object[] containing params
                loadFromArr.add(new VarInsnNode(ALOAD, paramsStartIndex));
                // push index of param (-paramsStartIndex because first param will always be arr[0] but loadInsn.var will be 1 for virtual methods)
                loadFromArr.add(InsnUtil.getIntPush(loadInsn.var - paramsStartIndex));
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
        }
    }

}
