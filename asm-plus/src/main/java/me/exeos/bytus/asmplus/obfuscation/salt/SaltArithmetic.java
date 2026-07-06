package me.exeos.bytus.asmplus.obfuscation.salt;

import me.exeos.bytus.asmplus.arithmetic.AsmArithmetic;
import me.exeos.bytus.asmplus.utils.InsnUtil;
import me.exeos.bytus.asmplus.utils.RandomUtil;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;

import java.util.Optional;

public class SaltArithmetic implements Opcodes {

    public static InsnList xorIntSaltPush(int value, SaltSource... saltSources) {
        InsnList pushInsn = new InsnList();

        if (saltSources.length == 0) {
            pushInsn.add(InsnUtil.getIntPush(value));
            return pushInsn;
        }

        // additional key required of saltSources are not even so the last salt source can be xored with something
        Optional<Integer> additionalKey = Optional.empty();
        if (saltSources.length % 2 != 0) {
            additionalKey = Optional.of(RandomUtil.getInt(10, 10000));
        }

        int key = saltSources[0].salt();
        for (int i = 1; i < saltSources.length; i++) {
            key ^= saltSources[i].salt();
        }

        if (additionalKey.isPresent()) {
            key ^= additionalKey.get();
        }

        pushInsn.add(InsnUtil.getIntPush(value ^ key));
        for (int i = 0; i < saltSources.length; i += 2) {
            pushInsn.add(saltSources[i].pushSaltInsn().make());
            if (i + 1 < saltSources.length) {
                pushInsn.add(saltSources[i + 1].pushSaltInsn().make());
            } else additionalKey.ifPresent(addKeyValue -> pushInsn.add(InsnUtil.getIntPush(addKeyValue)));
            pushInsn.add(new InsnNode(IXOR));
        }
        pushInsn.add(new InsnNode(IXOR));

        return pushInsn;
    }

    public static InsnList rotateIntSaltPush(int value, SaltSource... saltSources) {
        if (saltSources.length == 0) {
            return InsnUtil.getIntPushList(value);
        }

        boolean direction = RandomUtil.chance(50);
        InsnList pushInsn = new InsnList();

        int enc = value;
        for (SaltSource saltSource : saltSources) {
            int r = saltSource.salt() & 31;
            enc = direction
                    ? Integer.rotateLeft(enc ^ saltSource.salt(), r)
                    : Integer.rotateRight(enc ^ saltSource.salt(), r);
        }

        pushInsn.add(InsnUtil.getIntPush(enc));

        for (int i = saltSources.length - 1; i >= 0; i--) {
            pushInsn.add(saltSources[i].pushSaltInsn().make());
            pushInsn.add(InsnUtil.getIntPush(31));
            pushInsn.add(new InsnNode(IAND));

            if (RandomUtil.chance(50)) {
                pushInsn.add(new MethodInsnNode(INVOKESTATIC, "java/lang/Integer", direction ? "rotateRight" : "rotateLeft", "(II)I"));
            } else {
                pushInsn.add(direction ? AsmArithmetic.rotateRight() : AsmArithmetic.rotateLeft());
            }
            pushInsn.add(saltSources[i].pushSaltInsn().make());
            pushInsn.add(new InsnNode(IXOR));
        }

        return pushInsn;
    }
}
