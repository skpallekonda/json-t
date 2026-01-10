package io.github.datakore.jsont.grammar.types;

import io.github.datakore.jsont.grammar.data.JsontScalarType;

import java.util.List;

public class ArrayType implements ValueType {
    private final ValueType elementType;
    private final boolean optional;

    public ArrayType(ValueType elementType, boolean optional) {
        this.elementType = elementType;
        this.optional = optional;
    }

    @Override
    public String name() {
        return String.format("%s[]", elementType.name());
    }

    @Override
    public JsontScalarType valueType() {
        return elementType.valueType();
    }

    public ValueType elementType() {
        return this.elementType;
    }

    @Override
    public boolean isOptional() {
        return optional;
    }

    @Override
    public void setOptional(boolean b) {
        throw new IllegalArgumentException("Not allowed for ArrayType");
    }

    @Override
    public void validateShape(Object raw) {
        if (raw == null) {
            return; // nullability handled in ValueType.validate()
        }
        if (!(raw instanceof List)) {
//            throw new IllegalArgumentException(
//                    "Expected list, but got " + raw.getClass().getName());
        }
    }

    @Override
    public void checkNullability(Object raw) {
        if (raw == null && !isOptional()) {
            throw new IllegalArgumentException(
                    "Null value not allowed for type " + name());
        }
    }
}
