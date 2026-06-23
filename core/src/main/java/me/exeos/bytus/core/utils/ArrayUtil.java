package me.exeos.bytus.core.utils;

public class ArrayUtil {

    public static <T> boolean contains(int[] arr, int i) {
        for (int entry : arr) {
            if (entry == i) {
                return true;
            }
        }
        return false;
    }

    public static <T> boolean contains(T[] arr, T i) {
        for (T entry : arr) {
            if (entry.equals(i)) {
                return true;
            }
        }
        return false;
    }
}
