package io.github.datakore.jsont.grammar.types;

import io.github.datakore.jsont.grammar.data.ValueNodeKind;
import io.github.datakore.jsont.grammar.schema.ast.SchemaModel;

public class ObjectType extends BaseType {

    private final int col;
    private final String fieldName;
    private final SchemaModel schema;

    public ObjectType(int col, String fieldName, SchemaModel schema) {
        this.schema = schema;
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

    @Override
    public String type() {
        return schema.name();
    }

    @Override
    public ValueNodeKind nodeKind() {
        return ValueNodeKind.OBJECT;
    }

    @Override
    public boolean isObject() {
        return true;
    }

}
