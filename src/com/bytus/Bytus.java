package com.bytus;

import com.bytus.core.Core;

public class Bytus {

    public static Bytus INSTANCE;
    public final Core core = new Core();

    public Bytus() {
        Bytus.INSTANCE = this;
    }

    public void start() throws Exception {
        core.load("C:\\Users\\valentin\\Desktop\\coding\\java\\Bytus\\jars\\obftest - org.jar");
        core.transform(Config.transformers());
        core.export("jars\\out.jar");
    }
}
