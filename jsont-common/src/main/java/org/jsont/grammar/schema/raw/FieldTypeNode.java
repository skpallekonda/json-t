package org.jsont.grammar.schema.raw;

public class FieldTypeNode {

    private final String typeName;
    private final boolean isArray;
    private final boolean isObject;
    private boolean optional;

    public FieldTypeNode(String typeName, boolean isArray, boolean isObject) {
        this.typeName = typeName;
        this.isArray = isArray;
        this.isObject = isObject;
    }

    public String getTypeName() {
        return typeName;
    }

    public boolean isArray() {
        return isArray;
    }

    public boolean isObject() {
        return isObject;
    }

    public boolean isOptional() {
        return optional;
    }

    public void setOptional(boolean optional) {
        this.optional = optional;
    }

}
