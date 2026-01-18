package io.github.datakore.jsont.exception;

import io.github.datakore.jsont.grammar.schema.ast.FieldModel;

import java.util.List;

public class DataValidationException extends JsonTException {

    private final FieldModel field;
    private final Object value;
    private final List<?> errors;

    public DataValidationException(Throwable cause) {
        super(cause);
        this.field = null;
        this.value = null;
        this.errors = null;
    }

    public DataValidationException(FieldModel field, Object value, List<?> errors) {
        super("Validation Error");
        this.field = field;
        this.value = value;
        this.errors = errors;
    }

}
