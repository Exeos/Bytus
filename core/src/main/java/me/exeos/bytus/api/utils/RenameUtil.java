package me.exeos.bytus.api.utils;

import me.exeos.bytus.api.generation.UniqueNameGen;

public class RenameUtil {

    private final static UniqueNameGen nameGen = new UniqueNameGen();

    public static String next() {
        return nameGen.next();
    }
}
