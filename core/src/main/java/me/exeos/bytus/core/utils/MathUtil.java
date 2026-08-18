package me.exeos.bytus.core.utils;

public class MathUtil {

    public static int getClosets(int a, int b, int target) {
        if (a == b) {
            return a;
        }

        int aDiff = target - a;
        int bDiff = target - b;

        if (Math.abs(aDiff) > Math.abs(bDiff)) {
            return b;
        }

        return a;
    }
}
