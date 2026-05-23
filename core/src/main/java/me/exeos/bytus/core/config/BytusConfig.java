package me.exeos.bytus.core.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import me.exeos.bytus.core.config.members.*;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

public class BytusConfig {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public final IOConfigMember io;
    public final List<String> exclusions;
    public final PackerConfigMember packer;
    public final ConstantsConfigMember constants;
    public final FlowConfigMember flow;
    public final ReferencesConfigMember references;

    @JsonCreator
    public BytusConfig(
            @JsonProperty("io") IOConfigMember io,
            @JsonProperty("exclusions") List<String> exclusions,
            @JsonProperty("packer") PackerConfigMember packer,
            @JsonProperty("constants") ConstantsConfigMember constants,
            @JsonProperty("flow") FlowConfigMember flow,
            @JsonProperty("references") ReferencesConfigMember references) {
        this.io = io;
        this.exclusions = exclusions;
        this.packer = packer;
        this.constants = constants;
        this.flow = flow;
        this.references = references;
    }

    public static BytusConfig fromJson(String json) throws JacksonException {
        return MAPPER.readValue(json, BytusConfig.class);
    }
}
