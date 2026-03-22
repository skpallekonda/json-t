package io.github.datakore.jsont.grammar.schema.ast;

import io.github.datakore.jsont.grammar.schema.constraints.FieldConstraint;
import io.github.datakore.jsont.grammar.types.ValueType;

import java.util.List;

public final class FieldModel {
    private final int fieldIndex;
    private final String fieldName;
    private final ValueType fieldType;
    private final boolean fieldOptional;
    private final List<FieldConstraint> constraints;
    private final String schemaName;

    public FieldModel(String schemaName, int position, String fieldName, ValueType fieldType, boolean fieldOptional,
                      List<FieldConstraint> constraints) {
        this.schemaName = schemaName;
        this.fieldIndex = position;
        this.fieldName = fieldName;
        this.fieldType = fieldType;
        this.fieldOptional = fieldOptional;
        this.constraints = constraints;
    }

    public String getSchema() {
        return schemaName;
    }

    public int getFieldIndex() {
        return fieldIndex;
    }

    public String getFieldName() {
        return fieldName;
    }

    public ValueType getFieldType() {
        return fieldType;
    }

    public boolean isFieldOptional() {
        return fieldOptional;
    }

    public List<FieldConstraint> getConstraints() {
        return constraints;
    }

    @Override
    public String toString() {
        /**
         * str: username?(minLength=5,maxLength='10')
         */
        StringBuilder sb = new StringBuilder();
        sb.append(fieldType);
        sb.append(": ");
        sb.append(fieldName);
        if (fieldOptional) {
            sb.append("?");
        }
        if (constraints != null && !constraints.isEmpty()) {
            sb.append("(");
            StringBuilder values = new StringBuilder();
            for (FieldConstraint value : constraints) {
                if (values.length() > 0) {
                    values.append(",");
                }
                values.append(value);
            }
            sb.append(values);
            sb.append(")");
        }
        return sb.toString();
    }
}