package me.exeos.bytus.asmplus.utils;

import me.exeos.bytus.asmplus.obfuscation.salt.SaltArithmetic;
import me.exeos.bytus.asmplus.obfuscation.salt.SaltSource;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.util.*;
import java.util.function.Consumer;

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

    public static boolean isTerminal(AbstractInsnNode insnNode) {
        return insnNode.getOpcode() == ATHROW || isReturn(insnNode);
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

    public static InsnList getIntPushSalted(int value, SaltSource... saltSources) {
        return RandomUtil.chance(70)
                ? SaltArithmetic.xorIntSaltPush(value, saltSources)
                : SaltArithmetic.rotateIntSaltPush(value, saltSources);
    }

    public static InsnList getIntPushList(int value) {
        InsnList push = new InsnList();
        push.add(getIntPush(value));

        return push;
    }

    public static AbstractInsnNode getIntPush(int value) {
        if (value >= Short.MIN_VALUE && value <= Short.MAX_VALUE)
            return getShortPush((short) value);

        return new LdcInsnNode(value);
    }

    public static AbstractInsnNode getLongPush(long value) {
        if (value == 0 || value == 1) {
            return new InsnNode((int) (LCONST_0 + value));
        }

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

    public static void addFromInsnList(InsnList source, List<AbstractInsnNode> target) {
        for (AbstractInsnNode insnNode : source) {
            target.add(insnNode);
        }
    }

    public static List<AbstractInsnNode> fromInsnList(InsnList from) {
        List<AbstractInsnNode> list = new ArrayList<>();
        for (AbstractInsnNode insnNode : from) {
            list.add(insnNode);
        }

        return list;
    }

    public static InsnList fromInsnList(List<AbstractInsnNode> from) {
        InsnList list = new InsnList();
        addToInsnList(from, list);

        return list;
    }

    /**
     * Safely loop trough instructions, you can insert, delete, etc without breaking iteration
     *
     * @param insnList
     * @param visitor
     */
    public static void loop(InsnList insnList, Consumer<AbstractInsnNode> visitor) {
        AbstractInsnNode current = insnList.getFirst();

        while (current != null) {
            AbstractInsnNode next = current.getNext();
            visitor.accept(current);
            current = next;
        }
    }

    public static void loop(List<AbstractInsnNode> insnList, Consumer<AbstractInsnNode> visitor) {
        AbstractInsnNode current = insnList.getFirst();

        while (current != null) {
            AbstractInsnNode next = current.getNext();
            visitor.accept(current);
            current = next;
        }
    }

    public static boolean isLambdaMetaFactory(InvokeDynamicInsnNode indy) {
        return indy.bsm.getOwner().equals("java/lang/invoke/LambdaMetafactory")
                && indy.bsm.getName().equals("metafactory")
                && indy.bsm.getDesc().equals("(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;");
    }

    public static Map<LabelNode, LabelNode> mapLabels(InsnList source) {
        Map<LabelNode, LabelNode> labelMap = new HashMap<>();
        for (AbstractInsnNode insnNode : source) {
            if (insnNode instanceof LabelNode labelNode) {
                labelMap.put(labelNode, new LabelNode());
            }
        }

        return labelMap;
    }

    public static InsnList copy(InsnList source) {
        InsnList copy = new InsnList();
        Map<LabelNode, LabelNode> labelMap = mapLabels(source);

        for (AbstractInsnNode insnNode : source) {
            copy.add(insnNode.clone(labelMap));
        }

        return copy;
    }

    public static boolean isWide(int opcode) {
        return switch (opcode) {
            case Opcodes.LLOAD, Opcodes.LSTORE, Opcodes.DLOAD, Opcodes.DSTORE -> true;
            default -> false;
        };
    }
}
