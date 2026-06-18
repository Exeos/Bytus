package me.exeos.bytus.asmplus.idkhowtonamethisyet;

import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

public record MethodWrapper(String owner, String name, String desc, int matchMode) {

    // matchModes:
    // 0 = owner, name, desc
    // 1 = owner, name
    // 2 = name

    public static MethodWrapper of(String owner, MethodNode methodNode) {
        return of(owner, methodNode, 0);
    }

    public static MethodWrapper of(String owner, MethodNode methodNode, int matchMode) {
        return new MethodWrapper(owner, methodNode.name, methodNode.desc, matchMode);
    }

    public static MethodWrapper of(MethodInsnNode insnNode) {
        return of(insnNode, 0);
    }

    public static MethodWrapper of(MethodInsnNode insnNode, int matchMode) {
        return new MethodWrapper(insnNode.owner, insnNode.name, insnNode.desc, matchMode);
    }

    public static MethodWrapper of(String owner, String name, String desc) {
        return new MethodWrapper(owner, name, desc, 0);
    }

    public static MethodWrapper of(String owner, String name) {
        return new MethodWrapper(owner, name, "", 1);
    }

    public static MethodWrapper of(String name) {
        return new MethodWrapper("", name, "", 2);
    }

    @Override
    public boolean equals(Object other) {
        if (other == null) {
            return false;
        }

        return other == this ||
                (other instanceof MethodWrapper(String otherOwner, String otherName, String otherDesc, int _)
                        && (matchMode() == 2 || otherOwner.equals(owner()))
                        && (otherName.equals(name()))
                        && (matchMode() == 1 || matchMode() == 2 || otherDesc.equals(desc()))
                );
    }
}
