package org.jsont.grammar.types;

import org.jsont.grammar.data.JsontScalarType;

public interface ValueType {
    /**
     * Human-readable name (int, string, Address, Address[], etc.)
     */
    String name();

    JsontScalarType valueType();

    /**
     * Entry point for validation.
     * Called exactly once per field value.
     */
    default void validate(Object raw) {
        checkNullability(raw);
        validateShape(raw);
        validateConstraints(raw);
    }

    /**
     * Whether null is allowed.
     */
    boolean isOptional();

    void setOptional(boolean b);

    /**
     * Null / presence check.
     */
    default void checkNullability(Object raw) {
        if (raw == null && !isOptional()) {
            throw new IllegalArgumentException(
                    "Null value not allowed for type " + name());
        }
    }

    /**
     * Structural validation (scalar / array / object).
     */
    void validateShape(Object raw);

    /**
     * Constraint validation (min, max, pattern, etc.).
     */
    default void validateConstraints(Object raw) {
        // no-op by default
    }
}
