package me.exeos.bytus.asmplus.utils;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.MethodNode;

public class MethodUtil {

    public static int getMethodReturnOpcode(MethodNode methodNode) {
        return Type.getReturnType(methodNode.desc).getOpcode(Opcodes.IRETURN);
    }
}
