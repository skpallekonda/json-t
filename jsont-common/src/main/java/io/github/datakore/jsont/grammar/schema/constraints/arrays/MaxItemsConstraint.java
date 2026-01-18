package io.github.datakore.jsont.grammar.schema.constraints.arrays;

import io.github.datakore.jsont.grammar.schema.constraints.BaseConstraint;

import java.util.List;

public class MaxItemsConstraint extends BaseConstraint {
    private final int maxItems;

    public MaxItemsConstraint(ConstraitType constraitType, int maxItems) {
        super(constraitType);
        this.maxItems = maxItems;
    }

    @Override
    protected String checkConstraintList(List<Object> value) {
        if (value != null && value.size() > maxItems) {
            return String.format("Field requires at most %d items", maxItems);
        } else {
            return null;
        }
    }

    @Override
    public Object constraintValue() {
        return this.maxItems;
    }
}
