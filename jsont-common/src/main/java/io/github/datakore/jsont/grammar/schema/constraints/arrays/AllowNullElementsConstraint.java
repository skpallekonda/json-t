package io.github.datakore.jsont.grammar.schema.constraints.arrays;

import io.github.datakore.jsont.grammar.schema.constraints.BaseConstraint;

import java.util.List;
import java.util.Objects;

public class AllowNullElementsConstraint extends BaseConstraint {
    private final boolean allowNullElements;

    public AllowNullElementsConstraint(ConstraitType constraint, boolean allowNullElements) {
        super(constraint);
        this.allowNullElements = allowNullElements;
    }

    @Override
    protected String checkConstraintList(List<Object> value) {
        if (!allowNullElements && value != null && value.stream().anyMatch(Objects::isNull)) {
            return String.format("Field requires no null elements");
        }
        return null;
    }

    @Override
    public Object constraintValue() {
        return this.allowNullElements;
    }
}
