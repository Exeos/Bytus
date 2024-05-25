package me.exeos.bytus.api.config;

import me.exeos.bytus.Bytus;
import org.objectweb.asm.tree.ClassNode;

import java.util.List;

public interface ConfigInterface {

    default Object getValue(String key) {
        return Bytus.instance.config.getValue(key);
    }

    default String getInputPath() {
        return (String) getValue("io.input");
    }

    default String getOutputPath() {
        return (String) getValue("io.output");
    }

    default String getMainClass() {
        return ((String) getValue("mainClass")).replace(".", "/");
    }

    default String getMainClassMain() {
        return (String) getValue("mainClassMain");
    }

    default String getBootstrapName() {
        return ((String) getValue("bootstrapClassName")).replace(".", "/");
    }

    default boolean isExcluded(ClassNode classNode) {
        return isExcluded(classNode.name);
    }

    default boolean isExcluded(String className) {
        for (String e : (List<String>) getValue("exclude")) {
            if (className.equals(e.replace(".", "/"))) {
                return true;
            }
        }

        return false;
    }

    default boolean isPackEnabled() {
        return (boolean) getValue("pack");
    }

    default boolean isRenamerEnabled() {
        return (boolean) getValue("renamer.enable");
    }
}
