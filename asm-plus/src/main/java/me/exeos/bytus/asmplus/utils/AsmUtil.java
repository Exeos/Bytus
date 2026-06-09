package me.exeos.bytus.asmplus.utils;

public class AsmUtil {

    public static boolean hasAccess(int access, int toCheck) {
        return (access & toCheck) != 0;
    }
}
