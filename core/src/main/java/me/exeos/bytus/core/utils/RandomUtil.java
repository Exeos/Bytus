package me.exeos.bytus.core.utils;

import java.util.Random;

public class RandomUtil {

    private static final String chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final Random rnd = new Random();

    public static int nextInt() {
        return rnd.nextInt(Integer.MAX_VALUE);
    }

    public static int getInt() {
        return getInt(Integer.MIN_VALUE, Integer.MAX_VALUE);
    }

    public static int getInt(int min, int max) {
        if (min == max) {
            return min;
        }

        if (min > max) {
//            throw new IllegalArgumentException("Max must be greater than min");
            int temp = min;
            min = max;
            max = temp;
        }

        return (int) ((Math.random() * (max - min)) + min);
    }

    public static boolean chance(int percentage) {
        if (percentage < 0 || percentage > 100) {
            throw new IllegalArgumentException("Percentage must be between 0 - 100");
        }
        return percentage <= getInt(0, 100);
    }

    public static String getString(int length) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < length; i++) {
            builder.append(chars.charAt(getInt(0, chars.length() - 1)));
        }
        return builder.toString();
    }
}
