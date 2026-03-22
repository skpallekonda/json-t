package io.github.datakore.jsont.grammar.schema.constraints.number;

import io.github.datakore.jsont.grammar.schema.constraints.BaseConstraint;

import java.math.BigDecimal;

public class MaxPrecisionConstraint extends BaseConstraint {
    private final int maxPrecision;

    public MaxPrecisionConstraint(ConstraitType constraitType, int maxPrecision) {
        super(constraitType);
        this.maxPrecision = maxPrecision;
    }

    public int getMaxPrecision() {
        return maxPrecision;
    }

    @Override
    protected String checkConstraintScalar(Object value) {
        if (value instanceof Number) {
            BigDecimal bd;
            if (value instanceof BigDecimal) {
                bd = (BigDecimal) value;
            } else if (value instanceof Double || value instanceof Float) {
                // Use String constructor to avoid precision issues with double
                bd = new BigDecimal(value.toString());
            } else {
                // Integers have scale 0, so they always pass unless maxPrecision < 0 (unlikely)
                return null;
            }

            // stripTrailingZeros is crucial because 1.500 has scale 3, but effectively 1.5 (scale 1)
            if (bd.stripTrailingZeros().scale() > maxPrecision) {
                return String.format("Field value precision is greater than maximum precision %d", maxPrecision);
            }
        }
        return null;
    }

    @Override
    public Object constraintValue() {
        return this.maxPrecision;
    }
}
