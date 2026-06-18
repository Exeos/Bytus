package me.exeos.bytus.asmplus.matcher.method;

import java.util.HashSet;
import java.util.Set;

public class MethodMatcher {

    private final Set<MethodMatchEntry> wrappers = new HashSet<>();

    public MethodMatcher(Set<MethodMatchEntry> initList) {
        wrappers.addAll(initList);
    }

    public void add(MethodMatchEntry mw) {
        wrappers.add(mw);
    }

    public Set<MethodMatchEntry> get() {
        return wrappers;
    }

    public boolean match(MethodMatchEntry other) {
        for (MethodMatchEntry methodMatchEntry : wrappers) {
            if (methodMatchEntry.equals(other)) {
                return true;
            }
        }
        return false;
    }

    public static enum Mode {
        OWNER_NAME_DESC,
        OWNER_NAME,
        NAME
    }
}
