package io.github.datakore.jsont.model;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * An immutable schema definition — either <em>straight</em> (declares own fields)
 * or <em>derived</em> (projects/transforms another schema).
 *
 * <p>Instances are produced exclusively by {@code JsonTSchemaBuilder.build()}:
 *
 * <pre>{@code
 *   // Straight schema
 *   JsonTSchema order = JsonTSchemaBuilder.straight("Order")
 *       .fieldFrom(JsonTFieldBuilder.scalar("id",      ScalarType.I64))
 *       .fieldFrom(JsonTFieldBuilder.scalar("product", ScalarType.STR).minLength(2))
 *       .fieldFrom(JsonTFieldBuilder.scalar("qty",     ScalarType.I32).minValue(1).maxValue(999))
 *       .fieldFrom(JsonTFieldBuilder.scalar("price",   ScalarType.D64).minValue(0.01))
 *       .validationFrom(
 *           JsonTValidationBlockBuilder.create().unique(FieldPath.single("id")))
 *       .build();
 *
 *   // Derived schema
 *   JsonTSchema summary = JsonTSchemaBuilder.derived("OrderSummary", "Order")
 *       .operation(SchemaOperation.project(FieldPath.single("id"), FieldPath.single("product")))
 *       .build();
 * }</pre>
 */
public final class JsonTSchema {

    private final String name;
    private final SchemaKind kind;
    private final List<JsonTField> fields;        // non-empty for STRAIGHT, empty for DERIVED
    private final String derivedFrom;             // null for STRAIGHT
    private final List<SchemaOperation> operations; // empty for STRAIGHT
    private final JsonTValidationBlock validation;  // null means no validation block

    /** Use {@code JsonTSchemaBuilder} for validated construction. */
    public JsonTSchema(String name,
                SchemaKind kind,
                List<JsonTField> fields,
                String derivedFrom,
                List<SchemaOperation> operations,
                JsonTValidationBlock validation) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(kind, "kind");
        this.name = name;
        this.kind = kind;
        this.fields = List.copyOf(fields);
        this.derivedFrom = derivedFrom;
        this.operations = List.copyOf(operations);
        this.validation = validation;
    }

    // ─── Accessors ────────────────────────────────────────────────────────────

    /** Schema name — must be a valid {@code SCHEMAID} (starts with uppercase). */
    public String name() { return name; }

    /** Returns whether this is a straight or derived schema. */
    public SchemaKind kind() { return kind; }

    /**
     * Field list — non-empty for {@code STRAIGHT} schemas, always empty for {@code DERIVED}.
     */
    public List<JsonTField> fields() { return fields; }

    /**
     * The parent schema name this schema is derived from.
     *
     * @return {@link Optional#empty()} for straight schemas
     */
    public Optional<String> derivedFrom() { return Optional.ofNullable(derivedFrom); }

    /**
     * Ordered sequence of operations that transform the parent schema's rows.
     * Always empty for straight schemas.
     */
    public List<SchemaOperation> operations() { return operations; }

    /**
     * Optional validation block (uniqueness constraints + boolean rules).
     * Always empty for derived schemas.
     */
    public Optional<JsonTValidationBlock> validation() { return Optional.ofNullable(validation); }

    /** Returns {@code true} for straight schemas. */
    public boolean isStraight() { return kind == SchemaKind.STRAIGHT; }

    /** Returns {@code true} for derived schemas. */
    public boolean isDerived() { return kind == SchemaKind.DERIVED; }

    /**
     * Finds a field by name within a straight schema.
     *
     * @param fieldName the field name to look up
     * @return the matching field, or {@link Optional#empty()}
     */
    public Optional<JsonTField> findField(String fieldName) {
        return fields.stream().filter(f -> f.name().equals(fieldName)).findFirst();
    }

    /** Returns the number of fields in a straight schema (0 for derived). */
    public int fieldCount() { return fields.size(); }

    @Override
    public String toString() {
        if (kind == SchemaKind.DERIVED) {
            return "schema " + name + " derived " + derivedFrom + " { " + operations.size() + " operation(s) }";
        }
        return "schema " + name + " { " + fields.size() + " field(s) }";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof JsonTSchema s)) return false;
        return name.equals(s.name)
                && kind == s.kind
                && fields.equals(s.fields)
                && Objects.equals(derivedFrom, s.derivedFrom)
                && operations.equals(s.operations)
                && Objects.equals(validation, s.validation);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, kind, fields, derivedFrom, operations, validation);
    }
}
