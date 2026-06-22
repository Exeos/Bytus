package me.exeos.bytus.core.utils;

import me.exeos.bytus.core.asm.ObfCodenGen;

import java.util.Iterator;
import java.util.Random;
import java.util.Set;

public class RandomUtil {

    private static final String chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final Random rnd = new Random();

    public static int nextInt() {
        return rnd.nextInt(ObfCodenGen.SAFE_MAX);
    }

    public static int getInt() {
        return getInt(ObfCodenGen.SAFE_MIN, ObfCodenGen.SAFE_MAX);
    }

    public static int getInt(int min, int max) {
        if (min == max) {
            return min;
        }

        if (min > max) {
            int temp = min;
            min = max;
            max = temp;
        }

        return (int) ((Math.random() * (max - min)) + min);
    }

    public static int getIntExcept(int... except) {
        int i;
        do {
            i = getInt();
        } while (contains(except, i));
        return i;
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

    private static boolean contains(int[] arr, int i) {
        for (int i1 : arr) {
            if (i1 == i) {
                return true;
            }
        }
        return false;
    }

    public static <T> T getRandomEntry(Set<T> set) {
        if (set == null || set.isEmpty()) {
            return null;
        }

        int randomIndex = getInt(0, set.size() - 1);

        Iterator<T> iterator = set.iterator();
        int currentIndex = 0;

        while (iterator.hasNext()) {
            T element = iterator.next();
            if (currentIndex == randomIndex) {
                return element;
            }
            currentIndex++;
        }

        return null;
    }
}
