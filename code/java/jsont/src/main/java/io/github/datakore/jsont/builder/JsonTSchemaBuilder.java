package io.github.datakore.jsont.builder;

import io.github.datakore.jsont.error.BuildError;
import io.github.datakore.jsont.model.JsonTField;
import io.github.datakore.jsont.model.JsonTSchema;
import io.github.datakore.jsont.model.JsonTValidationBlock;
import io.github.datakore.jsont.model.SchemaKind;
import io.github.datakore.jsont.model.SchemaOperation;

import java.util.ArrayList;
import java.util.List;

/**
 * Fluent builder for {@link JsonTSchema}.
 *
 * <p>Use {@link #straight} for schemas that declare their own fields, or
 * {@link #derived} for schemas that project/transform a parent schema.
 *
 * <pre>{@code
 *   // Straight schema
 *   JsonTSchema order = JsonTSchemaBuilder.straight("Order")
 *       .fieldFrom(JsonTFieldBuilder.scalar("id",      ScalarType.I64))
 *       .fieldFrom(JsonTFieldBuilder.scalar("product", ScalarType.STR).minLength(2))
 *       .fieldFrom(JsonTFieldBuilder.scalar("qty",     ScalarType.I32).minValue(1).maxValue(999))
 *       .fieldFrom(JsonTFieldBuilder.scalar("price",   ScalarType.D64).minValue(0.01))
 *       .validationFrom(
 *           JsonTValidationBlockBuilder.create().unique("id"))
 *       .build();
 *
 *   // Derived schema — keeps only id and product
 *   JsonTSchema summary = JsonTSchemaBuilder.derived("OrderSummary", "Order")
 *       .operation(SchemaOperation.project(
 *           FieldPath.single("id"), FieldPath.single("product")))
 *       .build();
 * }</pre>
 */
public final class JsonTSchemaBuilder {

    private final String name;
    private final SchemaKind kind;
    private final String derivedFrom; // null for STRAIGHT

    private final List<JsonTField> fields = new ArrayList<>();
    private final List<SchemaOperation> operations = new ArrayList<>();
    private JsonTValidationBlock validation;

    private JsonTSchemaBuilder(String name, SchemaKind kind, String derivedFrom) {
        this.name = name;
        this.kind = kind;
        this.derivedFrom = derivedFrom;
    }

    // ─── Factory methods ──────────────────────────────────────────────────────

    /**
     * Begins a straight schema that declares its own fields.
     *
     * @param name schema name — must start with an uppercase letter
     */
    public static JsonTSchemaBuilder straight(String name) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("schema name must not be blank");
        return new JsonTSchemaBuilder(name, SchemaKind.STRAIGHT, null);
    }

    /**
     * Begins a derived schema that applies operations to {@code from}.
     *
     * @param name schema name — must start with an uppercase letter
     * @param from the parent schema name to derive from
     */
    public static JsonTSchemaBuilder derived(String name, String from) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("schema name must not be blank");
        if (from == null || from.isBlank()) throw new IllegalArgumentException("parent schema name must not be blank");
        return new JsonTSchemaBuilder(name, SchemaKind.DERIVED, from);
    }

    // ─── Straight-schema methods ──────────────────────────────────────────────

    /**
     * Builds and adds a field from the supplied builder.
     *
     * @param builder field builder (constraints already configured)
     * @return this builder
     * @throws BuildError if this is a derived schema, or if the field is misconfigured
     */
    public JsonTSchemaBuilder fieldFrom(JsonTFieldBuilder builder) throws BuildError {
        if (kind == SchemaKind.DERIVED)
            throw new BuildError("Cannot add fields to derived schema '" + name + "' — use operation() instead");
        if (builder == null) throw new BuildError("Field builder must not be null");
        fields.add(builder.build());
        return this;
    }

    /**
     * Attaches a validation block (uniqueness keys + rules).
     *
     * @param builder validation block builder (already configured)
     * @return this builder
     * @throws BuildError if this is a derived schema, or if the block is misconfigured
     */
    public JsonTSchemaBuilder validationFrom(JsonTValidationBlockBuilder builder) throws BuildError {
        if (kind == SchemaKind.DERIVED)
            throw new BuildError("Cannot add a validation block to derived schema '" + name + "'");
        if (builder == null) throw new BuildError("Validation block builder must not be null");
        this.validation = builder.build();
        return this;
    }

    // ─── Derived-schema methods ───────────────────────────────────────────────

    /**
     * Appends a schema operation (rename, exclude, project, filter, transform).
     * Operations are applied in declaration order when a row is transformed.
     *
     * @param op the operation to append
     * @return this builder
     * @throws BuildError if this is a straight schema
     */
    public JsonTSchemaBuilder operation(SchemaOperation op) throws BuildError {
        if (kind == SchemaKind.STRAIGHT)
            throw new BuildError("Cannot add operations to straight schema '" + name + "' — use fieldFrom() instead");
        if (op == null) throw new BuildError("SchemaOperation must not be null");
        operations.add(op);
        return this;
    }

    // ─── Build ────────────────────────────────────────────────────────────────

    /**
     * Validates state and constructs an immutable {@link JsonTSchema}.
     *
     * @throws BuildError if the schema is misconfigured:
     *   <ul>
     *     <li>Straight: no fields declared</li>
     *     <li>Derived: no parent name, or duplicate field declarations</li>
     *   </ul>
     */
    public JsonTSchema build() throws BuildError {
        validateName();
        switch (kind) {
            case STRAIGHT -> validateStraight();
            case DERIVED  -> validateDerived();
        }
        return new JsonTSchema(name, kind, fields, derivedFrom, operations, validation);
    }

    // ─── Validation helpers ───────────────────────────────────────────────────

    private void validateName() throws BuildError {
        if (name == null || name.isBlank())
            throw new BuildError("Schema name must not be blank");
        if (!Character.isUpperCase(name.charAt(0)))
            throw new BuildError("Schema name '" + name + "' must start with an uppercase letter");
    }

    private void validateStraight() throws BuildError {
        if (fields.isEmpty())
            throw new BuildError("Straight schema '" + name + "' must declare at least one field");
        // duplicate field name check
        long distinct = fields.stream().map(JsonTField::name).distinct().count();
        if (distinct != fields.size())
            throw new BuildError("Straight schema '" + name + "' contains duplicate field names");
    }

    private void validateDerived() throws BuildError {
        if (derivedFrom == null || derivedFrom.isBlank())
            throw new BuildError("Derived schema '" + name + "' must specify a parent schema name");
        if (derivedFrom.equals(name))
            throw new BuildError("Derived schema '" + name + "' cannot derive from itself");
    }
}
