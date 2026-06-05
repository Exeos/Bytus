package me.exeos.bytus.asmplus.utils;

import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import java.util.HashSet;
import java.util.Set;

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
        return (methodNode.access & accessCode) != 0;
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
}
