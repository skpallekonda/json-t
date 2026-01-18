package io.github.datakore.jsont.grammar.schema.constraints.arrays;

import io.github.datakore.jsont.grammar.schema.constraints.BaseConstraint;

import java.util.List;

public class MinItemsConstraint extends BaseConstraint {
    private final int minItems;

    public MinItemsConstraint(ConstraitType constraitType, int minItems) {
        super(constraitType);
        this.minItems = minItems;
    }

    @Override
    protected String checkConstraintList(List<Object> value) {
        if (value == null || value.size() < minItems) {
            return String.format("Field requires at least %d items", minItems);
        }
        return null;
    }

    @Override
    public Object constraintValue() {
        return this.minItems;
    }
}
