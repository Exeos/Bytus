package me.exeos.bytus.core.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import me.exeos.bytus.core.config.members.ConstantsConfigMember;
import me.exeos.bytus.core.config.members.PackerConfigMember;
import me.exeos.bytus.core.config.members.IOConfigMember;
import me.exeos.bytus.core.transformer.Transformer;
import me.exeos.bytus.core.transformer.impl.constants.number.OverUnderFlowIntTransformer;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

public class BytusConfig {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public final IOConfigMember io;
    public final List<String> exclusions;
    public final PackerConfigMember packer;
    public final ConstantsConfigMember constants;
    public final boolean flow;

    @JsonCreator
    public BytusConfig(
            @JsonProperty("io") IOConfigMember io,
            @JsonProperty("exclusions") List<String> exclusions,
            @JsonProperty("packer") PackerConfigMember packer,
            @JsonProperty("constants") ConstantsConfigMember constants,
            @JsonProperty("flow") boolean flow) {
        this.io = io;
        this.exclusions = exclusions;
        this.packer = packer;
        this.constants = constants;
        this.flow = flow;
    }

    public static BytusConfig fromJson(String json) throws JacksonException {
        return MAPPER.readValue(json, BytusConfig.class);
    }
}
