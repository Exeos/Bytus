package me.exeos.bytus.core.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import me.exeos.asmplus.jar.JarArchive;
import me.exeos.asmplus.matcher.method.MethodMatchEntry;
import me.exeos.asmplus.matcher.method.MethodMatcher;
import me.exeos.asmplus.utils.ClassUtil;
import me.exeos.asmplus.utils.JarUtil;
import me.exeos.asmplus.utils.MethodUtil;
import me.exeos.bytus.core.config.members.*;
import me.exeos.bytus.core.config.members.flow.FlowConfigMember;
import org.objectweb.asm.Opcodes;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.*;

public class BytusConfig {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public final IOConfigMember io;
    public final List<String> exclusions;
    public final EntryPointsConfigMember entryPoints;
    public final boolean rename;
    public final boolean salt;
    // Key = Class that gets initialized AFTER Values
    public final Map<String, Set<String>> classInitOrder = new HashMap<>();
    public final ConstantsConfigMember constants;
    public final MbaConfigMember mba;
    public final FlowConfigMember flow;
    public final ReferencesConfigMember references;

    @JsonCreator
    public BytusConfig(
            @JsonProperty("io") IOConfigMember io,
            @JsonProperty("exclusions") List<String> exclusions,
            @JsonProperty("entryPoints") EntryPointsConfigMember entryPoints,
            @JsonProperty("rename") boolean rename,
            @JsonProperty("salt") boolean salt,
            @JsonProperty("classInitOrder") String[][] classInitOrderRaw,
            @JsonProperty("constants") ConstantsConfigMember constants,
            @JsonProperty("mba") MbaConfigMember mba,
            @JsonProperty("flow") FlowConfigMember flow,
            @JsonProperty("referenceEncryption") ReferencesConfigMember references) {
        this.io = io;
        this.exclusions = exclusions;
        this.entryPoints = entryPoints;
        this.rename = rename;
        this.salt = salt;
        this.constants = constants;
        this.mba = mba;
        this.flow = flow;
        this.references = references;

        for (int i = 0; i < classInitOrderRaw.length; i++) {
            String[] initEntry = classInitOrderRaw[i];
            if (initEntry.length != 2) {
                System.out.println("Invalid classInitOrder entry at index: " + i);
                continue;
            }

            classInitOrder.computeIfAbsent(initEntry[1], _ -> new HashSet<>()).add(initEntry[0]);
        }
    }

    public static BytusConfig fromJson(String json) throws JacksonException {
        return MAPPER.readValue(json, BytusConfig.class);
    }

    public Set<MethodMatchEntry> getEntryPoints(JarArchive jar) {
        Set<MethodMatchEntry> entries = new HashSet<>();
        if (entryPoints.fromManifest()) {
            JarUtil.getMainClass(jar).ifPresent(mainClass -> {
                mainClass.methods.stream().filter(methodNode ->
                        methodNode.name.equals("main")
                                && methodNode.desc.equals("([Ljava/lang/String;)V")
                                && MethodUtil.hasAccess(methodNode, Opcodes.ACC_PUBLIC)
                                && MethodUtil.hasAccess(methodNode, Opcodes.ACC_STATIC)).forEach(entryMethod -> entries.add(new MethodMatchEntry(mainClass.name, entryMethod.name, entryMethod.desc, MethodMatcher.Mode.OWNER_NAME_DESC)));
            });
        }
        entryPoints.custom().forEach((className, methodName) -> {
            jar.getClassNode(className, false).flatMap(classNode -> ClassUtil.findMethod(classNode, methodName, "([Ljava/lang/String;)V")).ifPresent(entryMethod -> entries.add(new MethodMatchEntry(className, methodName, entryMethod.desc, MethodMatcher.Mode.OWNER_NAME_DESC)));
        });

        return entries;
    }
}
