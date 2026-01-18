package io.github.datakore.jsont.grammar.schema.constraints;

import io.github.datakore.jsont.errors.ErrorLocation;
import io.github.datakore.jsont.errors.Severity;
import io.github.datakore.jsont.errors.ValidationError;
import io.github.datakore.jsont.grammar.schema.ast.FieldModel;

import java.util.List;
import java.util.Map;

public abstract class BaseConstraint implements FieldConstraint {

    private final ConstraitType type;

    public BaseConstraint(ConstraitType type) {
        this.type = type;
    }

    public abstract Object constraintValue();

    @Override
    @SuppressWarnings("unchecked")
    public String checkConstraint(Object value) {
        if (type == ConstraitType.MandatoryField && value == null) {
            return "Mandatory field is null";
        } else if (value == null) {
            return null;
        }
        if (value instanceof Map) {
            return checkConstraintMap((Map<String, Object>) value);
        } else if (value instanceof List) {
            return checkConstraintList((List<Object>) value);
        } else {
            return checkConstraintScalar(value);
        }
    }

    protected String checkConstraintScalar(Object value) {
        return null;
    }

    protected String checkConstraintList(List<Object> value) {
        return null;
    }

    protected String checkConstraintMap(Map<String, Object> value) {
        return null;
    }

    @Override
    public ValidationError makeError(int rowIndex, FieldModel field, String errorMessage) {
        ErrorLocation location = new ErrorLocation(rowIndex, field.getFieldIndex(), field.getFieldName(), field.getSchema());
        return new ValidationError(Severity.FIELD_ERROR, errorMessage, location);
    }

    @Override
    public String toString() {
        String name = null;
        if (!type.getIdentifier().isEmpty()) {
            name = type.getIdentifier().iterator().next();
        }
        return String.format("%s = %s", name, constraintValue());
    }
}
