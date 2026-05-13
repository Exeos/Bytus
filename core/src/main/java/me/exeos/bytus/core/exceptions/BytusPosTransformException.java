package me.exeos.bytus.core.exceptions;

public class BytusPosTransformException extends RuntimeException {

    public BytusPosTransformException(String message) {
        super(message);
    }

    public BytusPosTransformException(String message, Throwable cause) {
        super(message, cause);
    }

    public BytusPosTransformException(Throwable cause) {
        super(cause);
    }
}
