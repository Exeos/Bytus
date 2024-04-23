package com.bytus.core.transformer.exclutions;

public class Exclution {

    private final ExType from;
    private final Object excludet;

    public Exclution(Object excludet, ExType from) {
        this.excludet = excludet;
        this.from = from;
    }

    public boolean match(Object inQuestion, ExType from) {
        return inQuestion == excludet && from == this.from;
    }
}
