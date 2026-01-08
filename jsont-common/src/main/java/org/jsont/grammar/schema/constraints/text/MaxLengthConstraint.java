package org.jsont.grammar.schema.constraints.text;

import org.jsont.errors.ErrorLocation;
import org.jsont.errors.ValidationError;
import org.jsont.grammar.data.ScalarNode;
import org.jsont.grammar.data.ValueNode;
import org.jsont.grammar.schema.constraints.FieldConstraint;

public class MaxLengthConstraint implements FieldConstraint {
    private final int maxLength;

    public MaxLengthConstraint(int maxLength) {
        this.maxLength = maxLength;
    }

    @Override
    public boolean checkConstraint(ValueNode node) {
        if (node instanceof ScalarNode) {
            ScalarNode scalarNode = (ScalarNode) node;
            return scalarNode.raw().length() <= maxLength;
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
                    "Field value length is greater than maximum length",
                    String.valueOf(maxLength),
                    String.valueOf(scalarNode.raw().length()));
        }
        return null;
    }
}
