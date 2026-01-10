package io.github.datakore.jsont.grammar.types;

import io.github.datakore.jsont.exception.SchemaException;
import io.github.datakore.jsont.grammar.data.JsontScalarType;

public class ScalarType implements ValueType {
    private final JsontScalarType kind;
    private boolean optional;

    public ScalarType(JsontScalarType kind, boolean optional) {
        this.kind = kind;
        this.optional = optional;
    }

    @Override
    public String name() {
        return kind.name().toLowerCase();
    }

    @Override
    public JsontScalarType valueType() {
        return this.kind;
    }

    @Override
    public boolean isOptional() {
        return optional;
    }

    @Override
    public void setOptional(boolean b) {
        this.optional = b;
    }

    @Override
    public void validateShape(Object raw) {
        if (raw == null) {
            return; // handled by nullability
        }

        switch (kind) {
            case NULL:
                checkNullability(raw);
                break;
            case OBJECT:
                throw new SchemaException(String.format("%s found, where a scalar value is expected", kind.name()));
            case BOOLEAN:
                if (!(raw instanceof Boolean)) {
                    throw new SchemaException("Boolean value expected, found " + raw);
                }
                break;
            case INT:
            case LONG:
            case DECIMAL:
            case FLOAT:
                if (!(raw instanceof Number)) {
                    throw new SchemaException(String.format(
                            "%s type expected, found other text %s", kind.name(), raw.toString()
                    ));
                }
            default:
        }

    }
}
