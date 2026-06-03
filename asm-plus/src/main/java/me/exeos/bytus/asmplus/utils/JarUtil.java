package me.exeos.bytus.asmplus.utils;

import me.exeos.bytus.asmplus.jar.JarArchive;
import org.objectweb.asm.tree.ClassNode;

import java.util.Optional;

public class JarUtil {

    public static Optional<ClassNode> findClass(JarArchive jar, String name) {
        ClassNode cn = jar.getClasses().getOrDefault(name, null);
        return cn == null ? Optional.empty() : Optional.of(cn);
    }
}
