package me.exeos.bytus.asmplus.idkhowtonamethisyet;

import java.util.HashSet;
import java.util.Set;

public class MWList {

    private final Set<MethodWrapper> wrappers = new HashSet<>();

    public MWList(Set<MethodWrapper> initList) {
        wrappers.addAll(initList);
    }

    public void add(MethodWrapper mw) {
        wrappers.add(mw);
    }

    public Set<MethodWrapper> get() {
        return wrappers;
    }

    public boolean contains(MethodWrapper other) {
        for (MethodWrapper methodWrapper : wrappers) {
            if (methodWrapper.equals(other)) {
                return true;
            }
        }
        return false;
    }
}
