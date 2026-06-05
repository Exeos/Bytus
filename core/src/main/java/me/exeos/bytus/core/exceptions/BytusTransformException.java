package me.exeos.bytus.core.exceptions;

public class BytusTransformException extends RuntimeException {

    public BytusTransformException(String message) {
        super(message);
    }

    public BytusTransformException(String message, Throwable cause) {
        super(message, cause);
    }

    public BytusTransformException(Throwable cause) {
        super(cause);
    }
}
