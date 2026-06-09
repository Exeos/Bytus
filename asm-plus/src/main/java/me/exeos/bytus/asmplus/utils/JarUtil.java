package me.exeos.bytus.asmplus.utils;

import me.exeos.bytus.asmplus.jar.JarArchive;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import javax.swing.text.html.Option;
import java.util.Optional;

public class JarUtil {


    public static Optional<ClassNode> findClass(JarArchive jar, String name) {
        ClassNode cn = jar.getClasses().getOrDefault(name, null);
        return cn == null ? Optional.empty() : Optional.of(cn);
    }

    public static Optional<ClassNode> getMainClass(JarArchive jar) {
        if (jar.getManifest() != null) {
            String mainClassName = jar.getManifest().getMainAttributes().getValue("Main-Class");
            if (mainClassName != null) {
                return JarUtil.findClass(jar, mainClassName.replace(".", "/"));
            }
        }
        return Optional.empty();
    }

    public static Optional<String> getMainMethodFromManifest(JarArchive jar) {
        if (jar.getManifest() != null) {
            String mainClassName = jar.getManifest().getMainAttributes().getValue("Main-Class");
            if (mainClassName != null) {
                return Optional.of(mainClassName.replace(".", "/") + "main" + "([Ljava/lang/String;)V");
            }
        }
        return Optional.empty();
    }

    public static Optional<MethodNode> findMethod(JarArchive jar, String ownerName, String methodName, String methodDesc) {
        Optional<ClassNode> owner = JarUtil.findClass(jar, ownerName);
        if (owner.isEmpty()) {
            return Optional.empty();
        }

        return ClassUtil.findMethod(owner.get(), methodName, methodDesc);
    }
}
