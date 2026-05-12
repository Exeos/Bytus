package me.exeos.bytus.api.config;

import java.util.HashMap;

public class Config {

    private final HashMap<String, Object> keySet;

    public Config(HashMap<String, Object> keySet) {
        this.keySet = keySet;
    }

    public Object getValue(String key) {
        return keySet.getOrDefault(key, null);
    }
}
