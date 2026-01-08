package org.jsont.grammar.schema.constraints.number;

import org.jsont.errors.ErrorLocation;
import org.jsont.errors.ValidationError;
import org.jsont.grammar.data.ScalarNode;
import org.jsont.grammar.data.ValueNode;
import org.jsont.grammar.schema.constraints.FieldConstraint;

public class MaxValueConstraint implements FieldConstraint {
    private final double maxValue;

    public MaxValueConstraint(double maxValue) {
        this.maxValue = maxValue;
    }

    @Override
    public boolean checkConstraint(ValueNode node) {
        if (node instanceof ScalarNode) {
            ScalarNode scalarNode = (ScalarNode) node;
            return Double.parseDouble(scalarNode.raw()) <= maxValue;
        }
        return true; // For other types, ignore as true
    }

    @Override
    public ValidationError makeError(int rowIndex, String fieldName, ValueNode node) {
        if (node instanceof ScalarNode) {
            ScalarNode scalarNode = (ScalarNode) node;
            return new ValidationError(
                    ErrorLocation.withRow("Row ", rowIndex),
                    fieldName,
                    "Field value is greater than maximum value",
                    String.valueOf(maxValue),
                    scalarNode.raw());
        }
        return null;
    }
}
