package io.github.datakore.jsont.model;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * An immutable field definition within a straight schema.
 *
 * <pre>{@code
 *   JsonTField id = JsonTFieldBuilder.scalar("id", ScalarType.I64).build();
 *   //  id.name()        -> "id"
 *   //  id.kind()        -> SCALAR
 *   //  id.scalarType()  -> I64
 *   //  id.optional()    -> false
 *
 *   JsonTField payload = JsonTFieldBuilder.anyOf("payload",
 *       List.of(AnyOfVariant.schemaRef("Person"), AnyOfVariant.schemaRef("Customer")))
 *       .discriminator("type").build();
 *   //  payload.kind()           -> ANY_OF
 *   //  payload.anyOfVariants()  -> [SchemaRef("Person"), SchemaRef("Customer")]
 *   //  payload.discriminator()  -> "type"
 * }</pre>
 */
public final class JsonTField {

    private final String name;
    private final FieldKind kind;
    private final ScalarType scalarType;          // non-null when kind.isScalar()
    private final String objectRef;               // non-null when kind.isObject()
    private final List<AnyOfVariant> anyOfVariants; // non-null when kind.isAnyOf()
    private final String discriminator;           // non-null when anyOf with multi-object dispatch
    private final boolean optional;
    private final boolean sensitive;              // true only for scalar fields with ~ marker
    private final FieldConstraints constraints;

    /** Use {@code JsonTFieldBuilder} for validated construction. Scalar / object fields. */
    public JsonTField(String name,
                      FieldKind kind,
                      ScalarType scalarType,
                      String objectRef,
                      boolean optional,
                      FieldConstraints constraints) {
        this(name, kind, scalarType, objectRef, null, null, optional, false, constraints);
    }

    /** Use {@code JsonTFieldBuilder} for validated construction. AnyOf fields. */
    public JsonTField(String name,
                      FieldKind kind,
                      List<AnyOfVariant> anyOfVariants,
                      String discriminator,
                      boolean optional,
                      FieldConstraints constraints) {
        this(name, kind, null, null, anyOfVariants, discriminator, optional, false, constraints);
    }

    /** Use {@code JsonTFieldBuilder} for validated construction. Scalar fields with sensitivity. */
    public JsonTField(String name,
                      FieldKind kind,
                      ScalarType scalarType,
                      String objectRef,
                      boolean optional,
                      boolean sensitive,
                      FieldConstraints constraints) {
        this(name, kind, scalarType, objectRef, null, null, optional, sensitive, constraints);
    }

    private JsonTField(String name,
                       FieldKind kind,
                       ScalarType scalarType,
                       String objectRef,
                       List<AnyOfVariant> anyOfVariants,
                       String discriminator,
                       boolean optional,
                       boolean sensitive,
                       FieldConstraints constraints) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(constraints, "constraints");
        if (kind.isScalar() && scalarType == null)
            throw new IllegalArgumentException("scalarType is required for kind " + kind);
        if (kind.isObject() && (objectRef == null || objectRef.isBlank()))
            throw new IllegalArgumentException("objectRef is required for kind " + kind);
        if (kind.isAnyOf() && (anyOfVariants == null || anyOfVariants.size() < 2))
            throw new IllegalArgumentException("anyOf field '" + name + "' requires at least 2 variants");
        this.name = name;
        this.kind = kind;
        this.scalarType = scalarType;
        this.objectRef = objectRef;
        this.anyOfVariants = anyOfVariants == null ? null : List.copyOf(anyOfVariants);
        this.discriminator = discriminator;
        this.optional = optional;
        this.sensitive = sensitive;
        this.constraints = constraints;
    }

    /** Field name as declared in the schema. */
    public String name() { return name; }

    /** Structural classification of the field's type. */
    public FieldKind kind() { return kind; }

    /**
     * Scalar type — only valid when {@link #kind()} is {@code SCALAR} or {@code ARRAY_SCALAR}.
     *
     * @throws UnsupportedOperationException for non-scalar fields
     */
    public ScalarType scalarType() {
        if (scalarType == null)
            throw new UnsupportedOperationException("Field '" + name + "' is not a scalar field");
        return scalarType;
    }

    /**
     * Schema reference name — only valid when {@link #kind()} is {@code OBJECT} or {@code ARRAY_OBJECT}.
     *
     * @throws UnsupportedOperationException for non-object fields
     */
    public String objectRef() {
        if (objectRef == null)
            throw new UnsupportedOperationException("Field '" + name + "' is not an object field");
        return objectRef;
    }

    /**
     * Union variants — only valid when {@link #kind()} is {@code ANY_OF} or {@code ARRAY_ANY_OF}.
     *
     * @throws UnsupportedOperationException for non-anyOf fields
     */
    public List<AnyOfVariant> anyOfVariants() {
        if (anyOfVariants == null)
            throw new UnsupportedOperationException("Field '" + name + "' is not an anyOf field");
        return anyOfVariants;
    }

    /**
     * Discriminator field name used to dispatch between multiple object variants.
     * Returns {@code null} when no discriminator is declared.
     * Only relevant when {@link #kind()} is {@code ANY_OF} or {@code ARRAY_ANY_OF}.
     */
    public String discriminator() { return discriminator; }

    /** Returns {@code true} if the field may be absent or null in a row. */
    public boolean optional() { return optional; }

    /**
     * Returns {@code true} if this is a sensitive scalar field.
     *
     * <p>Sensitive fields carry encrypted values on the wire ({@code base64:xxx}).
     * Only valid for scalar fields — always {@code false} for object and anyOf fields.
     */
    public boolean sensitive() { return sensitive; }

    /** Constraint set for this field (never {@code null}; use {@link FieldConstraints#hasAny()}). */
    public FieldConstraints constraints() { return constraints; }

    @Override
    public String toString() {
        String typeStr;
        if (kind.isScalar()) {
            typeStr = scalarType.keyword() + (kind.isArray() ? "[]" : "");
        } else if (kind.isAnyOf()) {
            String variants = anyOfVariants.stream()
                    .map(v -> v instanceof AnyOfVariant.Scalar s
                            ? s.type().keyword()
                            : "<" + ((AnyOfVariant.SchemaRef) v).name() + ">")
                    .collect(Collectors.joining(" | "));
            typeStr = "anyOf(" + variants + ")" + (kind.isArray() ? "[]" : "");
        } else {
            typeStr = "<" + objectRef + ">" + (kind.isArray() ? "[]" : "");
        }
        return name + (optional ? "?" : "") + ": " + typeStr;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof JsonTField f)) return false;
        return optional == f.optional
                && sensitive == f.sensitive
                && name.equals(f.name)
                && kind == f.kind
                && Objects.equals(scalarType, f.scalarType)
                && Objects.equals(objectRef, f.objectRef)
                && Objects.equals(anyOfVariants, f.anyOfVariants)
                && Objects.equals(discriminator, f.discriminator)
                && constraints.equals(f.constraints);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, kind, scalarType, objectRef, anyOfVariants, discriminator, optional, sensitive, constraints);
    }
}
