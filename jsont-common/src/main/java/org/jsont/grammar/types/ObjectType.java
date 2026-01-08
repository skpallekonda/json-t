package org.jsont.grammar.types;

import org.jsont.grammar.data.JsontScalarType;
import org.jsont.grammar.data.RowNode;

import java.util.Objects;

public class ObjectType implements ValueType {
    private final String schema;
    private boolean optional;

    public ObjectType(String schema, boolean optional) {
        this.schema = schema;
        this.optional = optional;
    }

    public String schema() {
        return schema;
    }

    @Override
    public String name() {
        return "<" + schema + ">";
    }

    @Override
    public JsontScalarType valueType() {
        return JsontScalarType.OBJECT;
    }

    @Override
    public boolean isOptional() {
        return optional;
    }

    @Override
    public void setOptional(boolean b) {
        this.optional = b;
    }

    /**
     * Structural validation only.
     * Ensures the raw value is an object-shaped row.
     */
    @Override
    public void validateShape(Object raw) {
        if (raw == null) {
            return; // nullability handled in ValueType.validate()
        }
        Object value = null;
        if (raw instanceof RowNode) {
            value = raw;
        } else if (raw instanceof ValueHolder) {
            value = ((ValueHolder) raw).value();
        }

        if (value != null && !(value instanceof RowNode)) {
            throw new IllegalArgumentException(
                    "Expected object value (RowNode) for type " + name()
            );
        }
    }

    /**
     * Object-level constraints (rare).
     * Usually empty.
     */
    @Override
    public void validateConstraints(Object raw) {
        // no-op by default
        // example: object-level invariants if needed
    }

    @Override
    public boolean equals(Object obj) {
        if (null == obj || !(obj instanceof ObjectType)) {
            return false;
        } else if (this == obj) {
            return true;
        } else {
            ObjectType that = (ObjectType) obj;
            return Objects.equals(this.schema, that.schema) && this.optional == that.optional;
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(schema, optional);
    }
}
