package me.exeos.bytus.asmplus.utils;

import me.exeos.bytus.asmplus.descriptor.DescriptorMember;
import me.exeos.bytus.asmplus.descriptor.DescriptorParser;
import me.exeos.bytus.asmplus.descriptor.descriptors.method.MethodDescriptor;
import me.exeos.bytus.asmplus.jar.JarArchive;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import java.util.*;

public class MethodUtil implements Opcodes {

    public static int getMethodReturnOpcode(MethodNode methodNode) {
        return Type.getReturnType(methodNode.desc).getOpcode(Opcodes.IRETURN);
    }

    public static InsnList endMethodByThrow() {
        InsnList insnList = new InsnList();

        insnList.add(new TypeInsnNode(Opcodes.NEW, "java/lang/IllegalStateException"));
        insnList.add(new InsnNode(Opcodes.DUP));
        insnList.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, "java/lang/IllegalStateException", "<init>", "()V", false));
        insnList.add(new InsnNode(Opcodes.ATHROW));

        return insnList;
    }

    public static boolean hasAccess(MethodNode methodNode, int accessCode) {
        return AsmUtil.hasAccess(methodNode.access, accessCode);
    }

    public static int addParam(MethodNode to, DescriptorMember param) {
        return addParam(to, param, true);
    }

    /**
     * Adds a Parameter to the method, by updating its descriptor, remapping locals and increasing maxLocals
     *
     * @param to    Method to add param to
     * @param param Param to add to method
     * @return Local slot of the newly added Param
     */
    public static int addParam(MethodNode to, DescriptorMember param, boolean fixVars) {
        MethodDescriptor newMethodDesc = DescriptorParser.parseMethodDesc(to.desc).addParam(param);
        int newParamSlot = newMethodDesc.getAbsoluteSlot(
                newMethodDesc.getParams().size() - 1,
                MethodUtil.getParamSlotStart(to));

        // loop trough each insn and update target var if it collides
        if (fixVars) {
            InsnUtil.loop(to.instructions, insnNode -> {
                if (insnNode instanceof VarInsnNode varInsnNode && varInsnNode.var >= newParamSlot) {
                    varInsnNode.var++;
                } else if (insnNode instanceof IincInsnNode iincInsnNode && iincInsnNode.var >= newParamSlot) {
                    iincInsnNode.var++;
                }
            });
        }

        to.desc = newMethodDesc.toDesc();
        to.maxLocals += param.getSlotWidth();
        return newParamSlot;
    }

    public static int getNewParamSlot(MethodNode to, DescriptorMember param) {
        MethodDescriptor newMethodDesc = DescriptorParser.parseMethodDesc(to.desc).addParam(param);

        return newMethodDesc.getAbsoluteSlot(
                newMethodDesc.getParams().size() - 1,
                MethodUtil.getParamSlotStart(to));
    }

    public static void fixVars(InsnList container, int threshold) {
        InsnUtil.loop(container, insnNode -> {
            if (insnNode instanceof VarInsnNode varInsnNode && varInsnNode.var >= threshold) {
                varInsnNode.var++;
            } else if (insnNode instanceof IincInsnNode iincInsnNode && iincInsnNode.var >= threshold) {
                iincInsnNode.var++;
            }
        });
    }

    public static int getParamSlotStart(MethodNode methodNode) {
        return MethodUtil.hasAccess(methodNode, ACC_STATIC) ? 0 : 1;
    }

    public static Set<String> getInvokeDynamicTargets(MethodNode methodNode) {
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

    public static Map<ClassNode, Set<MethodNode>> getInvokeDynamicTargets(JarArchive jar, MethodNode methodNode) {
        Map<ClassNode, Set<MethodNode>> targeted = new HashMap<>();
        for (AbstractInsnNode insnNode : methodNode.instructions) {
            if (insnNode instanceof InvokeDynamicInsnNode indy) {
                for (Object bsmArg : indy.bsmArgs) {
                    if (bsmArg instanceof Handle handle) {
                        JarUtil.findClass(jar, handle.getOwner()).ifPresent(classNode -> {
                            targeted.putIfAbsent(classNode, new HashSet<>());
                            ClassUtil.findMethod(classNode, handle.getName(), handle.getDesc()).ifPresent(targeted.get(classNode)::add);
                        });
                    }
                }
            }
        }

        return targeted;
    }

    public static boolean isSpecial(MethodNode methodNode) {
        return methodNode.name.equals("<init>") || methodNode.name.equals("<clinit>");
    }
}
