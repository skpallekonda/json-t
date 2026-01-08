package org.jsont.exception;

public class FieldException extends JsonTException {
    public FieldException(String message) {
        super(message);
    }

    public FieldException(String message, Throwable cause) {
        super(message, cause);
    }

    public FieldException(Throwable cause) {
        super(cause);
    }

    public FieldException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
