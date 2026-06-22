package me.exeos.bytus.core.asm;

import me.exeos.bytus.asmplus.utils.InsnUtil;
import me.exeos.bytus.core.transformer.extensions.ClassExtension;
import me.exeos.bytus.core.transformer.extensions.MethodExtension;
import me.exeos.bytus.core.utils.RandomUtil;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

public class ObfCodenGen implements Opcodes {
    
    public static final int SAFE_MIN = -9000000;
    public static final int SAFE_MAX = 9000000;

    public static InsnList getObfuscatedIntPush(
            int value, 
            ClassExtension.ClassSaltInfo classSaltInfo, 
            MethodExtension.MethodSaltInfo methodSaltInfo, 
            MethodExtension.ParamObfInfo paramObfInfo
    ) {
        InsnList idk = new InsnList();

        InsnList methodSaltPush = new InsnList();
        if (methodSaltInfo.hasSalt()) {
            int saltSlot = paramObfInfo.getArrayIndexBySlotOrSlot(methodSaltInfo.getSaltSlotOrDefault());
            if (paramObfInfo.hasParamObf()) {
                methodSaltPush.add(new VarInsnNode(ALOAD, paramObfInfo.getObjArrSlotOrDefault()));
                methodSaltPush.add(InsnUtil.getIntPush(saltSlot));
                methodSaltPush.add(new InsnNode(AALOAD));
                methodSaltPush.add(new TypeInsnNode(CHECKCAST, "java/lang/Integer"));
                methodSaltPush.add(new MethodInsnNode(INVOKEVIRTUAL, "java/lang/Integer", "intValue", "()I"));
            } else {
                methodSaltPush.add(new VarInsnNode(ILOAD, saltSlot));
            }
        }

        InsnList classSaltPush = new InsnList();
        if (classSaltInfo.hasSalt()) {
            classSaltPush.add(new FieldInsnNode(GETSTATIC, classSaltInfo.getOwner(), classSaltInfo.getName(), classSaltInfo.getDesc()));
        }

        if (methodSaltInfo.hasSalt() && classSaltInfo.hasSalt()) {
            int first = RandomUtil.getInt(SAFE_MIN, SAFE_MAX);
            int second = RandomUtil.getInt(SAFE_MIN, SAFE_MAX);
            int obf = value + first + second;

            idk.add(InsnUtil.getIntPush(obf));
            idk.add(InsnUtil.getIntPushSalted(first, true, methodSaltInfo.getSalt(), methodSaltPush));
            idk.add(new InsnNode(ISUB));
            idk.add(InsnUtil.getIntPushSalted(second, true, classSaltInfo.getSalt(), classSaltPush));
            idk.add(new InsnNode(ISUB));
        } else if (methodSaltInfo.hasSalt()) {
            idk.add(InsnUtil.getIntPushSalted(value, true, methodSaltInfo.getSalt(), methodSaltPush));
        } else if (classSaltInfo.hasSalt()) {
            idk.add(InsnUtil.getIntPushSalted(value, true, classSaltInfo.getSalt(), classSaltPush));
        } else {
            idk.add(InsnUtil.getIntPush(value));
        }
        return idk;
    }

    public static InsnList getRandomJump(
            LabelNode to,
            ClassExtension.ClassSaltInfo classSaltInfo,
            MethodExtension.MethodSaltInfo methodSaltInfo,
            MethodExtension.ParamObfInfo paramObfInfo
    ) {
        InsnList insns = new InsnList();
        switch (RandomUtil.getInt(0, 5)) {
            case 0 -> {
                insns.add(getObfuscatedIntPush(0, classSaltInfo, methodSaltInfo, paramObfInfo));
                insns.add(new JumpInsnNode(IFEQ, to));
            }
            case 1 -> {
                insns.add(getObfuscatedIntPush(RandomUtil.getIntExcept(0), classSaltInfo, methodSaltInfo, paramObfInfo));
                insns.add(new JumpInsnNode(IFNE, to));
            }
            case 2 -> {
                insns.add(getObfuscatedIntPush(RandomUtil.getInt(SAFE_MIN, -1), classSaltInfo, methodSaltInfo, paramObfInfo));
                insns.add(new JumpInsnNode(IFLT, to));
            }
            case 3 -> {
                insns.add(getObfuscatedIntPush(RandomUtil.getInt(SAFE_MIN, 0), classSaltInfo, methodSaltInfo, paramObfInfo));
                insns.add(new JumpInsnNode(IFLE, to));
            }
            case 4 -> {
                insns.add(getObfuscatedIntPush(RandomUtil.getInt(1, SAFE_MAX), classSaltInfo, methodSaltInfo, paramObfInfo));
                insns.add(new JumpInsnNode(IFGT, to));
            }
            case 5 -> {
                insns.add(getObfuscatedIntPush(RandomUtil.getInt(0, SAFE_MAX), classSaltInfo, methodSaltInfo, paramObfInfo));
                insns.add(new JumpInsnNode(IFGE, to));
            }
            case 6 -> {
                int random = RandomUtil.getInt(SAFE_MIN, SAFE_MAX);
                insns.add(getObfuscatedIntPush(random, classSaltInfo, methodSaltInfo, paramObfInfo));
                insns.add(getObfuscatedIntPush(random + 1, classSaltInfo, methodSaltInfo, paramObfInfo));
                insns.add(getObfuscatedIntPush(1, classSaltInfo, methodSaltInfo, paramObfInfo));
                insns.add(new InsnNode(ISUB));
                insns.add(new JumpInsnNode(IF_ICMPEQ, to));
            }
            case 7 -> {
                int random = RandomUtil.getInt(SAFE_MIN, SAFE_MAX);
                insns.add(getObfuscatedIntPush(random, classSaltInfo, methodSaltInfo, paramObfInfo));
                insns.add(getObfuscatedIntPush(RandomUtil.getIntExcept(random), classSaltInfo, methodSaltInfo, paramObfInfo));
                insns.add(new JumpInsnNode(IF_ICMPNE, to));
            }
            case 8 -> {
                int random = RandomUtil.getInt();
                insns.add(getObfuscatedIntPush(random, classSaltInfo, methodSaltInfo, paramObfInfo));
                insns.add(getObfuscatedIntPush(RandomUtil.getInt(random + 1, SAFE_MAX + 1), classSaltInfo, methodSaltInfo, paramObfInfo));
                insns.add(new JumpInsnNode(IF_ICMPLT, to));
            }
            case 9 -> {
                int random = RandomUtil.getInt();
                insns.add(getObfuscatedIntPush(random, classSaltInfo, methodSaltInfo, paramObfInfo));
                insns.add(getObfuscatedIntPush(RandomUtil.getInt(random, SAFE_MAX), classSaltInfo, methodSaltInfo, paramObfInfo));
                insns.add(new JumpInsnNode(IF_ICMPLE, to));
            }
            case 10 -> {
                int random = RandomUtil.getInt();
                insns.add(getObfuscatedIntPush(random, classSaltInfo, methodSaltInfo, paramObfInfo));
                insns.add(getObfuscatedIntPush(RandomUtil.getInt(SAFE_MIN - 1, random - 1), classSaltInfo, methodSaltInfo, paramObfInfo));
                insns.add(new JumpInsnNode(IF_ICMPGT, to));
            }
            case 11 -> {
                int random = RandomUtil.getInt();
                insns.add(getObfuscatedIntPush(random, classSaltInfo, methodSaltInfo, paramObfInfo));
                insns.add(getObfuscatedIntPush(RandomUtil.getInt(SAFE_MIN, random), classSaltInfo, methodSaltInfo, paramObfInfo));
                insns.add(new JumpInsnNode(IF_ICMPGE, to));
            }
            default -> throw new IllegalStateException("This should never be reached");
        }
        return insns;
    }
}
