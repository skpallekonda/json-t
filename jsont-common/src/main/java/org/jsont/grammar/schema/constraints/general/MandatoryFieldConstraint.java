package org.jsont.grammar.schema.constraints.general;

import org.jsont.errors.ErrorLocation;
import org.jsont.errors.ValidationError;
import org.jsont.grammar.data.NullNode;
import org.jsont.grammar.data.ValueNode;
import org.jsont.grammar.schema.constraints.FieldConstraint;

public class MandatoryFieldConstraint implements FieldConstraint {
    private final boolean mandatory;

    public MandatoryFieldConstraint(boolean mandatory) {
        this.mandatory = mandatory;
    }

    @Override
    public boolean checkConstraint(ValueNode node) {
        if (mandatory && node instanceof NullNode) {
            return false;
        }
        return true;
    }

    @Override
    public ValidationError makeError(int rowIndex, String fieldName, ValueNode node) {
        return new ValidationError(
                ErrorLocation.withRow("Row ", rowIndex),
                fieldName,
                "Mandatory field is null",
                "non-null",
                "null");
    }
}
