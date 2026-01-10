package io.github.datakore.jsont.grammar.schema.constraints.text;

import io.github.datakore.jsont.errors.ErrorLocation;
import io.github.datakore.jsont.errors.ValidationError;
import io.github.datakore.jsont.grammar.data.ScalarNode;
import io.github.datakore.jsont.grammar.data.ValueNode;
import io.github.datakore.jsont.grammar.schema.constraints.FieldConstraint;

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
