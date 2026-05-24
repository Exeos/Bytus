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
            for (MethodNode methodNode : classNode.methods) {
                includedMethods.add(classNode.name + methodNode.name);
            }
        }

        for (ClassNode classNode : getIncludedClasses()) {
            for (MethodNode methodNode : classNode.methods) {
                int paramArrVarIndex = methodNode.maxLocals++;
                for (AbstractInsnNode insnNode : methodNode.instructions) {
                    if (insnNode instanceof MethodInsnNode methodInsnNode) {
                        if (!includedMethods.contains(classNode.name + methodInsnNode.name)) {
                            continue;
                        }

                        MethodDescriptor descriptor = DescriptorParser.parseMethodDesc(methodInsnNode.desc);
                        if (descriptor.args.isEmpty()) {
                            continue;
                        }

                        int paramArrLength = descriptor.args.size();
                        InsnList insn = new InsnList();

                        insn.add(InsnUtil.getIntPush(paramArrLength));
                        insn.add(new TypeInsnNode(ANEWARRAY, "java/lang/Object"));
                        insn.add(new VarInsnNode(ASTORE, paramArrVarIndex));

                        for (int i = 0; i < descriptor.args.size(); i++) {
                            DescriptorMember arg = descriptor.args.get(i);

                            insn.add(new VarInsnNode(ALOAD, paramArrVarIndex));
                            insn.add(new InsnNode(SWAP));
                            insn.add(InsnUtil.getIntPush(i));
                            insn.add(new InsnNode(SWAP));
                            if (arg.isPrimitive && !arg.isArray) {
                                String primClassName = TypeUtil.primitiveToClass(arg.value.toCharArray()[0]);

                                insn.add(new MethodInsnNode(INVOKESTATIC, primClassName, "valueOf", "(" + arg.value + ")L" + primClassName + ";"));
                            }
                            insn.add(new InsnNode(AASTORE));
                        }
                        insn.add(new VarInsnNode(ALOAD, paramArrVarIndex));

                        methodNode.instructions.insertBefore(insnNode, insn);
                        methodInsnNode.desc = "([Ljava/lang/Object;)" + descriptor.returnType.toDesc();
                    }
                }
            }
            for (MethodNode methodNode : classNode.methods) {
                // parse desc
                MethodDescriptor descriptor = DescriptorParser.parseMethodDesc(methodNode.desc);
                if (descriptor.args.isEmpty()) {
                    continue;
                }

                // if method is static param is stored at index 0 if not then that would be the class instance
                int paramArrIndex = MethodUtil.hasAccess(methodNode, ACC_STATIC) ? 0 : 1;

                Map<Integer, DescriptorMember> argMap = new HashMap<>();
                for (int i = 0; i < descriptor.args.size(); i++) {
                    argMap.put(i + paramArrIndex, descriptor.args.get(i));
                }

                // convert load insn
                if (!(methodNode.name.equals("main") && methodNode.desc.equals("([Ljava/lang/String;)V"))) {
                    methodNode.desc = "([Ljava/lang/Object;)" + (descriptor.returnType.isPrimitive ? descriptor.returnType.value : ("L" + descriptor.returnType.value + ";"));
                }

                AbstractInsnNode current = methodNode.instructions.getFirst();
                while (current != null) {
                    AbstractInsnNode next = current.getNext();

                    current = next;
                }
                for (ListIterator<AbstractInsnNode> it = methodNode.instructions.iterator(); it.hasNext(); ) {
                    AbstractInsnNode insnNode = it.next();
                    if (insnNode instanceof VarInsnNode varInsnNode && InsnUtil.isLoad(insnNode)) {
                        DescriptorMember argLoaded = argMap.get(varInsnNode.var);
                        if (argLoaded == null) {
                            continue;
                        }
                        InsnList loadInsn = new InsnList();

                        loadInsn.add(new VarInsnNode(ALOAD, paramArrIndex));
                        loadInsn.add(InsnUtil.getIntPush(varInsnNode.var - paramArrIndex));
                        loadInsn.add(new InsnNode(AALOAD));
                        loadInsn.add(new TypeInsnNode(CHECKCAST, argLoaded.toNonePrimitive().getType()));
                        if (argLoaded.isPrimitive && !argLoaded.isArray) {
                            loadInsn.add(new MethodInsnNode(
                                    INVOKEVIRTUAL,
                                    TypeUtil.primitiveToClass(argLoaded.value.toCharArray()[0]),
                                    TypeUtil.clsInstanceToPrimMethodName(argLoaded.value.toCharArray()[0]),
                                    "()" + argLoaded.value
                            ));
                        }

                        methodNode.instructions.insertBefore(insnNode, loadInsn);
                        it.remove();
                    }
                }
            }
        }
    }

}
