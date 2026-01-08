package org.jsont.grammar.schema.raw;


public class ConstraintNode {

    private final String name;
    private final Object value;

    public ConstraintNode(String name, Object value) {
        this.name = name;
        this.value = value;
    }

    public String getName() {
        return name;
    }

    public Object getValue() {
        return value;
    }

}
