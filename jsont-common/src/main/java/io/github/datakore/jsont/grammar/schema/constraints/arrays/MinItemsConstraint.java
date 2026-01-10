package io.github.datakore.jsont.grammar.schema.constraints.arrays;

import io.github.datakore.jsont.errors.ErrorLocation;
import io.github.datakore.jsont.errors.ValidationError;
import io.github.datakore.jsont.grammar.data.ArrayNode;
import io.github.datakore.jsont.grammar.data.ValueNode;
import io.github.datakore.jsont.grammar.schema.constraints.FieldConstraint;

public class MinItemsConstraint implements FieldConstraint {
    private final int minItems;

    public MinItemsConstraint(int minItems) {
        this.minItems = minItems;
    }

    @Override
    public boolean checkConstraint(ValueNode node) {
        if (node instanceof ArrayNode) {
            ArrayNode arrayNode = (ArrayNode) node;
            return arrayNode.elements().size() >= minItems;
        }
        return true; // For other types, ignore as true
    }

    @Override
    public ValidationError makeError(int rowIndex, String fieldName, ValueNode node) {
        if (node instanceof ArrayNode) {
            ArrayNode arrayNode = (ArrayNode) node;
            return new ValidationError(
                    ErrorLocation.withRow("Row ", rowIndex),
                    fieldName,
                    "Field array size is less than minimum value",
                    String.valueOf(minItems),
                    String.valueOf(arrayNode.elements().size()));
        }
        return null;
    }
}
