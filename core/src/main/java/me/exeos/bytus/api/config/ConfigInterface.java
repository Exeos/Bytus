package me.exeos.bytus.api.config;

import me.exeos.bytus.Bytus;

public interface ConfigInterface {

    default String getInputPath() {
        return (String) Bytus.instance.config.getValue("io.input");
    }

    default String getOutputPath() {
        return (String) Bytus.instance.config.getValue("io.output");
    }

    default boolean isRenamerEnabled() {
        return (boolean) Bytus.instance.config.getValue("renamer.enable");
    }
}
