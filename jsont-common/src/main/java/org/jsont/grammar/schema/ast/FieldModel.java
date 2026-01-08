package org.jsont.grammar.schema.ast;

import org.jsont.grammar.schema.constraints.FieldConstraint;
import org.jsont.grammar.types.ValueType;

import java.util.List;

public final class FieldModel {
    private final int position;
    private final String name;
    private final ValueType type;
    private final boolean optional;
    private final boolean arrays;
    private final List<FieldConstraint> constraints;

    public FieldModel(
            int position, String name, ValueType type, boolean optional, boolean arrays,
            List<FieldConstraint> constraints
    ) {
        this.position = position;
        this.name = name;
        this.type = type;
        this.optional = optional;
        this.arrays = arrays;
        this.constraints = constraints;
    }

    public int position() {
        return position;
    }

    public String name() {
        return name;
    }

    public boolean optional() {
        return optional;
    }

    public boolean arrays() {
        return arrays;
    }

    public List<FieldConstraint> constraints() {
        return constraints;
    }

    public ValueType type() {
        return type;
    }

}
