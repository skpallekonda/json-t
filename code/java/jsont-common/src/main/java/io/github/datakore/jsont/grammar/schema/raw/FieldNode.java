package io.github.datakore.jsont.grammar.schema.raw;

import java.util.Collections;
import java.util.List;

public class FieldNode {
    private final String schemaName;
    private final String fieldName;
    private final FieldTypeNode typeRef;
    private final List<ConstraintNode> constraints;

    public FieldNode(String schemaName, String fieldName, FieldTypeNode typeRef, List<ConstraintNode> constraints) {
        this.schemaName = schemaName;
        this.fieldName = fieldName;
        this.typeRef = typeRef;
        this.constraints = constraints != null ? constraints : Collections.emptyList();
    }

    public String getSchemaName() {
        return schemaName;
    }

    public String getFieldName() {
        return fieldName;
    }

    public FieldTypeNode getTypeRef() {
        return typeRef;
    }

    public List<ConstraintNode> constraints() {
        return constraints;
    }
}
