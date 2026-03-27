package io.github.datakore.jsont.builder;

import io.github.datakore.jsont.error.BuildError;
import io.github.datakore.jsont.model.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JsonTRowBuilderTest {

    // ── helper schema ─────────────────────────────────────────────────────────

    /** Schema: id(i64), name(str), email(email, optional). */
    static JsonTSchema personSchema() throws BuildError {
        return JsonTSchemaBuilder.straight("Person")
                .fieldFrom(JsonTFieldBuilder.scalar("id",    ScalarType.I64))
                .fieldFrom(JsonTFieldBuilder.scalar("name",  ScalarType.STR))
                .fieldFrom(JsonTFieldBuilder.scalar("email", ScalarType.EMAIL).optional())
                .build();
    }

    // ── untyped mode ──────────────────────────────────────────────────────────

    @Test void untypedBuilder_pushAndBuild() {
        JsonTRow row = JsonTRowBuilder.create()
                .push(JsonTValue.i64(1L))
                .push(JsonTValue.text("Alice"))
                .push(JsonTValue.nullValue())
                .build();
        assertEquals(3, row.size());
        assertEquals(JsonTValue.i64(1L), row.get(0));
        assertEquals(JsonTValue.text("Alice"), row.get(1));
    }

    @Test void untypedBuilder_emptyRow() {
        JsonTRow row = JsonTRowBuilder.create().build();
        assertTrue(row.isEmpty());
    }

    // ── schema-aware happy path ────────────────────────────────────────────────

    @Test void schemaAware_happyPath() throws BuildError {
        JsonTRow row = JsonTRowBuilder.withSchema(personSchema())
                .pushChecked(JsonTValue.i64(1L))
                .pushChecked(JsonTValue.text("Alice"))
                .pushChecked(JsonTValue.nullValue())  // optional field — null is OK
                .buildChecked();
        assertEquals(3, row.size());
        assertEquals(JsonTValue.i64(1L), row.get(0));
    }

    @Test void schemaAware_unspecified_alwaysPasses() throws BuildError {
        JsonTRow row = JsonTRowBuilder.withSchema(personSchema())
                .pushChecked(JsonTValue.i64(1L))
                .pushChecked(JsonTValue.text("Bob"))
                .pushChecked(JsonTValue.unspecified())
                .buildChecked();
        assertInstanceOf(JsonTValue.Unspecified.class, row.get(2));
    }

    // ── type mismatch ─────────────────────────────────────────────────────────

    @Test void schemaAware_typeMismatch_throwsBuildError() throws BuildError {
        var builder = JsonTRowBuilder.withSchema(personSchema());
        // field 0 is i64 — pushing a str should fail
        assertThrows(BuildError.class, () -> builder.pushChecked(JsonTValue.text("not-an-int")));
    }

    @Test void schemaAware_typeMismatch_wrongNumericType() throws BuildError {
        var builder = JsonTRowBuilder.withSchema(personSchema());
        // field 0 is i64 — pushing i32 is a type mismatch
        assertThrows(BuildError.class, () -> builder.pushChecked(JsonTValue.i32(1)));
    }

    // ── string subtype compatibility ──────────────────────────────────────────

    @Test void schemaAware_stringSubtype_textAccepted() throws BuildError {
        // field 2 is EMAIL type — Text value should be accepted
        JsonTRow row = JsonTRowBuilder.withSchema(personSchema())
                .pushChecked(JsonTValue.i64(1L))
                .pushChecked(JsonTValue.text("Alice"))
                .pushChecked(JsonTValue.text("alice@example.com"))
                .buildChecked();
        assertInstanceOf(JsonTValue.Text.class, row.get(2));
    }

    // ── too many values ───────────────────────────────────────────────────────

    @Test void schemaAware_tooManyValues_throwsBuildError() throws BuildError {
        var schema = personSchema();
        assertThrows(BuildError.class, () ->
                JsonTRowBuilder.withSchema(schema)
                        .pushChecked(JsonTValue.i64(1L))
                        .pushChecked(JsonTValue.text("Alice"))
                        .pushChecked(JsonTValue.nullValue())
                        .pushChecked(JsonTValue.text("extra"))  // 4th value, schema has 3 fields
        );
    }

    // ── incomplete row ────────────────────────────────────────────────────────

    @Test void schemaAware_missingRequiredField_throwsBuildError() throws BuildError {
        // Only push field 0 (id) — field 1 (name) is required
        var schema = personSchema();
        assertThrows(BuildError.class, () ->
                JsonTRowBuilder.withSchema(schema)
                        .pushChecked(JsonTValue.i64(1L))
                        .buildChecked()
        );
    }

    @Test void schemaAware_missingOptionalField_succeeds() throws BuildError {
        // Push only required fields; optional email is absent — buildChecked should succeed
        JsonTRow row = JsonTRowBuilder.withSchema(personSchema())
                .pushChecked(JsonTValue.i64(1L))
                .pushChecked(JsonTValue.text("Alice"))
                .buildChecked();
        assertEquals(2, row.size());
    }

    // ── null always passes ────────────────────────────────────────────────────

    @Test void schemaAware_nullOnRequiredField_passesTypeCheck() throws BuildError {
        // Null passes type validation — null enforcement is left to ValidationPipeline
        JsonTRow row = JsonTRowBuilder.withSchema(personSchema())
                .pushChecked(JsonTValue.nullValue())
                .pushChecked(JsonTValue.nullValue())
                .buildChecked();
        assertEquals(2, row.size());
    }
}
