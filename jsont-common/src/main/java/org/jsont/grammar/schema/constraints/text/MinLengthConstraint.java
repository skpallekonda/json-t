package org.jsont.grammar.schema.constraints.text;

import org.jsont.errors.ErrorLocation;
import org.jsont.errors.ValidationError;
import org.jsont.grammar.data.ScalarNode;
import org.jsont.grammar.data.ValueNode;
import org.jsont.grammar.schema.constraints.FieldConstraint;

public class MinLengthConstraint implements FieldConstraint {
    private final int minLength;

    public MinLengthConstraint(int minLength) {
        this.minLength = minLength;
    }

    public int minLength() {
        return minLength;
    }

    @Override
    public boolean checkConstraint(ValueNode node) {
        if (node instanceof ScalarNode) {
            ScalarNode scalarNode = (ScalarNode) node;
            return scalarNode.raw().length() >= minLength;
        }
        return true;
    }

    @Override
    public ValidationError makeError(int rowIndex, String fieldName, ValueNode node) {
        if (node instanceof ScalarNode) {
            ScalarNode scalarNode = (ScalarNode) node;
            return new ValidationError(
                    ErrorLocation.withRow("Row ", rowIndex),
                    fieldName,
                    "Field value length is less than minimum length",
                    String.valueOf(minLength),
                    String.valueOf(scalarNode.raw().length()));
        }
        return null;
    }
}
