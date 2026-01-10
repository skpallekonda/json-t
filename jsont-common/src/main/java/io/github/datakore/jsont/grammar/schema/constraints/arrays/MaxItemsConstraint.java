package io.github.datakore.jsont.grammar.schema.constraints.arrays;

import io.github.datakore.jsont.errors.ErrorLocation;
import io.github.datakore.jsont.errors.ValidationError;
import io.github.datakore.jsont.grammar.data.ArrayNode;
import io.github.datakore.jsont.grammar.data.ValueNode;
import io.github.datakore.jsont.grammar.schema.constraints.FieldConstraint;

public class MaxItemsConstraint implements FieldConstraint {
    private final int maxItems;

    public MaxItemsConstraint(int maxItems) {
        this.maxItems = maxItems;
    }

    @Override
    public boolean checkConstraint(ValueNode node) {
        if (node instanceof ArrayNode) {
            ArrayNode arrayNode = (ArrayNode) node;
            return arrayNode.elements().size() <= maxItems;
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
                    "Field array size is more than maximum value",
                    String.valueOf(maxItems),
                    String.valueOf(arrayNode.elements().size()));
        }
        return null;
    }
}
