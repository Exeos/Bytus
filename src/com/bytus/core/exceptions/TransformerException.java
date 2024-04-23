package com.bytus.core.exceptions;

public class TransformerException extends RuntimeException {

    public TransformerException(ExceptionType et, String message) {
        super("Failed at -> " + et.name() + ": " + message);
    }

    public enum ExceptionType {
        INIT,
        RUN
    }
}
