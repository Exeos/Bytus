package me.exeos.bytus.core.config.members;

import java.util.Map;

public record EntryPointsConfigMember(boolean fromManifest, Map<String, String> custom) {
}
