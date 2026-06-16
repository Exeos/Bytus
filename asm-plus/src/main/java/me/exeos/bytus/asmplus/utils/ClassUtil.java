package me.exeos.bytus.asmplus.utils;

import me.exeos.bytus.asmplus.jar.JarArchive;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

public class ClassUtil implements Opcodes {

    public static String getNoneCollidingClassName(JarArchive archive, Function<Integer, String> nameGeneration) {
        String name;
        int tryCount = 1;
        do {
            name = nameGeneration.apply(tryCount);
            tryCount++;
        } while (archive.getClasses().containsKey(name));

        return name;
    }

    public static MethodNode getOrCreateStaticInitializer(ClassNode classNode) {
        for (MethodNode method : classNode.methods) {
            if (method.name.equals("<clinit>")) return method;
        }

        MethodNode methodNode = new MethodNode(ACC_STATIC, "<clinit>", "()V", null, null);
        methodNode.instructions.add(new InsnNode(RETURN));
        classNode.methods.add(methodNode);
        return methodNode;
    }

    public static Optional<MethodNode> findMethod(ClassNode owner, String methodName, String methodDesc) {
        for (MethodNode methodNode : owner.methods) {
            if (methodNode.name.equals(methodName) && methodNode.desc.equals(methodDesc)) {
                return Optional.of(methodNode);
            }
        }

        return Optional.empty();
    }

    public static boolean isEnum(ClassNode classNode) {
        return classNode.superName != null && classNode.superName.equals("java/lang/Enum");
    }
}
