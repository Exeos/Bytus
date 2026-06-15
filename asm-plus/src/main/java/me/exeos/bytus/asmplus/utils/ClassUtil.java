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

    private final static int MAX_GENERATION_TIES = 50;

    public static String getNoneCollidingClassName(JarArchive archive, Function<Integer, String> nameGeneration) {
        return getNonCollidingName(archive.getClasses().keySet(), nameGeneration);
    }

    public static String getNoneCollidingFieldName(JarArchive archive, ClassNode classNode, Function<Integer, String> nameGeneration) {
        Set<String> collidingNames = new HashSet<>();
        HierarchyUtil.forEachAncestorClass(archive, classNode, true, true, cn -> {
            cn.fields.forEach(fieldNode -> collidingNames.add(fieldNode.name));
        });

        return getNonCollidingName(collidingNames, nameGeneration);
    }


    public static String getNoneCollidingMethodName(JarArchive archive, ClassNode classNode, Function<Integer, String> nameGeneration) {
        return getNoneCollidingMethodName(archive, classNode, Set.of(), nameGeneration);
    }

    public static String getNoneCollidingMethodName(JarArchive archive, ClassNode classNode, Set<String> exclusions, Function<Integer, String> nameGeneration) {
        Set<String> collidingNames = new HashSet<>(exclusions);

        HierarchyUtil.forEachAncestorClass(archive, classNode, true, true, cn -> {
            cn.methods.forEach(methodNode -> collidingNames.add(methodNode.name));
        });

        return getNonCollidingName(collidingNames, nameGeneration);
    }

    private static String getNonCollidingName(Set<String> collidingNames, Function<Integer, String> nameGeneration) {
        String name;
        int tryCount = 1;
        do {
            name = nameGeneration.apply(tryCount);

            if (tryCount >= MAX_GENERATION_TIES) {
                throw new RuntimeException("Failed to generate name. Max tries of: " + MAX_GENERATION_TIES + " exceeded");
            }
            tryCount++;
        } while (collidingNames.contains(name));

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
