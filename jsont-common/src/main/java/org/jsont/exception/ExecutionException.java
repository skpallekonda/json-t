package org.jsont.exception;

import org.jsont.errors.ValidationError;

import java.util.List;

public class ExecutionException extends JsonTException {

    private List<ValidationError> errors;

    public ExecutionException(String message) {
        super(message);
    }

    public ExecutionException(String message, List<ValidationError> validationErrors) {
        super(message);
        this.errors = validationErrors;
    }

    public ExecutionException(String message, Throwable cause) {
        super(message, cause);
    }

    public ExecutionException(Throwable cause) {
        super(cause);
    }

    public ExecutionException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }

    public List<ValidationError> getErrors() {
        return errors;
    }
}
