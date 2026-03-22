package io.github.datakore.jsont.grammar.types;

import io.github.datakore.jsont.grammar.data.ValueNodeKind;

public class NullType implements ValueType {

    private final int col;
    private final String fieldName;

    public NullType(int col, String fieldName) {
        this.col = col;
        this.fieldName = fieldName;
    }

    @Override
    public String fieldName() {
        return fieldName;
    }

    @Override
    public int colPosition() {
        return this.col;
    }

    @Override
    public String type() {
        return ValueNodeKind.NULL.name();
    }

    @Override
    public ValueNodeKind nodeKind() {
        return ValueNodeKind.NULL;
    }

    @Override
    public boolean isObject() {
        return false;
    }

    @Override
    public boolean isArray() {
        return false;
    }

    @Override
    public boolean isEnum() {
        return false;
    }
}
