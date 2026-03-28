package io.github.datakore.jsont.builder;

import io.github.datakore.jsont.error.BuildError;
import io.github.datakore.jsont.model.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Fluent row builder. Use {@link #create()} for free-form rows, or {@link #withSchema}
 * for type-checked construction.
 *
 * <pre>{@code
 *   // untyped
 *   JsonTRow row = JsonTRowBuilder.create()
 *       .push(JsonTValue.i64(1L))
 *       .push(JsonTValue.text("Alice"))
 *       .build();
 *
 *   // schema-aware — validates each value against the corresponding field type
 *   JsonTRow row = JsonTRowBuilder.withSchema(personSchema)
 *       .pushChecked(JsonTValue.i64(1L))
 *       .pushChecked(JsonTValue.text("Alice"))
 *       .buildChecked();
 * }</pre>
 *
 * <p>Null and Unspecified always pass type checks — null enforcement is left to the
 * validation pipeline.
 */
public final class JsonTRowBuilder {

    private final List<JsonTValue> values = new ArrayList<>();
    private final JsonTSchema schema; // null = untyped mode

    private JsonTRowBuilder(JsonTSchema schema) { this.schema = schema; }

    // ─── Factory methods ──────────────────────────────────────────────────────

    /** Creates an untyped builder — no schema validation. */
    public static JsonTRowBuilder create() { return new JsonTRowBuilder(null); }

    /**
     * Creates a schema-aware builder. Use {@link #pushChecked} and {@link #buildChecked}
     * to get type and arity validation.
     */
    public static JsonTRowBuilder withSchema(JsonTSchema schema) {
        Objects.requireNonNull(schema, "schema must not be null");
        return new JsonTRowBuilder(schema);
    }

    // ─── Untyped mode ─────────────────────────────────────────────────────────

    /** Appends a value. No validation. */
    public JsonTRowBuilder push(JsonTValue value) {
        Objects.requireNonNull(value, "value must not be null");
        values.add(value);
        return this;
    }

    /** Builds the row from all pushed values. Always succeeds. */
    public JsonTRow build() { return JsonTRow.at(0L, values); }

    // ─── Schema-aware mode ────────────────────────────────────────────────────

    /**
     * Appends a value and validates it against the next schema field.
     *
     * @throws BuildError on type mismatch or if already at the field limit
     */
    public JsonTRowBuilder pushChecked(JsonTValue value) throws BuildError {
        Objects.requireNonNull(value, "value must not be null");
        if (schema != null) {
            int idx = values.size();
            if (idx >= schema.fieldCount()) {
                throw new BuildError("Too many values: schema '" + schema.name()
                        + "' has " + schema.fieldCount() + " fields, but pushed " + (idx + 1));
            }
            validateValueForField(value, schema.fields().get(idx));
        }
        values.add(value);
        return this;
    }

    /**
     * Builds the row, checking that all required fields were pushed.
     *
     * @throws BuildError if a required field has no value
     */
    public JsonTRow buildChecked() throws BuildError {
        if (schema != null) {
            for (int i = values.size(); i < schema.fieldCount(); i++) {
                JsonTField field = schema.fields().get(i);
                if (!field.optional()) {
                    throw new BuildError("Missing required field '" + field.name()
                            + "' (index " + i + ") in schema '" + schema.name() + "'");
                }
            }
        }
        return JsonTRow.at(0L, values);
    }

    // ─── Internals ────────────────────────────────────────────────────────────

    private static void validateValueForField(JsonTValue value, JsonTField field) throws BuildError {
        // Null and Unspecified are always accepted — deferred to validation pipeline
        if (value instanceof JsonTValue.Null || value instanceof JsonTValue.Unspecified) return;

        if (field.kind().isObject()) {
            // Object fields accept Array values (nested rows as positional arrays)
            if (!(value instanceof JsonTValue.Array)) {
                throw new BuildError("Type mismatch for field '" + field.name()
                        + "': expected array/object, got " + value.getClass().getSimpleName());
            }
            return;
        }

        if (!field.kind().isScalar()) return;

        ScalarType expected = field.scalarType();
        if (!isCompatible(value, expected)) {
            throw new BuildError("Type mismatch for field '" + field.name()
                    + "': expected " + expected.keyword()
                    + ", got " + value.getClass().getSimpleName());
        }
    }

    /** Returns true when {@code value} is type-compatible with {@code expected}. */
    private static boolean isCompatible(JsonTValue value, ScalarType expected) {
        if (expected == ScalarType.BOOL) return value instanceof JsonTValue.Bool;
        if (isStringType(expected))     return value instanceof JsonTValue.Text;
        return switch (expected) {
            case I16  -> value instanceof JsonTValue.I16;
            case I32  -> value instanceof JsonTValue.I32;
            case I64  -> value instanceof JsonTValue.I64;
            case U16  -> value instanceof JsonTValue.U16;
            case U32  -> value instanceof JsonTValue.U32;
            case U64  -> value instanceof JsonTValue.U64;
            case D32  -> value instanceof JsonTValue.D32;
            case D64  -> value instanceof JsonTValue.D64;
            case D128 -> value instanceof JsonTValue.D128;
            default   -> false;
        };
    }

    /** String-like scalar types that accept {@link JsonTValue.Text} values. */
    private static boolean isStringType(ScalarType t) {
        return switch (t) {
            case STR, NSTR, URI, UUID, EMAIL, HOSTNAME,
                 IPV4, IPV6, DATE, TIME, DATETIME, TIMESTAMP, TSZ,
                 DURATION, INST, BASE64, OID, HEX -> true;
            default -> false;
        };
    }
}
