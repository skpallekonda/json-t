package io.github.datakore.jsont.model;

/**
 * Classifies how a field's type is structured.
 *
 * <ul>
 *   <li>{@code SCALAR} — a single scalar value (e.g. {@code i32}, {@code str}).</li>
 *   <li>{@code OBJECT} — a single nested object (reference to another schema).</li>
 *   <li>{@code ARRAY_SCALAR} — an ordered list of scalar values.</li>
 *   <li>{@code ARRAY_OBJECT} — an ordered list of nested objects.</li>
 *   <li>{@code ANY_OF} — a union of two or more scalar or schema-ref variants.</li>
 *   <li>{@code ARRAY_ANY_OF} — an ordered list of anyOf union values.</li>
 * </ul>
 */
public enum FieldKind {

    SCALAR,
    OBJECT,
    ARRAY_SCALAR,
    ARRAY_OBJECT,
    ANY_OF,
    ARRAY_ANY_OF;

    /** Returns {@code true} for array variants ({@code ARRAY_SCALAR}, {@code ARRAY_OBJECT}, {@code ARRAY_ANY_OF}). */
    public boolean isArray() {
        return this == ARRAY_SCALAR || this == ARRAY_OBJECT || this == ARRAY_ANY_OF;
    }

    /** Returns {@code true} for {@code SCALAR} and {@code ARRAY_SCALAR}. */
    public boolean isScalar() {
        return this == SCALAR || this == ARRAY_SCALAR;
    }

    /** Returns {@code true} for {@code OBJECT} and {@code ARRAY_OBJECT}. */
    public boolean isObject() {
        return this == OBJECT || this == ARRAY_OBJECT;
    }

    /** Returns {@code true} for {@code ANY_OF} and {@code ARRAY_ANY_OF}. */
    public boolean isAnyOf() {
        return this == ANY_OF || this == ARRAY_ANY_OF;
    }
}
