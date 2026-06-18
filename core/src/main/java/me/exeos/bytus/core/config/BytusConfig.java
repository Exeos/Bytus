package me.exeos.bytus.core.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import me.exeos.bytus.asmplus.idkhowtonamethisyet.MethodWrapper;
import me.exeos.bytus.asmplus.jar.JarArchive;
import me.exeos.bytus.asmplus.utils.ClassUtil;
import me.exeos.bytus.asmplus.utils.JarUtil;
import me.exeos.bytus.asmplus.utils.MethodUtil;
import me.exeos.bytus.core.config.members.ConstantsConfigMember;
import me.exeos.bytus.core.config.members.EntryPointsConfigMember;
import me.exeos.bytus.core.config.members.IOConfigMember;
import me.exeos.bytus.core.config.members.MbaConfigMember;
import me.exeos.bytus.core.config.members.flow.FlowConfigMember;
import me.exeos.bytus.core.config.members.reference.ReferencesConfigMember;
import org.objectweb.asm.Opcodes;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class BytusConfig {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public final IOConfigMember io;
    public final List<String> exclusions;
    public final EntryPointsConfigMember entryPoints;
    public final boolean rename;
    public final boolean salt;
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
            @JsonProperty("constants") ConstantsConfigMember constants,
            @JsonProperty("mba") MbaConfigMember mba,
            @JsonProperty("flow") FlowConfigMember flow,
            @JsonProperty("references") ReferencesConfigMember references) {
        this.io = io;
        this.exclusions = exclusions;
        this.entryPoints = entryPoints;
        this.rename = rename;
        this.salt = salt;
        this.constants = constants;
        this.mba = mba;
        this.flow = flow;
        this.references = references;
    }

    public static BytusConfig fromJson(String json) throws JacksonException {
        return MAPPER.readValue(json, BytusConfig.class);
    }

    public Set<MethodWrapper> getEntryPoints(JarArchive jar) {
        Set<MethodWrapper> entries = new HashSet<>();
        if (entryPoints.fromManifest()) {
            JarUtil.getMainClass(jar).ifPresent(mainClass -> {
                mainClass.methods.stream().filter(methodNode ->
                        methodNode.name.equals("main")
                                && methodNode.desc.equals("([Ljava/lang/String;)V")
                                && MethodUtil.hasAccess(methodNode, Opcodes.ACC_PUBLIC)
                                && MethodUtil.hasAccess(methodNode, Opcodes.ACC_STATIC)).forEach(entryMethod -> entries.add(new MethodWrapper(mainClass.name, entryMethod.name, entryMethod.desc, 0)));
            });
        }
        entryPoints.custom().forEach((className, methodName) -> {
            JarUtil.findClass(jar, className).flatMap(classNode -> ClassUtil.findMethod(classNode, methodName, "([Ljava/lang/String;)V")).ifPresent(entryMethod -> entries.add(new MethodWrapper(className, methodName, entryMethod.desc, 0)));
        });

        return entries;
    }
}
