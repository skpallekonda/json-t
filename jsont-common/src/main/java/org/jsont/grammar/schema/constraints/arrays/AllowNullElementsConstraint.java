package org.jsont.grammar.schema.constraints.arrays;

import org.jsont.errors.ErrorLocation;
import org.jsont.errors.ValidationError;
import org.jsont.grammar.data.ArrayNode;
import org.jsont.grammar.data.NullNode;
import org.jsont.grammar.data.ValueNode;
import org.jsont.grammar.schema.constraints.FieldConstraint;

public class AllowNullElementsConstraint implements FieldConstraint {
    private final boolean allowNullElements;

    public AllowNullElementsConstraint(boolean allowNullElements) {
        this.allowNullElements = allowNullElements;
    }

    @Override
    public boolean checkConstraint(ValueNode node) {
        if (node instanceof ArrayNode) {
            ArrayNode arrayNode = (ArrayNode) node;
            if (!allowNullElements) {
                return arrayNode.elements().stream()
                        .filter(valueNode -> valueNode instanceof NullNode).count() == 0;
            }
            return true;
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
                    "Field array contains null elements",
                    String.valueOf(allowNullElements),
                    String.valueOf(
                            arrayNode.elements().stream().filter(valueNode -> valueNode instanceof NullNode)
                                    .count()));
        }
        return null;
    }
}
