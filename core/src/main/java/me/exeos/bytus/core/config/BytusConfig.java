package me.exeos.bytus.core.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import me.exeos.bytus.core.config.members.ConstantsConfigMember;
import me.exeos.bytus.core.config.members.IOConfigMember;
import me.exeos.bytus.core.config.members.MbaConfigMember;
import me.exeos.bytus.core.config.members.flow.FlowConfigMember;
import me.exeos.bytus.core.config.members.reference.ReferencesConfigMember;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

public class BytusConfig {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public final IOConfigMember io;
    public final List<String> exclusions;
    public final String mainClassName;
    public final boolean rename;
    public final ConstantsConfigMember constants;
    public final MbaConfigMember mba;
    public final FlowConfigMember flow;
    public final ReferencesConfigMember references;

    @JsonCreator
    public BytusConfig(
            @JsonProperty("io") IOConfigMember io,
            @JsonProperty("exclusions") List<String> exclusions,
            @JsonProperty("mainClassName") String mainClassName,
            @JsonProperty("rename") boolean rename,
            @JsonProperty("constants") ConstantsConfigMember constants,
            @JsonProperty("mba") MbaConfigMember mba,
            @JsonProperty("flow") FlowConfigMember flow,
            @JsonProperty("references") ReferencesConfigMember references) {
        this.io = io;
        this.exclusions = exclusions;
        this.mainClassName = mainClassName;
        this.rename = rename;
        this.constants = constants;
        this.mba = mba;
        this.flow = flow;
        this.references = references;
    }

    public static BytusConfig fromJson(String json) throws JacksonException {
        return MAPPER.readValue(json, BytusConfig.class);
    }
}
