package com.bytus.test;


import java.lang.reflect.Method;

public class Bootsstrap {

    public static void main(String[] args) {
        try {
            CLoader myClassLoader = new CLoader();
            Class dynamicClass = myClassLoader.findClass("Main");
            Method m = dynamicClass.getMethod("main", String[].class);

            m.invoke(null, new Object[] {null});
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }
}
