package io.github.datakore.jsont.grammar.schema.constraints.number;

import io.github.datakore.jsont.grammar.schema.constraints.BaseConstraint;

public class MinValueConstraint extends BaseConstraint {
    private final double minValue;

    public MinValueConstraint(ConstraitType constraitType, double minValue) {
        super(constraitType);
        this.minValue = minValue;
    }

    @Override
    protected String checkConstraintScalar(Object value) {
        if (value instanceof Number) {
            Number number = (Number) value;
            if (number.doubleValue() < minValue) {
                return String.format("Field value is lesser than minimum value %f", minValue);
            }
        }
        return null;
    }

    @Override
    public Object constraintValue() {
        return this.minValue;
    }
}
