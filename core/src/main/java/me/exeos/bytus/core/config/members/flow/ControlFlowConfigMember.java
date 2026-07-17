package me.exeos.bytus.core.config.members.flow;

public record ControlFlowConfigMember(boolean enable, boolean replaceGotos, int minDispatcherChainLength,
                                      int maxDispatcherChainLength) {
}
