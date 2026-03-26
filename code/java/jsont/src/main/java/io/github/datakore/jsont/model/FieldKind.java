package io.github.datakore.jsont.model;

/**
 * Classifies how a field's type is structured.
 *
 * <ul>
 *   <li>{@code SCALAR} — a single scalar value (e.g. {@code i32}, {@code str}).</li>
 *   <li>{@code OBJECT} — a single nested object (reference to another schema).</li>
 *   <li>{@code ARRAY_SCALAR} — an ordered list of scalar values.</li>
 *   <li>{@code ARRAY_OBJECT} — an ordered list of nested objects.</li>
 * </ul>
 */
public enum FieldKind {

    SCALAR,
    OBJECT,
    ARRAY_SCALAR,
    ARRAY_OBJECT;

    /** Returns {@code true} for {@code ARRAY_SCALAR} and {@code ARRAY_OBJECT}. */
    public boolean isArray() {
        return this == ARRAY_SCALAR || this == ARRAY_OBJECT;
    }

    /** Returns {@code true} for {@code SCALAR} and {@code ARRAY_SCALAR}. */
    public boolean isScalar() {
        return this == SCALAR || this == ARRAY_SCALAR;
    }

    /** Returns {@code true} for {@code OBJECT} and {@code ARRAY_OBJECT}. */
    public boolean isObject() {
        return this == OBJECT || this == ARRAY_OBJECT;
    }
}
