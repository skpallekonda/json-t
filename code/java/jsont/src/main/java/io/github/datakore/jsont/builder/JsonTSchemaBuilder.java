package io.github.datakore.jsont.builder;

import io.github.datakore.jsont.error.BuildError;
import io.github.datakore.jsont.internal.transform.OperationApplicator;
import io.github.datakore.jsont.model.JsonTField;
import io.github.datakore.jsont.model.JsonTSchema;
import io.github.datakore.jsont.model.JsonTValidationBlock;
import io.github.datakore.jsont.model.SchemaKind;
import io.github.datakore.jsont.model.SchemaOperation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Fluent builder for {@link JsonTSchema}. Use {@link #straight} for new fields 
 * or {@link #derived} to transform an existing schema.
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
     * Builds and adds a field.
     *
     * @throws BuildError if called on a derived schema, or if the field is misconfigured
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
     * @throws BuildError if called on a derived schema, or if the block is misconfigured
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
     * Operations run in declaration order at transform time.
     *
     * @throws BuildError if called on a straight schema
     */
    public JsonTSchemaBuilder operation(SchemaOperation op) throws BuildError {
        if (kind == SchemaKind.STRAIGHT)
            throw new BuildError("Cannot add operations to straight schema '" + name + "' — use fieldFrom() instead");
        if (op == null) throw new BuildError("SchemaOperation must not be null");
        operations.add(op);
        return this;
    }

    /**
     * Appends a {@link SchemaOperation.Decrypt} operation to a derived schema's pipeline.
     *
     * <p>The named fields are decrypted in-place at transform time. Returns an error if
     * called on a straight schema.
     *
     * @param fields one or more field names to decrypt
     * @throws BuildError if called on a straight schema or if fields list is empty
     */
    public JsonTSchemaBuilder decrypt(String... fields) throws BuildError {
        return operation(SchemaOperation.decrypt(fields));
    }

    // ─── Build ────────────────────────────────────────────────────────────────

    /** @throws BuildError if the schema is misconfigured (e.g. no fields, duplicate names) */
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
        checkOperationDataflow(operations);
    }

    /**
     * Checks if Transform or Filter use any encrypted fields before Decrypt. 
     * This logic is self-contained within the operations list.
     */
    private void checkOperationDataflow(List<SchemaOperation> ops) throws BuildError {
        // Collect all field names appearing in any Decrypt op — presumed sensitive.
        Set<String> knownSensitive = new HashSet<>();
        for (SchemaOperation op : ops) {
            if (op instanceof SchemaOperation.Decrypt d) {
                knownSensitive.addAll(d.fields());
            }
        }
        if (knownSensitive.isEmpty()) return; // No Decrypt ops — nothing to check.

        Set<String> decrypted = new HashSet<>();

        for (SchemaOperation op : ops) {
            if (op instanceof SchemaOperation.Decrypt d) {
                decrypted.addAll(d.fields());

            } else if (op instanceof SchemaOperation.Transform t) {
                // Check expression refs
                for (String ref : OperationApplicator.collectFieldRefs(t.expr())) {
                    if (knownSensitive.contains(ref) && !decrypted.contains(ref)) {
                        throw new BuildError(
                                "field '" + ref + "' is encrypted; add decrypt(" + ref
                                        + ") before this transform");
                    }
                }
                // Check the target field itself
                String tgt = t.target().leaf();
                if (knownSensitive.contains(tgt) && !decrypted.contains(tgt)) {
                    throw new BuildError(
                            "field '" + tgt + "' is encrypted; add decrypt(" + tgt
                                    + ") before this transform");
                }

            } else if (op instanceof SchemaOperation.Filter f) {
                for (String ref : OperationApplicator.collectFieldRefs(f.predicate())) {
                    if (knownSensitive.contains(ref) && !decrypted.contains(ref)) {
                        throw new BuildError(
                                "field '" + ref + "' is encrypted; add decrypt(" + ref
                                        + ") before this filter");
                    }
                }
            }
            // Rename/Exclude/Project operate on field identity — OK on encrypted fields.
        }
    }
}
