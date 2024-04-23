package me.exeos.asmplus.utils;

import java.util.Random;

public class RandomUtil {

    private static final Random RANDOM = new Random();

    public static int getInt() {
        return RANDOM.nextInt();
    }

    public static int getInt(int min, int max) {
        if (min == max)
            return min;

        if (min > max) {
            throw new IllegalArgumentException("Max must be greater than min");
        }

        return RANDOM.nextInt(max - min + 1) + min;
    }
}
