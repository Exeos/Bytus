package com.bytus.utils;

import java.util.ArrayList;

public class NameGen {

    private String currentName;

    public NameGen() {
        this.currentName = "a";
    }

    public String name() {
        String name = currentName;
        currentName = nextName(currentName);
        return name;
    }

    private String nextName(String name) {
        char[] chars = name.toCharArray();
        int index = chars.length - 1;

        while (index >= 0 && chars[index] == 'z') {
            chars[index] = 'a';
            index--;
        }

        if (index < 0) {
            chars = new char[chars.length + 1];
            chars[0] = 'a';
            for (int i = 1; i < chars.length; i++) {
                chars[i] = 'a';
            }
        } else {
            chars[index]++;
        }

        return new String(chars);
    }

}
