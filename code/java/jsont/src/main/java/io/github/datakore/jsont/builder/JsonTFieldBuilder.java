package io.github.datakore.jsont.builder;

import io.github.datakore.jsont.error.BuildError;
import io.github.datakore.jsont.model.AnyOfVariant;
import io.github.datakore.jsont.model.FieldConstraints;
import io.github.datakore.jsont.model.FieldKind;
import io.github.datakore.jsont.model.JsonTField;
import io.github.datakore.jsont.model.JsonTValue;
import io.github.datakore.jsont.model.ScalarType;

import java.util.List;

/**
 * Fluent builder for {@link JsonTField}.
 *
 * <p>Start with the appropriate factory, then chain constraint methods, and pass
 * the builder to {@link JsonTSchemaBuilder#fieldFrom}:
 *
 * <pre>{@code
 *   // Scalar field with constraints
 *   JsonTFieldBuilder.scalar("price", ScalarType.D64)
 *       .minValue(0.01)
 *       .maxPrecision(2)
 *
 *   // Optional object array
 *   JsonTFieldBuilder.object("tags", "Tag")
 *       .asArray()
 *       .optional()
 *       .minItems(1)
 *       .maxItems(20)
 * }</pre>
 */
public final class JsonTFieldBuilder {

    private final String name;
    private FieldKind kind;
    private final ScalarType scalarType;
    private final String objectRef;
    private final List<AnyOfVariant> anyOfVariants; // non-null for ANY_OF
    private String discriminatorField;

    // modifiers
    private boolean optional = false;
    private boolean sensitive = false;

    // numeric constraints
    private Double minValue;
    private Double maxValue;

    // string constraints
    private Integer minLength;
    private Integer maxLength;
    private String pattern;

    // general
    private boolean required = false;

    // decimal
    private Integer maxPrecision;

    // array
    private Integer minItems;
    private Integer maxItems;
    private boolean allowNullElements = false;
    private Integer maxNullElements;

    // constant
    private JsonTValue constantValue;

    private JsonTFieldBuilder(String name, FieldKind kind, ScalarType scalarType, String objectRef) {
        this.name = name;
        this.kind = kind;
        this.scalarType = scalarType;
        this.objectRef = objectRef;
        this.anyOfVariants = null;
    }

    private JsonTFieldBuilder(String name, FieldKind kind, List<AnyOfVariant> anyOfVariants) {
        this.name = name;
        this.kind = kind;
        this.scalarType = null;
        this.objectRef = null;
        this.anyOfVariants = List.copyOf(anyOfVariants);
    }

    // ─── Factory methods ──────────────────────────────────────────────────────

    /**
     * Declares a scalar field of the given type.
     *
     * @param name  field name (must start with lowercase)
     * @param type  scalar type keyword
     */
    public static JsonTFieldBuilder scalar(String name, ScalarType type) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name must not be blank");
        if (type == null) throw new IllegalArgumentException("type must not be null");
        return new JsonTFieldBuilder(name, FieldKind.SCALAR, type, null);
    }

    /**
     * Declares an object field referencing another schema.
     *
     * @param name      field name
     * @param schemaRef the referenced schema's name (must start with uppercase)
     */
    public static JsonTFieldBuilder object(String name, String schemaRef) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name must not be blank");
        if (schemaRef == null || schemaRef.isBlank()) throw new IllegalArgumentException("schemaRef must not be blank");
        return new JsonTFieldBuilder(name, FieldKind.OBJECT, null, schemaRef);
    }

    /**
     * Declares an {@code anyOf} field accepting two or more scalar or schema-ref variants.
     *
     * <p>Variants are tried left-to-right within each JSON token-type category; the first
     * match wins.  When two or more variants are full object schemas (not enums), use
     * {@link #discriminator(String)} to specify the field that resolves ambiguity.
     *
     * @param name     field name
     * @param variants at least two {@link AnyOfVariant} arms
     */
    public static JsonTFieldBuilder anyOf(String name, List<AnyOfVariant> variants) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name must not be blank");
        if (variants == null || variants.size() < 2)
            throw new IllegalArgumentException("anyOf field '" + name + "' requires at least 2 variants");
        return new JsonTFieldBuilder(name, FieldKind.ANY_OF, variants);
    }

    // ─── Modifiers ────────────────────────────────────────────────────────────

    /** Marks the field as optional (may be absent or null in a row). */
    public JsonTFieldBuilder optional() {
        this.optional = true;
        return this;
    }

    /**
     * Marks this field as sensitive (encrypted on the wire).
     *
     * <p>Only valid on scalar fields. Calling this on an {@code object} or
     * {@code anyOf} field causes {@link #build()} to throw a {@link BuildError}.
     */
    public JsonTFieldBuilder sensitive() {
        this.sensitive = true;
        return this;
    }

    /** Promotes this field to an array type. Idempotent. */
    public JsonTFieldBuilder asArray() {
        this.kind = switch (kind) {
            case SCALAR      -> FieldKind.ARRAY_SCALAR;
            case OBJECT      -> FieldKind.ARRAY_OBJECT;
            case ANY_OF      -> FieldKind.ARRAY_ANY_OF;
            case ARRAY_SCALAR, ARRAY_OBJECT, ARRAY_ANY_OF -> kind; // idempotent
        };
        return this;
    }

    /**
     * Sets the discriminator field name for multi-object {@code anyOf} dispatch.
     * Required when two or more variants are full object schemas (not enums).
     *
     * @param fieldName the name of the field present in all object variants
     */
    public JsonTFieldBuilder discriminator(String fieldName) {
        this.discriminatorField = fieldName;
        return this;
    }

    // ─── Numeric constraints ──────────────────────────────────────────────────

    /** Minimum numeric value (inclusive). Applies to numeric scalar types. */
    public JsonTFieldBuilder minValue(double min) {
        this.minValue = min;
        return this;
    }

    /** Maximum numeric value (inclusive). Applies to numeric scalar types. */
    public JsonTFieldBuilder maxValue(double max) {
        this.maxValue = max;
        return this;
    }

    // ─── String constraints ───────────────────────────────────────────────────

    /** Minimum string length (inclusive). Applies to string-like scalar types. */
    public JsonTFieldBuilder minLength(int min) {
        this.minLength = min;
        return this;
    }

    /** Maximum string length (inclusive). Applies to string-like scalar types. */
    public JsonTFieldBuilder maxLength(int max) {
        this.maxLength = max;
        return this;
    }

    /**
     * Regex pattern the field value must match.
     * Applies to string-like scalar types.
     */
    public JsonTFieldBuilder pattern(String regex) {
        this.pattern = regex;
        return this;
    }

    // ─── General constraints ──────────────────────────────────────────────────

    /** Field is required even when declared optional (must be non-null). */
    public JsonTFieldBuilder required() {
        this.required = true;
        return this;
    }

    // ─── Decimal constraints ──────────────────────────────────────────────────

    /** Maximum number of fractional digits. Applies to {@code D32}, {@code D64}, {@code D128}. */
    public JsonTFieldBuilder maxPrecision(int digits) {
        this.maxPrecision = digits;
        return this;
    }

    // ─── Array constraints ────────────────────────────────────────────────────

    /** Minimum number of elements in an array field (inclusive). */
    public JsonTFieldBuilder minItems(int n) {
        this.minItems = n;
        return this;
    }

    /** Maximum number of elements in an array field (inclusive). */
    public JsonTFieldBuilder maxItems(int n) {
        this.maxItems = n;
        return this;
    }

    /** Array elements are allowed to be null. */
    public JsonTFieldBuilder allowNullElements() {
        this.allowNullElements = true;
        return this;
    }

    /** Maximum number of null elements permitted in an array. */
    public JsonTFieldBuilder maxNullElements(int n) {
        this.maxNullElements = n;
        return this;
    }

    // ─── Constant constraint ──────────────────────────────────────────────────

    /**
     * Field must always equal this exact value. Any mismatch produces a Fatal diagnostic.
     * Useful for schema versioning fields (e.g. {@code version = "v1"}).
     */
    public JsonTFieldBuilder constantValue(JsonTValue value) {
        this.constantValue = value;
        return this;
    }

    // ─── Build ────────────────────────────────────────────────────────────────

    /** @throws BuildError if the configuration is invalid */
    JsonTField build() throws BuildError {
        if (name == null || name.isBlank())
            throw new BuildError("Field name must not be blank");
        if (kind.isScalar() && scalarType == null)
            throw new BuildError("Scalar field '" + name + "' requires a ScalarType");
        if (kind.isObject() && (objectRef == null || objectRef.isBlank()))
            throw new BuildError("Object field '" + name + "' requires a schema reference");
        if (kind.isAnyOf() && (anyOfVariants == null || anyOfVariants.size() < 2))
            throw new BuildError("anyOf field '" + name + "' requires at least 2 variants");
        if (minValue != null && maxValue != null && minValue > maxValue)
            throw new BuildError("Field '" + name + "': minValue (" + minValue + ") > maxValue (" + maxValue + ")");
        if (minLength != null && maxLength != null && minLength > maxLength)
            throw new BuildError("Field '" + name + "': minLength (" + minLength + ") > maxLength (" + maxLength + ")");
        if (minItems != null && maxItems != null && minItems > maxItems)
            throw new BuildError("Field '" + name + "': minItems (" + minItems + ") > maxItems (" + maxItems + ")");
        if (maxPrecision != null && maxPrecision < 0)
            throw new BuildError("Field '" + name + "': maxPrecision must be >= 0");
        if (sensitive && !kind.isScalar())
            throw new BuildError("Field '" + name + "': sensitive() is only valid on scalar fields, not " + kind);

        FieldConstraints constraints = new FieldConstraints(
                minValue, maxValue,
                minLength, maxLength,
                pattern,
                required,
                maxPrecision,
                minItems, maxItems,
                allowNullElements, maxNullElements,
                constantValue
        );

        if (kind.isAnyOf()) {
            return new JsonTField(name, kind, anyOfVariants, discriminatorField, optional, constraints);
        }
        return new JsonTField(name, kind, scalarType, objectRef, optional, sensitive, constraints);
    }
}
