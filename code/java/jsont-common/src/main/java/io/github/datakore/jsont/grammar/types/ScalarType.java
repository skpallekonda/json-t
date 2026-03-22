package io.github.datakore.jsont.grammar.types;

import io.github.datakore.jsont.grammar.data.ValueNodeKind;
import io.github.datakore.jsont.grammar.schema.ast.JsonBaseType;

public class ScalarType extends BaseType {

    private final JsonBaseType jsonBaseType;
    private final ValueNodeKind kind;
    private final int col;
    private final String fieldName;

    public ScalarType(int col, String fieldName, JsonBaseType jsonBaseType, ValueNodeKind kind) {
        this.jsonBaseType = jsonBaseType;
        this.kind = kind;
        this.col = col;
        this.fieldName = fieldName;
    }

    @Override
    public String fieldName() {
        return fieldName;
    }

    @Override
    public int colPosition() {
        return col;
    }

    public JsonBaseType elementType() {
        return jsonBaseType;
    }

    @Override
    public String type() {
        return this.jsonBaseType.identifier();
    }

    @Override
    public ValueNodeKind nodeKind() {
        return kind;
    }

}
