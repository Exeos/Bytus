package me.exeos.bytus.core.utils;

import me.exeos.bytus.core.asm.ObfCodenGen;

import java.util.*;
import java.util.stream.Collectors;

public class RandomUtil {

    private final static String CHARS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private final static Random RND = new Random();

    public static int nextInt() {
        return RND.nextInt(ObfCodenGen.SAFE_MAX);
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
        } while (ArrayUtil.contains(except, i));
        return i;
    }

    public static int getIntExcept(Set<Integer> except) {
        int i;
        do {
            i = getInt();
        } while (except.contains(i));
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
            builder.append(CHARS.charAt(getInt(0, CHARS.length() - 1)));
        }
        return builder.toString();
    }

    public static <K, V> Map.Entry<K, V> getRandomMapEntry(Map<K, V> map) {
        int randomIndex = getInt(0, map.size() - 1);

        var iterator = map.entrySet().iterator();
        int currentIndex = 0;

        while (iterator.hasNext()) {
            var element = iterator.next();
            if (currentIndex == randomIndex) {
                return element;
            }
            currentIndex++;
        }

        return null;
    }

    public static <T> T getRandomEntry(Set<T> set, T... except) {
        if (set == null) {
            return null;
        }

        return getRandomEntry(
                set.stream().filter(t -> !ArrayUtil.contains(except, t)).collect(Collectors.toSet())
        );
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

    public static <T> T getRandom(List<T> list) {
        if (list.isEmpty()) {
            return null;
        }

        return list.get(getInt(0, list.size() - 1));
    }
}
