package io.github.datakore.jsont.grammar.types;

import io.github.datakore.jsont.grammar.data.JsontScalarType;

public class EnumType implements ValueType {

    private final boolean optional;
    private final JsontScalarType elementType;
    private final String enumName;

    public EnumType(String enumName, JsontScalarType elementType, boolean optional) {
        this.enumName = enumName;
        this.optional = optional;
        this.elementType = elementType;
    }

    public String enumName() {
        return enumName;
    }

    @Override
    public String name() {
        return this.elementType.name();
    }

    @Override
    public JsontScalarType valueType() {
        return this.elementType;
    }

    @Override
    public boolean isOptional() {
        return this.optional;
    }

    @Override
    public void setOptional(boolean b) {
        throw new IllegalArgumentException("Not Allowed");
    }

    @Override
    public void validateShape(Object raw) {
        // Do nothing
    }
}
