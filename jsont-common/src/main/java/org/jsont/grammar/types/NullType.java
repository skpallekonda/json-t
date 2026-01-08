package org.jsont.grammar.types;

import org.jsont.exception.DataException;
import org.jsont.grammar.data.JsontScalarType;

public class NullType implements ValueType {
    @Override
    public String name() {
        return JsontScalarType.NULL.name();
    }

    @Override
    public JsontScalarType valueType() {
        return JsontScalarType.NULL;
    }

    @Override
    public boolean isOptional() {
        return false;
    }

    @Override
    public void setOptional(boolean b) {
        throw new IllegalArgumentException("Not Allowed");
    }

    @Override
    public void validateShape(Object raw) {
        if (raw != null) {
            throw new DataException("Null type can not hold object");
        }
    }
}
