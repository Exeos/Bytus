package me.exeos.bytus.core.transformer.impl;

import me.exeos.bytus.asmplus.jar.JarArchive;
import me.exeos.bytus.asmplus.remapper.ClassRemapper;
import me.exeos.bytus.core.config.BytusConfig;
import me.exeos.bytus.core.transformer.AbstractTransformer;
import me.exeos.bytus.core.transformer.Priority;
import me.exeos.bytus.core.transformer.context.JarContext;
import me.exeos.bytus.core.utils.RandomUtil;
import org.objectweb.asm.tree.ClassNode;

import java.util.HashMap;
import java.util.Map;

public class Renamor extends AbstractTransformer {

    public Renamor(BytusConfig config) {
        super(config);
    }

    @Override
    public boolean applies() {
        return config.rename;
    }

    @Override
    public int priority() {
        return Priority.RENAME;
    }

    @Override
    public void transform(JarContext context) {
        ClassRemapper remapper = new ClassRemapper(genClassMappings(context.jar()));
        remapper.remap(context.jar());
    }

    private Map<String, String> genClassMappings(JarArchive archive) {
        Map<String, String> classMapping = new HashMap<>();
        for (ClassNode classNode : archive.getClasses().values()) {
            if (classNode.name.equals(config.mainClassName)) {
                classMapping.put(classNode.name, classNode.name);
            } else {
                int l = 1;
                String rnd = RandomUtil.getString(l);
                while (classMapping.containsValue(rnd)) {
                    l++;
                    rnd = RandomUtil.getString(l);
                }
                classMapping.put(classNode.name, rnd);
            }
        }

        return classMapping;
    }
}
