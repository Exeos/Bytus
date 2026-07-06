package me.exeos.bytus.core.asm;

import me.exeos.bytus.asmplus.InsnFactory;
import me.exeos.bytus.asmplus.obfuscation.salt.SaltSource;
import me.exeos.bytus.asmplus.utils.InsnUtil;
import me.exeos.bytus.core.transformer.extensions.ClassExtension;
import me.exeos.bytus.core.transformer.extensions.MethodExtension;
import me.exeos.bytus.core.utils.RandomUtil;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

/**
 * Class handling the heavy lifting. Should be accessed trough extensions
 */
public class ObfCodenGen implements Opcodes {

    public static final int SAFE_MIN = -9000000;
    public static final int SAFE_MAX = 9000000;

    public static InsnList getObfuscatedIntPush(
            int value,
            boolean isClinit,
            ClassExtension.ClassSaltInfo classSaltInfo,
            MethodExtension.MethodSaltInfo methodSaltInfo,
            MethodExtension.ParamObfInfo paramObfInfo
    ) {
        boolean hasMethodSalt = methodSaltInfo.hasSalt();
        boolean hasClassSalt = classSaltInfo.hasSalt() && (!isClinit || classSaltInfo.hasPreInitializingSalts());

        if (!hasMethodSalt && !hasClassSalt) {
            return InsnUtil.getIntPushList(value);
        }

        SaltSource methodSalt = new SaltSource(methodSaltInfo.getSaltOrDefault(), getMethodSaltPush(methodSaltInfo, paramObfInfo));
        SaltSource classSalt;
        if (classSaltInfo.hasPreInitializingSalts() && isClinit) {
            ClassExtension.ClassSaltInfo randomPre = RandomUtil.getRandomEntry(classSaltInfo.getPreInitingSalts());
            classSalt = new SaltSource(randomPre.getSaltOrDefault(), getClassSaltPush(randomPre));
        } else {
            classSalt = new SaltSource(classSaltInfo.getSaltOrDefault(), getClassSaltPush(classSaltInfo));
        }

        if (hasMethodSalt && hasClassSalt) {
            return InsnUtil.getIntPushSalted(value, methodSalt, classSalt);
        }

        if (hasMethodSalt) {
            return InsnUtil.getIntPushSalted(value, methodSalt);
        }

        return InsnUtil.getIntPushSalted(value, classSalt);
    }

    private static InsnFactory getMethodSaltPush(MethodExtension.MethodSaltInfo methodSaltInfo, MethodExtension.ParamObfInfo paramObfInfo) {
        return () -> {
            InsnList push = new InsnList();

            int saltSlot = paramObfInfo.getArrayIndexBySlotOrSlot(methodSaltInfo.getSaltSlotOrDefault());
            if (paramObfInfo.hasParamObf()) {
                push.add(new VarInsnNode(ALOAD, paramObfInfo.getObjArrSlotOrDefault()));
                push.add(InsnUtil.getIntPush(saltSlot));
                push.add(new InsnNode(AALOAD));
                push.add(new TypeInsnNode(CHECKCAST, "java/lang/Integer"));
                push.add(new MethodInsnNode(INVOKEVIRTUAL, "java/lang/Integer", "intValue", "()I"));
            } else {
                push.add(new VarInsnNode(ILOAD, saltSlot));
            }

            return push;
        };
    }

    private static InsnFactory getClassSaltPush(ClassExtension.ClassSaltInfo classSaltInfo) {
        return () -> {
            InsnList classSaltPush = new InsnList();
            classSaltPush.add(new FieldInsnNode(GETSTATIC, classSaltInfo.getOwner(), classSaltInfo.getName(), classSaltInfo.getDesc()));

            return classSaltPush;
        };
    }

    public static InsnList getRandomJump(
            LabelNode to,
            boolean isClinit,
            ClassExtension.ClassSaltInfo classSaltInfo,
            MethodExtension.MethodSaltInfo methodSaltInfo,
            MethodExtension.ParamObfInfo paramObfInfo
    ) {
        InsnList insns = new InsnList();
        switch (RandomUtil.getInt(0, 5)) {
            case 0 -> {
                insns.add(getObfuscatedIntPush(0, isClinit, classSaltInfo, methodSaltInfo, paramObfInfo));
                insns.add(new JumpInsnNode(IFEQ, to));
            }
            case 1 -> {
                insns.add(getObfuscatedIntPush(RandomUtil.getIntExcept(0), isClinit, classSaltInfo, methodSaltInfo, paramObfInfo));
                insns.add(new JumpInsnNode(IFNE, to));
            }
            case 2 -> {
                insns.add(getObfuscatedIntPush(RandomUtil.getInt(SAFE_MIN, -1), isClinit, classSaltInfo, methodSaltInfo, paramObfInfo));
                insns.add(new JumpInsnNode(IFLT, to));
            }
            case 3 -> {
                insns.add(getObfuscatedIntPush(RandomUtil.getInt(SAFE_MIN, 0), isClinit, classSaltInfo, methodSaltInfo, paramObfInfo));
                insns.add(new JumpInsnNode(IFLE, to));
            }
            case 4 -> {
                insns.add(getObfuscatedIntPush(RandomUtil.getInt(1, SAFE_MAX), isClinit, classSaltInfo, methodSaltInfo, paramObfInfo));
                insns.add(new JumpInsnNode(IFGT, to));
            }
            case 5 -> {
                insns.add(getObfuscatedIntPush(RandomUtil.getInt(0, SAFE_MAX), isClinit, classSaltInfo, methodSaltInfo, paramObfInfo));
                insns.add(new JumpInsnNode(IFGE, to));
            }
            case 6 -> {
                int random = RandomUtil.getInt(SAFE_MIN, SAFE_MAX);
                insns.add(getObfuscatedIntPush(random, isClinit, classSaltInfo, methodSaltInfo, paramObfInfo));
                insns.add(getObfuscatedIntPush(random + 1, isClinit, classSaltInfo, methodSaltInfo, paramObfInfo));
                insns.add(getObfuscatedIntPush(1, isClinit, classSaltInfo, methodSaltInfo, paramObfInfo));
                insns.add(new InsnNode(ISUB));
                insns.add(new JumpInsnNode(IF_ICMPEQ, to));
            }
            case 7 -> {
                int random = RandomUtil.getInt(SAFE_MIN, SAFE_MAX);
                insns.add(getObfuscatedIntPush(random, isClinit, classSaltInfo, methodSaltInfo, paramObfInfo));
                insns.add(getObfuscatedIntPush(RandomUtil.getIntExcept(random), isClinit, classSaltInfo, methodSaltInfo, paramObfInfo));
                insns.add(new JumpInsnNode(IF_ICMPNE, to));
            }
            case 8 -> {
                int random = RandomUtil.getInt();
                insns.add(getObfuscatedIntPush(random, isClinit, classSaltInfo, methodSaltInfo, paramObfInfo));
                insns.add(getObfuscatedIntPush(RandomUtil.getInt(random + 1, SAFE_MAX + 1), isClinit, classSaltInfo, methodSaltInfo, paramObfInfo));
                insns.add(new JumpInsnNode(IF_ICMPLT, to));
            }
            case 9 -> {
                int random = RandomUtil.getInt();
                insns.add(getObfuscatedIntPush(random, isClinit, classSaltInfo, methodSaltInfo, paramObfInfo));
                insns.add(getObfuscatedIntPush(RandomUtil.getInt(random, SAFE_MAX), isClinit, classSaltInfo, methodSaltInfo, paramObfInfo));
                insns.add(new JumpInsnNode(IF_ICMPLE, to));
            }
            case 10 -> {
                int random = RandomUtil.getInt();
                insns.add(getObfuscatedIntPush(random, isClinit, classSaltInfo, methodSaltInfo, paramObfInfo));
                insns.add(getObfuscatedIntPush(RandomUtil.getInt(SAFE_MIN - 1, random - 1), isClinit, classSaltInfo, methodSaltInfo, paramObfInfo));
                insns.add(new JumpInsnNode(IF_ICMPGT, to));
            }
            case 11 -> {
                int random = RandomUtil.getInt();
                insns.add(getObfuscatedIntPush(random, isClinit, classSaltInfo, methodSaltInfo, paramObfInfo));
                insns.add(getObfuscatedIntPush(RandomUtil.getInt(SAFE_MIN, random), isClinit, classSaltInfo, methodSaltInfo, paramObfInfo));
                insns.add(new JumpInsnNode(IF_ICMPGE, to));
            }
            default -> throw new IllegalStateException("This should never be reached");
        }
        return insns;
    }
}
