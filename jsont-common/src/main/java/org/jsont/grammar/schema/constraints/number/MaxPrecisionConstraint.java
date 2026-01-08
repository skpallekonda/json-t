package org.jsont.grammar.schema.constraints.number;

import org.jsont.errors.ErrorLocation;
import org.jsont.errors.ValidationError;
import org.jsont.grammar.data.ScalarNode;
import org.jsont.grammar.data.ValueNode;
import org.jsont.grammar.schema.constraints.FieldConstraint;

import java.math.BigDecimal;

public class MaxPrecisionConstraint implements FieldConstraint {
    private final int maxPrecision;

    public MaxPrecisionConstraint(int maxPrecision) {
        this.maxPrecision = maxPrecision;
    }

    @Override
    public boolean checkConstraint(ValueNode node) {
        if (node instanceof ScalarNode) {
            ScalarNode scalarNode = (ScalarNode) node;
            try {
                BigDecimal bd = new BigDecimal(scalarNode.raw());
                return bd.scale() <= maxPrecision;
            } catch (NumberFormatException e) {
                return false; // Not a number
            }
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
                    "Field value precision is greater than maximum precision",
                    String.valueOf(maxPrecision),
                    scalarNode.raw());
        }
        return null;
    }
}
