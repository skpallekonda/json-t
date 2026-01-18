package io.github.datakore.jsont.validator;

import io.github.datakore.jsont.errors.ErrorLocation;
import io.github.datakore.jsont.errors.Severity;
import io.github.datakore.jsont.errors.ValidationError;
import io.github.datakore.jsont.errors.collector.ErrorCollector;
import io.github.datakore.jsont.exception.DataException;
import io.github.datakore.jsont.grammar.schema.ast.FieldModel;
import io.github.datakore.jsont.grammar.schema.ast.NamespaceT;
import io.github.datakore.jsont.grammar.schema.ast.SchemaModel;
import io.github.datakore.jsont.grammar.schema.constraints.FieldConstraint;
import io.github.datakore.jsont.grammar.types.ObjectType;
import io.github.datakore.jsont.util.StringUtils;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class SchemaValidator {

    private final NamespaceT namespace;
    private final ErrorCollector errorCollector;

    public SchemaValidator(NamespaceT namespace, ErrorCollector errorCollector) {
        this.namespace = namespace;
        this.errorCollector = errorCollector;
    }

    public void validate(SchemaModel schema, int rowIndex, Map<String, Object> rowObject) {
        if (rowObject == null) return;
        AtomicInteger errorCount = new AtomicInteger(0);
        validateInternal(schema, rowIndex, rowObject, errorCount);
        if (errorCount.get() > 0) {
            throw new DataException("Constraint Check Failed");
        }
    }

    @SuppressWarnings("unchecked")
    private void validateInternal(SchemaModel schema, int rowIndex, Map<String, Object> rowObject, AtomicInteger errorCount) {
        for (FieldModel field : schema.fields()) {
            Object value = rowObject.get(field.getFieldName());

            // 1. Check Mandatory Fields
            if (value == null && !field.isFieldOptional()) {
                reportError(rowIndex, field, "Mandatory field is missing or null");
                errorCount.incrementAndGet();
                continue;
            }

            if (value == null) continue;

            // 2. Validate Constraints
            if (field.getConstraints() != null) {
                for (FieldConstraint constraint : field.getConstraints()) {
                    String errorMessage = constraint.checkConstraint(value);
                    if (!StringUtils.isBlank(errorMessage)) {
                        ValidationError error = constraint.makeError(rowIndex, field, errorMessage);
                        errorCollector.report(error);
                        errorCount.incrementAndGet();
                    }
                }
            }

            // 3. Recursive Validation for Nested Objects
            if (field.getFieldType() instanceof ObjectType && value instanceof Map) {
                ObjectType objectType = (ObjectType) field.getFieldType();
                SchemaModel nestedSchema = namespace.findSchema(objectType.type());
                if (nestedSchema != null) {
                    validateInternal(nestedSchema, rowIndex, (Map<String, Object>) value, errorCount);
                }
            }
        }
    }

    private void reportError(int rowIndex, FieldModel field, String message) {
        errorCollector.report(new ValidationError(
                Severity.FIELD_ERROR,
                message,
                new ErrorLocation(rowIndex, field.getFieldIndex(), field.getFieldName(), field.getSchema())
        ));
    }
}
