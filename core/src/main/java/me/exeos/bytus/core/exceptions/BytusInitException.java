package me.exeos.bytus.core.exceptions;

public class BytusInitException extends RuntimeException {

    public BytusInitException(String message) {
        super(message);
    }

    public BytusInitException(String message, Throwable cause) {
        super(message, cause);
    }

    public BytusInitException(Throwable cause) {
        super(cause);
    }
}
