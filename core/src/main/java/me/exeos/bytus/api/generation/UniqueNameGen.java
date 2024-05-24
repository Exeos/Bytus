package me.exeos.bytus.api.generation;

import me.exeos.bytus.api.utils.NumberSysUtil;

public class UniqueNameGen {

    private long count = -1;

    public String next() {
        count++;
        return NumberSysUtil.toBase26(count);
    }
}
