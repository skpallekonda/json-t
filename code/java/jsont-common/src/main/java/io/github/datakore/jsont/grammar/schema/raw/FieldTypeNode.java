package io.github.datakore.jsont.grammar.schema.raw;

import io.github.datakore.jsont.grammar.data.ValueNodeKind;

public class FieldTypeNode {

    private final String typeName;
    private final ValueNodeKind kind;
    private boolean optional;

    public FieldTypeNode(String typeName, ValueNodeKind kind) {
        this.typeName = typeName;
        this.kind = kind;
    }

    public boolean isOptional() {
        return optional;
    }

    public void setOptional(boolean optional) {
        this.optional = optional;
    }

    public boolean isObject() {
        return kind == ValueNodeKind.OBJECT;
    }

    public boolean isArray() {
        return kind == ValueNodeKind.ARRAY;
    }

    public String getTypeName() {
        return typeName;
    }

    public ValueNodeKind getKind() {
        return kind;
    }

}
