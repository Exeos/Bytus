package me.exeos.asmplus.utils;

import java.security.SecureRandom;

public class RandomUtil {


    public static int getInt() {
        return getInt(Integer.MIN_VALUE, Integer.MAX_VALUE);
    }

    public static int getInt(int min, int max) {
        if (min == max)
            return min;

        if (min > max) {
            throw new IllegalArgumentException("Max must be greater than min");
        }

        return (int) ((Math.random() * (max - min)) + min);
    }

    public static byte[] getBytes(int length) {
        byte[] randomBytes = new byte[length];
        SecureRandom secureRandom = new SecureRandom();
        secureRandom.nextBytes(randomBytes);
        return randomBytes;
    }
}
