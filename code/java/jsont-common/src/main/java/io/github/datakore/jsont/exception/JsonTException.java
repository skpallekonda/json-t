package io.github.datakore.jsont.exception;

import java.util.function.Function;

public abstract class JsonTException extends RuntimeException {

    private static final Function<String, String> customMessage = (msg) -> String.format("Jsont::%s", msg);

    public JsonTException(String message) {
        super(customMessage.apply(message));
    }

    public JsonTException(String message, Throwable cause) {
        super(customMessage.apply(message), cause);
    }

    public JsonTException(Throwable cause) {
        super(customMessage.apply(cause.getMessage()), cause);
    }

    public JsonTException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(customMessage.apply(message), cause, enableSuppression, writableStackTrace);
    }
}
