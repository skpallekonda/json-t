package io.github.datakore.jsont.grammar.schema.constraints.arrays;

import io.github.datakore.jsont.grammar.schema.constraints.BaseConstraint;

import java.util.List;
import java.util.Objects;

public class MaxNullElementsConstraint extends BaseConstraint {
    private final int maxNullElements;

    public MaxNullElementsConstraint(ConstraitType constraitType, int maxNullElements) {
        super(constraitType);
        this.maxNullElements = maxNullElements;
    }

    @Override
    protected String checkConstraintList(List<Object> value) {
        if (value != null && value.stream().filter(Objects::isNull).count() > maxNullElements) {
            return String.format("Field requires at most %d null elements", maxNullElements);
        }
        return null;
    }

    @Override
    public Object constraintValue() {
        return this.maxNullElements;
    }
}
