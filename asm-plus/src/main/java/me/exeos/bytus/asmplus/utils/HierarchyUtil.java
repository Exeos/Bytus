package me.exeos.bytus.asmplus.utils;

import me.exeos.bytus.asmplus.jar.JarArchive;
import org.objectweb.asm.tree.ClassNode;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

public class HierarchyUtil {

    public static void forEachAncestorClass(JarArchive jar, ClassNode start, Consumer<ClassNode> visitor) {
        forEachAncestorClass(jar, start, true, false, visitor);
    }

    public static void forEachAncestorClass(JarArchive jar, ClassNode start, boolean includeInterfaces, boolean includeStart, Consumer<ClassNode> visitor) {
        Deque<String> work = new ArrayDeque<>();
        Set<String> seen = new HashSet<>();

        if (start == null) {
            return;
        }

        if (includeStart) {
            work.add(start.name);
        } else {
            if (start.superName != null) {
                work.add(start.superName);
            }
            if (includeInterfaces) {
                work.addAll(start.interfaces);
            }
        }

        while (!work.isEmpty()) {
            String name = work.removeFirst();
            if (!seen.add(name)) {
                continue;
            }
            ;

            ClassNode cn = jar.getClasses().get(name);
            if (cn == null) {
                continue;
            }

            visitor.accept(cn);

            if (cn.superName != null) {
                work.add(cn.superName);
            }

            if (includeInterfaces) {
                work.addAll(cn.interfaces);
            }
        }
    }
}
