package me.exeos.bytus.asmplus.utils;

import me.exeos.bytus.asmplus.descriptor.DescriptorMember;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.util.List;
import java.util.Optional;

public class InsnUtil implements Opcodes {

    public static boolean isStore(AbstractInsnNode insnNode) {
        return insnNode.getOpcode() >= ISTORE && insnNode.getOpcode() <= ASTORE;
    }

    public static boolean isLoad(AbstractInsnNode insnNode) {
        return insnNode.getOpcode() >= ILOAD && insnNode.getOpcode() <= ALOAD;
    }

    public static boolean isReturn(AbstractInsnNode insnNode) {
        return insnNode.getOpcode() >= Opcodes.IRETURN && insnNode.getOpcode() <= Opcodes.RETURN;
    }

    public static boolean isBranch(AbstractInsnNode insnNode) {
        return insnNode instanceof JumpInsnNode;
    }

    public static boolean isIConstPush(AbstractInsnNode insnNode) {
        return isIConstPush(insnNode.getOpcode());
    }

    public static boolean isIConstPush(int opcode) {
        return opcode >= ICONST_M1 && opcode <= ICONST_5;
    }

    public static boolean isIntPush(AbstractInsnNode insnNode) {
        return isIConstPush(insnNode) || insnNode.getOpcode() == BIPUSH || insnNode.getOpcode() == SIPUSH ||
                (insnNode instanceof LdcInsnNode ldcInsnNode && ldcInsnNode.cst instanceof Integer);
    }

    public static AbstractInsnNode getIConstPush(int value) {
        if (value < -1 || value > 5)
            throw new IllegalStateException("Value: " + value + " isn't in required bound: -1 to +5");

        return new InsnNode(ICONST_0 + value);
    }

    public static AbstractInsnNode getBytePush(byte value) {
        if (isIConstPush(ICONST_0 + value)) {
            return getIConstPush(value);
        }

        return new IntInsnNode(BIPUSH, value);
    }

    public static AbstractInsnNode getShortPush(short value) {
        if (isIConstPush(ICONST_0 + value)) {
            return getIConstPush(value);
        }

        if (value >= Byte.MIN_VALUE && value <= Byte.MAX_VALUE) {
            return getBytePush((byte) value);
        }

        return new IntInsnNode(SIPUSH, value);
    }

    public static AbstractInsnNode getIntPush(int value) {
        if (value >= Short.MIN_VALUE && value <= Short.MAX_VALUE)
            return getShortPush((short) value);

        return new LdcInsnNode(value);
    }

    public static Optional<Integer> getIntValue(AbstractInsnNode insnNode) {
        if (isIConstPush(insnNode.getOpcode())) {
            return Optional.of(insnNode.getOpcode() - 3);
        }

        switch (insnNode.getOpcode()) {
            case BIPUSH, SIPUSH -> {
                return Optional.of(((IntInsnNode) insnNode).operand);
            }
            case LDC -> {
                LdcInsnNode ldcInsnNode = (LdcInsnNode) insnNode;
                if (ldcInsnNode.cst instanceof Integer value) {
                    return Optional.of(value);
                }
            }
        }

        return Optional.empty();
    }

    public static void addToInsnList(List<AbstractInsnNode> source, InsnList target) {
        for (AbstractInsnNode insnNode : source) {
            target.add(insnNode);
        }
    }
}
