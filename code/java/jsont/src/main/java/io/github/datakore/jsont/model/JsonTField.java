package io.github.datakore.jsont.model;

import java.util.Objects;

/**
 * An immutable field definition within a straight schema.
 *
 * <pre>{@code
 *   JsonTField id = JsonTFieldBuilder.scalar("id", ScalarType.I64).build();
 *   //  id.name()        -> "id"
 *   //  id.kind()        -> SCALAR
 *   //  id.scalarType()  -> I64
 *   //  id.optional()    -> false
 * }</pre>
 */
public final class JsonTField {

    private final String name;
    private final FieldKind kind;
    private final ScalarType scalarType;   // non-null when kind.isScalar()
    private final String objectRef;        // non-null when kind.isObject()
    private final boolean optional;
    private final FieldConstraints constraints;

    /** Use {@code JsonTFieldBuilder} for validated construction. */
    public JsonTField(String name,
               FieldKind kind,
               ScalarType scalarType,
               String objectRef,
               boolean optional,
               FieldConstraints constraints) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(constraints, "constraints");
        if (kind.isScalar() && scalarType == null)
            throw new IllegalArgumentException("scalarType is required for kind " + kind);
        if (kind.isObject() && (objectRef == null || objectRef.isBlank()))
            throw new IllegalArgumentException("objectRef is required for kind " + kind);
        this.name = name;
        this.kind = kind;
        this.scalarType = scalarType;
        this.objectRef = objectRef;
        this.optional = optional;
        this.constraints = constraints;
    }

    /** Field name as declared in the schema. */
    public String name() { return name; }

    /** Structural classification of the field's type. */
    public FieldKind kind() { return kind; }

    /**
     * Scalar type — only valid when {@link #kind()} is {@code SCALAR} or {@code ARRAY_SCALAR}.
     *
     * @throws UnsupportedOperationException for object fields
     */
    public ScalarType scalarType() {
        if (scalarType == null)
            throw new UnsupportedOperationException("Field '" + name + "' is not a scalar field");
        return scalarType;
    }

    /**
     * Schema reference name — only valid when {@link #kind()} is {@code OBJECT} or {@code ARRAY_OBJECT}.
     *
     * @throws UnsupportedOperationException for scalar fields
     */
    public String objectRef() {
        if (objectRef == null)
            throw new UnsupportedOperationException("Field '" + name + "' is not an object field");
        return objectRef;
    }

    /** Returns {@code true} if the field may be absent or null in a row. */
    public boolean optional() { return optional; }

    /** Constraint set for this field (never {@code null}; use {@link FieldConstraints#hasAny()}). */
    public FieldConstraints constraints() { return constraints; }

    @Override
    public String toString() {
        String typeStr = kind.isScalar()
                ? scalarType.keyword() + (kind.isArray() ? "[]" : "")
                : "<" + objectRef + ">" + (kind.isArray() ? "[]" : "");
        return name + (optional ? "?" : "") + ": " + typeStr;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof JsonTField f)) return false;
        return optional == f.optional
                && name.equals(f.name)
                && kind == f.kind
                && Objects.equals(scalarType, f.scalarType)
                && Objects.equals(objectRef, f.objectRef)
                && constraints.equals(f.constraints);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, kind, scalarType, objectRef, optional, constraints);
    }
}
