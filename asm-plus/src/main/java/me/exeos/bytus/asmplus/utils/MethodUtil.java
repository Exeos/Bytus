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
     * @param remapLocals Should instructions loading locals be remapped
     * @return Local slot of the newly added Param
     */
    public static int addParam(MethodNode to, DescriptorMember param, boolean remapLocals) {
        MethodDescriptor newMethodDesc = DescriptorParser.parseMethodDesc(to.desc).addParam(param);
        int newParamSlot = newMethodDesc.getAbsoluteSlot(
                newMethodDesc.getParams().size() - 1,
                MethodUtil.getLocalsOffset(to));

        // loop trough each insn and update target var if it collides
        if (remapLocals) {
            remapLocals(to.instructions, newParamSlot);
        }

        to.desc = newMethodDesc.toDesc();
        to.maxLocals += param.getSlotWidth();
        return newParamSlot;
    }

    public static int getNewParamSlot(MethodNode to, DescriptorMember param) {
        MethodDescriptor newMethodDesc = DescriptorParser.parseMethodDesc(to.desc).addParam(param);

        return newMethodDesc.getAbsoluteSlot(
                newMethodDesc.getParams().size() - 1,
                MethodUtil.getLocalsOffset(to));
    }

    /**
     * Remaps indexes of locals so they don't collide with newly added params
     * @param container Instructions to remap
     * @param threshold The index threshold marking the end of the method params
     */
    public static void remapLocals(InsnList container, int threshold) {
        InsnUtil.loop(container, insnNode -> {
            if (insnNode instanceof VarInsnNode varInsnNode && varInsnNode.var >= threshold) {
                varInsnNode.var++;
            } else if (insnNode instanceof IincInsnNode iincInsnNode && iincInsnNode.var >= threshold) {
                iincInsnNode.var++;
            }
        });
    }

    /**
     * Returns the start slot of the locals in a method
     * @param methodNode Method node to get the offset for
     * @return The start slot of the locals in a method
     */
    public static int getLocalsOffset(MethodNode methodNode) {
        return MethodUtil.hasAccess(methodNode, ACC_STATIC) ? 0 : 1;
    }

    /**
     * Finds all methods targeted by invokedynamic insn in the provided methods instructions
     * @param methodNode The MethodNode to scan for indy instructions
     * @return Set of owner + name + desc of targeted methods
     */
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

    /**
     * Finds all methods targeted by invokedynamic and maps them into their owner classes
     * @param jar JarArchive containing the relevant classes
     * @param methodNode The MethodNode to scan for indy instructions
     * @return Map mapping classes and theirs methods if that method is targeted by invoke dynamic
     */
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

    public static void removeAllInsn(MethodNode methodNode) {
        for (AbstractInsnNode insnNode : methodNode.instructions) {
            methodNode.instructions.remove(insnNode);
        }
    }
}
