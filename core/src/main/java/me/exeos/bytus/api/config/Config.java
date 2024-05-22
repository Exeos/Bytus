package me.exeos.bytus.api.config;

import javax.annotation.Nullable;
import java.util.HashMap;

public class Config {

    private final HashMap<String, Object> keySet;

    public Config(HashMap<String, Object> keySet) {
        this.keySet = keySet;
    }

    @Nullable
    public Object getValue(String key) {
        return keySet.getOrDefault(key, null);
    }
}
