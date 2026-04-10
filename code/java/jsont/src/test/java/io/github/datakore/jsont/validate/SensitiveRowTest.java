package io.github.datakore.jsont.validate;

// =============================================================================
// SensitiveRowTest — Step 10.3: Row Parse via promoteRow (Java)
// =============================================================================
// The row scanner has no schema context, so it always emits quoted strings as
// JsonTString.Plain("…"). promoteRow, which runs inside the validation pipeline
// and has both field and value, decodes the wire value as base64 ciphertext for
// sensitive fields (the ~ schema marker is the authority, not a string prefix).
//
// These tests construct rows that mimic scanner output (plain text values) and
// feed them through ValidationPipeline.validateRows, then inspect the
// promoted row values.
//
// Coverage:
//   • base64 string on sensitive field → Encrypted after promoteRow
//   • Encrypted value carries correct decoded bytes
//   • Non-sensitive field with base64-looking string stays plain Str
//   • Sensitive field that is already Encrypted passes through unchanged
//   • Null on optional sensitive field stays Null
//   • Multiple sensitive fields in one row — all decoded
//   • Two rows processed independently — both decoded correctly
//   • Invalid base64 payload → FormatViolation (row rejected)
// =============================================================================

import io.github.datakore.jsont.builder.JsonTFieldBuilder;
import io.github.datakore.jsont.builder.JsonTSchemaBuilder;
import io.github.datakore.jsont.diagnostic.DiagnosticEvent;
import io.github.datakore.jsont.internal.diagnostic.MemorySink;
import io.github.datakore.jsont.model.JsonTRow;
import io.github.datakore.jsont.model.JsonTSchema;
import io.github.datakore.jsont.model.JsonTString;
import io.github.datakore.jsont.model.JsonTValue;
import io.github.datakore.jsont.model.ScalarType;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SensitiveRowTest {

    // ── schemas ───────────────────────────────────────────────────────────────

    /** Person: str~:ssn, str:name */
    static JsonTSchema personSchema() throws Exception {
        return JsonTSchemaBuilder.straight("Person")
                .fieldFrom(JsonTFieldBuilder.scalar("ssn",  ScalarType.STR).sensitive())
                .fieldFrom(JsonTFieldBuilder.scalar("name", ScalarType.STR))
                .build();
    }

    /** Employee: str~:ssn, d64~:salary, str:dept */
    static JsonTSchema employeeSchema() throws Exception {
        return JsonTSchemaBuilder.straight("Employee")
                .fieldFrom(JsonTFieldBuilder.scalar("ssn",    ScalarType.STR).sensitive())
                .fieldFrom(JsonTFieldBuilder.scalar("salary", ScalarType.D64).sensitive())
                .fieldFrom(JsonTFieldBuilder.scalar("dept",   ScalarType.STR))
                .build();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    /** Encode bytes as the plain-base64 wire-format string the scanner would produce. */
    static JsonTValue b64Wire(byte[] plaintext) {
        return JsonTValue.text(Base64.getEncoder().encodeToString(plaintext));
    }

    static JsonTValue b64Wire(String plaintext) {
        return b64Wire(plaintext.getBytes());
    }

    /** Run rows silently, return only clean rows. */
    static List<JsonTRow> runSilent(JsonTSchema schema, List<JsonTRow> rows) {
        var pipeline = ValidationPipeline.builder(schema).withoutConsole().build();
        return pipeline.validateRows(rows);
    }

    /** Run rows capturing all diagnostic events. */
    static RunResult runWithEvents(JsonTSchema schema, List<JsonTRow> rows) {
        var sink = new MemorySink();
        var pipeline = ValidationPipeline.builder(schema).withoutConsole().withSink(sink).build();
        List<JsonTRow> clean = pipeline.validateRows(rows);
        return new RunResult(clean, sink.events());
    }

    record RunResult(List<JsonTRow> clean, List<DiagnosticEvent> events) {}

    // ── tests ─────────────────────────────────────────────────────────────────

    @Test
    void sensitive_field_base64_becomes_encrypted() throws Exception {
        var row = JsonTRow.of(b64Wire("123-45-6789"), JsonTValue.text("Alice"));
        var clean = runSilent(personSchema(), List.of(row));

        assertEquals(1, clean.size());
        assertTrue(clean.get(0).values().get(0).isEncrypted(),
                "ssn should be Encrypted");
        assertFalse(clean.get(0).values().get(1).isEncrypted(),
                "name must stay plain");
    }

    @Test
    void encrypted_value_carries_correct_bytes() throws Exception {
        byte[] plaintext = "123-45-6789".getBytes();
        var row = JsonTRow.of(b64Wire(plaintext), JsonTValue.text("Alice"));
        var clean = runSilent(personSchema(), List.of(row));

        var enc = (JsonTValue.Encrypted) clean.get(0).values().get(0);
        assertArrayEquals(plaintext, enc.envelope());
    }

    @Test
    void non_sensitive_field_base64_prefix_stays_str() throws Exception {
        // name is not sensitive — a base64-looking string stays as plain Str
        var row = JsonTRow.of(
                b64Wire("123-45-6789"),   // sensitive ssn — will be decoded
                JsonTValue.text("aGVsbG8=") // name is not sensitive — stays as Str
        );
        var clean = runSilent(personSchema(), List.of(row));

        assertFalse(clean.get(0).values().get(1).isEncrypted(),
                "non-sensitive field must not become Encrypted");
        assertInstanceOf(JsonTString.class, clean.get(0).values().get(1));
    }

    @Test
    void sensitive_field_already_encrypted_passes_through() throws Exception {
        // If the value is already Encrypted (e.g. after a prior promotion),
        // promoteRow must leave it unchanged — no double-decoding.
        byte[] ciphertext = "already-decoded-bytes".getBytes();
        var row = JsonTRow.of(
                JsonTValue.encrypted(ciphertext),
                JsonTValue.text("Dave")
        );
        var clean = runSilent(personSchema(), List.of(row));

        assertTrue(clean.get(0).values().get(0).isEncrypted(),
                "pre-Encrypted value must stay Encrypted");
        assertArrayEquals(ciphertext,
                ((JsonTValue.Encrypted) clean.get(0).values().get(0)).envelope());
    }

    @Test
    void null_on_optional_sensitive_field_stays_null() throws Exception {
        var schema = JsonTSchemaBuilder.straight("Person")
                .fieldFrom(JsonTFieldBuilder.scalar("ssn",  ScalarType.STR).sensitive().optional())
                .fieldFrom(JsonTFieldBuilder.scalar("name", ScalarType.STR))
                .build();

        var row = JsonTRow.of(JsonTValue.nullValue(), JsonTValue.text("Carol"));
        var clean = runSilent(schema, List.of(row));

        assertInstanceOf(JsonTValue.Null.class, clean.get(0).values().get(0),
                "null stays null");
    }

    @Test
    void multiple_sensitive_fields_all_encrypted() throws Exception {
        var row = JsonTRow.of(
                b64Wire("111-22-3333"),
                b64Wire("95000.00"),
                JsonTValue.text("Engineering")
        );
        var clean = runSilent(employeeSchema(), List.of(row));

        assertEquals(1, clean.size());
        assertTrue(clean.get(0).values().get(0).isEncrypted(), "ssn encrypted");
        assertTrue(clean.get(0).values().get(1).isEncrypted(), "salary encrypted");
        assertFalse(clean.get(0).values().get(2).isEncrypted(), "dept plain");
    }

    @Test
    void two_rows_both_decoded_independently() throws Exception {
        var rows = List.of(
                JsonTRow.of(b64Wire("111-22-3333"), JsonTValue.text("Eve")),
                JsonTRow.of(b64Wire("444-55-6666"), JsonTValue.text("Frank"))
        );
        var clean = runSilent(personSchema(), rows);

        assertEquals(2, clean.size());
        assertArrayEquals("111-22-3333".getBytes(),
                ((JsonTValue.Encrypted) clean.get(0).values().get(0)).envelope());
        assertArrayEquals("444-55-6666".getBytes(),
                ((JsonTValue.Encrypted) clean.get(1).values().get(0)).envelope());
    }

    @Test
    void invalid_base64_payload_row_is_rejected() throws Exception {
        var row = JsonTRow.of(
                JsonTValue.text("!!!not-valid-base64!!!"),
                JsonTValue.text("Grace")
        );
        var result = runWithEvents(personSchema(), List.of(row));

        assertTrue(result.clean().isEmpty(), "invalid base64 row should be rejected");
        // The bad base64 leaves the value as a plain string, which then fails
        // normal validation (plain string cannot promote to STR without issue, but
        // more importantly we leave it for the constraint checker to handle).
        // Since the field is required (not optional) and the value is present as
        // Str, no required violation fires — the row stays plain.
        // The row should be accepted with the un-decoded string or rejected.
        // Check that no clean row came through with an Encrypted value.
        assertFalse(result.clean().stream()
                .flatMap(r -> r.values().stream())
                .anyMatch(JsonTValue::isEncrypted));
    }

    @Test
    void mixed_row_sensitive_encrypted_plain_unchanged() throws Exception {
        var row = JsonTRow.of(b64Wire("secret-ssn"), JsonTValue.text("Helen"));
        var clean = runSilent(personSchema(), List.of(row));

        assertTrue(clean.get(0).values().get(0).isEncrypted());
        assertInstanceOf(JsonTString.class, clean.get(0).values().get(1));
    }
}
