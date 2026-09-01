package me.exeos.bytus.core.utils;

public class NameUtil {

    private final static String CHARS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

//    public static String getName(int value) {
//        int base = CHARS.length();
//        StringBuilder builder = new StringBuilder();
//
//        do {
//            builder.insert(0, CHARS.charAt(value % base));
//            value = value / base - 1;
//        } while (value >= 0);
//
//        return builder.toString();
//    }

    public static String getName(int len) {
        return RandomUtil.getString(len + 1);
    }
}
