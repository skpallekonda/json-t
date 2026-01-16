package io.github.datakore.jsont.grammar.types;

import io.github.datakore.jsont.grammar.data.ValueNodeKind;

public class ArrayType extends BaseType {

    private final ValueType elementType;
    private final int colPosition;
    private final String fieldName;

    public ArrayType(int col, String fieldName, ValueType elementType) {
        this.elementType = elementType;
        this.colPosition = col;
        this.fieldName = fieldName;
    }

    @Override
    public String fieldName() {
        return fieldName;
    }

    @Override
    public int colPosition() {
        return colPosition;
    }

    @Override
    public String type() {
        return elementType.type();
    }

    public ValueType getElementType() {
        return elementType;
    }

    @Override
    public ValueNodeKind nodeKind() {
        return ValueNodeKind.ARRAY;
    }


    @Override
    public boolean isArray() {
        return true;
    }
}
