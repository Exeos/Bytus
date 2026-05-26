package me.exeos.bytus.core.config.members;

public record ReferencesConfigMember(boolean enable, boolean proxy, int proxyMinDepth, int proxyMaxDepth, boolean encrypt, boolean encryptMethodCalls, boolean encryptFieldAccess) {}
