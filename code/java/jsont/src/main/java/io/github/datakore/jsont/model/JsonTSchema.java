package io.github.datakore.jsont.model;

import io.github.datakore.jsont.error.BuildError;
import io.github.datakore.jsont.internal.validate.scope.ScopeValidation;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable schema definition, either straight or derived. 
 * Please use {@link io.github.datakore.jsont.builder.JsonTSchemaBuilder} for creating instances.
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

    public List<JsonTField> fields() { return fields; }

    /** Empty for straight schemas. */
    public Optional<String> derivedFrom() { return Optional.ofNullable(derivedFrom); }

    /** Transformation operations, applied in order. Empty for straight schemas. */
    public List<SchemaOperation> operations() { return operations; }

    /** Uniqueness constraints and boolean rules. Empty for derived schemas. */
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

    /**
     * This validates the transformation pipeline against the parent schema. 
     * It checks for field existence, renames, and encryption states properly.
     */
    public void validateWithParent(JsonTSchema parent) throws BuildError {
        if (kind != SchemaKind.DERIVED) return;
        if (parent.kind != SchemaKind.STRAIGHT) return; // nested derived: skip for now
        ScopeValidation.validate(operations, parent.fields(), parent.name());
    }

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
