package io.github.datakore.jsont.grammar.schema.constraints.number;

import io.github.datakore.jsont.grammar.schema.constraints.BaseConstraint;

public class MaxValueConstraint extends BaseConstraint {
    private final double maxValue;

    public MaxValueConstraint(ConstraitType constraitType, double maxValue) {
        super(constraitType);
        this.maxValue = maxValue;
    }

    @Override
    protected String checkConstraintScalar(Object value) {
        if (value instanceof Number) {
            Number number = (Number) value;
            if (number.doubleValue() > maxValue) {
                return String.format("Field value is greater than maximum value %f", maxValue);
            }
        }
        return null;
    }

    @Override
    public Object constraintValue() {
        return this.maxValue;
    }
}
