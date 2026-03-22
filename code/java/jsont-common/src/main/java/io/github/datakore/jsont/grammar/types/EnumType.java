package io.github.datakore.jsont.grammar.types;

import io.github.datakore.jsont.grammar.data.ValueNodeKind;
import io.github.datakore.jsont.grammar.schema.ast.EnumModel;

public class EnumType extends BaseType {

    private final EnumModel enumModel;
    private final int col;
    private final String fieldName;

    public EnumType(int col, String fieldName, EnumModel enumName) {
        this.col = col;
        this.fieldName = fieldName;
        this.enumModel = enumName;
    }

    @Override
    public String fieldName() {
        return fieldName;
    }

    @Override
    public int colPosition() {
        return col;
    }

    @Override
    public String type() {
        return enumModel.name();
    }

    @Override
    public ValueNodeKind nodeKind() {
        return ValueNodeKind.ENUM;
    }

    @Override
    public boolean isEnum() {
        return true;
    }
}
