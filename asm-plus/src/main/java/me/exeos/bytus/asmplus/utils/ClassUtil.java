package me.exeos.bytus.asmplus.utils;

import me.exeos.bytus.asmplus.jar.JarArchive;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;

public class ClassUtil {

    private final static int MAX_GENERATION_TIES = 50;

    public static String getNoneCollidingFieldName(JarArchive archive, ClassNode classNode, Function<Integer, String> nameGeneration) {
        Set<String> collidingNames = new HashSet<>();

        classNode.fields.forEach(fieldNode -> collidingNames.add(fieldNode.name));
        HierarchyUtil.forEachAncestorClass(archive, classNode, ancestor -> {
            ancestor.fields.forEach(fieldNode -> collidingNames.add(fieldNode.name));
        });

        return getNonCollidingName(collidingNames, nameGeneration);
    }

    public static String getNoneCollidingMethodName(JarArchive archive, ClassNode classNode, Function<Integer, String> nameGeneration) {
        Set<String> collidingNames = new HashSet<>();

        classNode.methods.forEach(methodNode -> collidingNames.add(methodNode.name));
        HierarchyUtil.forEachAncestorClass(archive, classNode, ancestor -> {
            ancestor.methods.forEach(methodNode -> collidingNames.add(methodNode.name));
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
}
