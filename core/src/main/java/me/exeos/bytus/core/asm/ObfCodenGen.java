package me.exeos.bytus.core.asm;

import me.exeos.bytus.asmplus.utils.InsnUtil;
import me.exeos.bytus.core.transformer.extensions.MethodExtension;
import me.exeos.bytus.core.utils.RandomUtil;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;

public class ObfCodenGen implements Opcodes {
    
    public static final int SAFE_MIN = -9000000;
    public static final int SAFE_MAX = 9000000;

    public static InsnList getObfuscatedIntPush(int value, MethodExtension.SaltInfo saltInfo, MethodExtension.ParamObfInfo paramObfInfo) {
        return InsnUtil.getIntPushSalted(
                value,
                saltInfo.hasSalt(),
                saltInfo.getSaltOrDefault(),
                paramObfInfo.getArrayIndexBySlotOrSlot(saltInfo.getSaltSlotOrDefault()),
                paramObfInfo.hasParamObf(),
                paramObfInfo.getObjArrSlotOrDefault()
        );
    }

    public static InsnList getRandomJump(LabelNode to, MethodExtension.SaltInfo saltInfo, MethodExtension.ParamObfInfo paramObfInfo) {
        InsnList insns = new InsnList();
        switch (RandomUtil.getInt(0, 5)) {
            case 0 -> {
                insns.add(getObfuscatedIntPush(0, saltInfo, paramObfInfo));
                insns.add(new JumpInsnNode(IFEQ, to));
            }
            case 1 -> {
                insns.add(getObfuscatedIntPush(RandomUtil.getIntExcept(0), saltInfo, paramObfInfo));
                insns.add(new JumpInsnNode(IFNE, to));
            }
            case 2 -> {
                insns.add(getObfuscatedIntPush(RandomUtil.getInt(SAFE_MIN, -1), saltInfo, paramObfInfo));
                insns.add(new JumpInsnNode(IFLT, to));
            }
            case 3 -> {
                insns.add(getObfuscatedIntPush(RandomUtil.getInt(SAFE_MIN, 0), saltInfo, paramObfInfo));
                insns.add(new JumpInsnNode(IFLE, to));
            }
            case 4 -> {
                insns.add(getObfuscatedIntPush(RandomUtil.getInt(1, SAFE_MAX), saltInfo, paramObfInfo));
                insns.add(new JumpInsnNode(IFGT, to));
            }
            case 5 -> {
                insns.add(getObfuscatedIntPush(RandomUtil.getInt(0, SAFE_MAX), saltInfo, paramObfInfo));
                insns.add(new JumpInsnNode(IFGE, to));
            }
            case 6 -> {
                int random = RandomUtil.getInt(SAFE_MIN, SAFE_MAX);
                insns.add(getObfuscatedIntPush(random, saltInfo, paramObfInfo));
                insns.add(getObfuscatedIntPush(random + 1, saltInfo, paramObfInfo));
                insns.add(getObfuscatedIntPush(1, saltInfo, paramObfInfo));
                insns.add(new InsnNode(ISUB));
                insns.add(new JumpInsnNode(IF_ICMPEQ, to));
            }
            case 7 -> {
                int random = RandomUtil.getInt(SAFE_MIN, SAFE_MAX);
                insns.add(getObfuscatedIntPush(random, saltInfo, paramObfInfo));
                insns.add(getObfuscatedIntPush(RandomUtil.getIntExcept(random), saltInfo, paramObfInfo));
                insns.add(new JumpInsnNode(IF_ICMPNE, to));
            }
            case 8 -> {
                int random = RandomUtil.getInt();
                insns.add(getObfuscatedIntPush(random, saltInfo, paramObfInfo));
                insns.add(getObfuscatedIntPush(RandomUtil.getInt(random + 1, SAFE_MAX + 1), saltInfo, paramObfInfo));
                insns.add(new JumpInsnNode(IF_ICMPLT, to));
            }
            case 9 -> {
                int random = RandomUtil.getInt();
                insns.add(getObfuscatedIntPush(random, saltInfo, paramObfInfo));
                insns.add(getObfuscatedIntPush(RandomUtil.getInt(random, SAFE_MAX), saltInfo, paramObfInfo));
                insns.add(new JumpInsnNode(IF_ICMPLE, to));
            }
            case 10 -> {
                int random = RandomUtil.getInt();
                insns.add(getObfuscatedIntPush(random, saltInfo, paramObfInfo));
                insns.add(getObfuscatedIntPush(RandomUtil.getInt(SAFE_MIN - 1, random - 1), saltInfo, paramObfInfo));
                insns.add(new JumpInsnNode(IF_ICMPGT, to));
            }
            case 11 -> {
                int random = RandomUtil.getInt();
                insns.add(getObfuscatedIntPush(random, saltInfo, paramObfInfo));
                insns.add(getObfuscatedIntPush(RandomUtil.getInt(SAFE_MIN, random), saltInfo, paramObfInfo));
                insns.add(new JumpInsnNode(IF_ICMPGE, to));
            }
            default -> throw new IllegalStateException("This should never be reached");
        }
        return insns;
    }
}
