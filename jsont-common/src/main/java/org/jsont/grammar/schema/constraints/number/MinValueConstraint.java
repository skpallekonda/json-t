package org.jsont.grammar.schema.constraints.number;

import org.jsont.errors.ErrorLocation;
import org.jsont.errors.ValidationError;
import org.jsont.grammar.data.ScalarNode;
import org.jsont.grammar.data.ValueNode;
import org.jsont.grammar.schema.constraints.FieldConstraint;

public class MinValueConstraint implements FieldConstraint {
    private final double minValue;

    public MinValueConstraint(double minValue) {
        this.minValue = minValue;
    }

    @Override
    public boolean checkConstraint(ValueNode node) {
        if (node instanceof ScalarNode) {
            ScalarNode scalarNode = (ScalarNode) node;
            return Double.parseDouble(scalarNode.raw()) >= minValue;
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
                    "Field value is lesser than minimum value",
                    String.valueOf(minValue),
                    scalarNode.raw());
        }
        return null;
    }
}
