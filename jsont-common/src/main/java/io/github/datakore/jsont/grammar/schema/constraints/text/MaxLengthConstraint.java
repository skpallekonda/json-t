package io.github.datakore.jsont.grammar.schema.constraints.text;

import io.github.datakore.jsont.grammar.schema.constraints.BaseConstraint;

public class MaxLengthConstraint extends BaseConstraint {
    public final int maxLength;

    public MaxLengthConstraint(ConstraitType constraitType, int maxLength) {
        super(constraitType);
        this.maxLength = maxLength;
    }

    @Override
    protected String checkConstraintScalar(Object value) {
        if (value instanceof String) {
            if (value.toString().length() > maxLength) {
                return String.format("Field value length is greater than maximum length %d", maxLength);
            }
        }
        return null;
    }

    @Override
    public Object constraintValue() {
        return this.maxLength;
    }
}
