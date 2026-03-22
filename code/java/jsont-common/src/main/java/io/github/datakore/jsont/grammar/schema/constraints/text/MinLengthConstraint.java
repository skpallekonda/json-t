package io.github.datakore.jsont.grammar.schema.constraints.text;

import io.github.datakore.jsont.errors.ErrorLocation;
import io.github.datakore.jsont.errors.ValidationError;
import io.github.datakore.jsont.grammar.data.ScalarNode;
import io.github.datakore.jsont.grammar.data.ValueNode;
import io.github.datakore.jsont.grammar.schema.constraints.BaseConstraint;

public class MinLengthConstraint extends BaseConstraint {
    private final int minLength;

    public MinLengthConstraint(ConstraitType constraitType, int minLength) {
        super(constraitType);
        this.minLength = minLength;
    }

    public int minLength() {
        return minLength;
    }

    @Override
    protected String checkConstraintScalar(Object value) {
        if (value instanceof String) {
            if (value.toString().length() < minLength) {
                return String.format("Field value length is lesser than maximum length %d", minLength);
            }
        }
        return null;
    }

    @Override
    public Object constraintValue() {
        return this.minLength;
    }
}
