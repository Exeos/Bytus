package me.exeos.bytus.core.asm;

import me.exeos.asmplus.InsnProvider;
import me.exeos.asmplus.codegen.value.ValueSource;
import me.exeos.asmplus.codegen.value.impl.ConstantPusher;
import me.exeos.asmplus.codegen.value.impl.IntObfuscation;
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
            return ConstantPusher.getIntPushList(value);
        }

        ValueSource<Integer> methodSalt = new ValueSource<>(methodSaltInfo.getSaltOrDefault(), getMethodSaltPush(methodSaltInfo, paramObfInfo));
        ValueSource<Integer> classSalt;
        if (classSaltInfo.hasPreInitializingSalts() && isClinit) {
            ClassExtension.ClassSaltInfo randomPre = RandomUtil.getRandomEntry(classSaltInfo.getPreInitingSalts());
            classSalt = new ValueSource<>(randomPre.getSalt(), getClassSaltPush(randomPre));
        } else {
            classSalt = new ValueSource<>(classSaltInfo.getSaltOrDefault(), getClassSaltPush(classSaltInfo));
        }

        if (hasMethodSalt && hasClassSalt) {
            return IntObfuscation.getIntPush(value, methodSalt, classSalt);
        }

        if (hasMethodSalt) {
            return IntObfuscation.getIntPush(value, methodSalt);
        }

        return IntObfuscation.getIntPush(value, classSalt);
    }

    private static InsnProvider getMethodSaltPush(MethodExtension.MethodSaltInfo methodSaltInfo, MethodExtension.ParamObfInfo paramObfInfo) {
        return () -> {
            InsnList push = new InsnList();

            int saltSlot = paramObfInfo.getArrayIndexBySlotOrSlot(methodSaltInfo.getSaltSlotOrDefault());
            if (paramObfInfo.hasParamObf()) {
                push.add(new VarInsnNode(ALOAD, paramObfInfo.getObjArrSlotOrDefault()));
                push.add(ConstantPusher.getIntPush(saltSlot));
                push.add(new InsnNode(AALOAD));
                push.add(new TypeInsnNode(CHECKCAST, "java/lang/Integer"));
                push.add(new MethodInsnNode(INVOKEVIRTUAL, "java/lang/Integer", "intValue", "()I"));
            } else {
                push.add(new VarInsnNode(ILOAD, saltSlot));
            }

            return push;
        };
    }

    private static InsnProvider getClassSaltPush(ClassExtension.ClassSaltInfo classSaltInfo) {
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
            MethodExtension.ParamObfInfo paramObfInfo,
            boolean shouldJump
    ) {
        InsnList insns = new InsnList();
        switch (RandomUtil.getInt(0, 5)) {
            case 0 -> {
                insns.add(getObfuscatedIntPush(shouldJump ? 0 : RandomUtil.getIntExcept(0), isClinit, classSaltInfo, methodSaltInfo, paramObfInfo));
                insns.add(new JumpInsnNode(IFEQ, to));
            }
            case 1 -> {
                insns.add(getObfuscatedIntPush(shouldJump ? RandomUtil.getIntExcept(0) : 0, isClinit, classSaltInfo, methodSaltInfo, paramObfInfo));
                insns.add(new JumpInsnNode(IFNE, to));
            }
            case 2 -> {
                insns.add(getObfuscatedIntPush(shouldJump ? RandomUtil.getInt(SAFE_MIN, -1) : RandomUtil.getInt(0, SAFE_MAX), isClinit, classSaltInfo, methodSaltInfo, paramObfInfo));
                insns.add(new JumpInsnNode(IFLT, to));
            }
            case 3 -> {
                insns.add(getObfuscatedIntPush(shouldJump ? RandomUtil.getInt(SAFE_MIN, 0) : RandomUtil.getInt(1, SAFE_MAX), isClinit, classSaltInfo, methodSaltInfo, paramObfInfo));
                insns.add(new JumpInsnNode(IFLE, to));
            }
            case 4 -> {
                insns.add(getObfuscatedIntPush(shouldJump ? RandomUtil.getInt(1, SAFE_MAX) : RandomUtil.getInt(SAFE_MIN, 0), isClinit, classSaltInfo, methodSaltInfo, paramObfInfo));
                insns.add(new JumpInsnNode(IFGT, to));
            }
            case 5 -> {
                insns.add(getObfuscatedIntPush(shouldJump ? RandomUtil.getInt(0, SAFE_MAX) : RandomUtil.getInt(SAFE_MIN, -1), isClinit, classSaltInfo, methodSaltInfo, paramObfInfo));
                insns.add(new JumpInsnNode(IFGE, to));
            }
            default -> throw new IllegalStateException("This should never be reached");
        }
        return insns;
    }
}
