package io.github.datakore.jsont.grammar.schema.raw;


import io.github.datakore.jsont.grammar.schema.constraints.FieldConstraint;

public class ConstraintNode {

    private final FieldConstraint.ConstraitType name;
    private final Object value;

    public ConstraintNode(FieldConstraint.ConstraitType name, Object value) {
        this.name = name;
        this.value = value;
    }

    public FieldConstraint.ConstraitType getName() {
        return name;
    }

    public Object getValue() {
        return value;
    }

}
