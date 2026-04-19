package io.github.datakore.jsont.builder;

// =============================================================================
// DataflowTest — Step 10.4: Build-time Dataflow Analysis (Java)
// =============================================================================
//
// Covers the two layers of dataflow enforcement:
//
//   Step 5  — build-time operation ordering (no parent needed)
//   Step 5  — parent-aware: validateWithParent (decrypt on nonexistent / non-sensitive field)
//   Step 5a — runtime constraint pass-through for Encrypted values
//
// Build-time ordering checks (Step 5):
//   • transform before decrypt on sensitive field → BuildError
//   • filter  before decrypt on sensitive field → BuildError
//   • decrypt then transform → OK (correct ordering)
//   • decrypt then filter   → OK (correct ordering)
//   • rename/exclude/project before decrypt → OK (identity ops, no value access)
//   • two sensitive fields, decrypt first one, transform second before its decrypt → error
//   • no decrypt op at all → no check fires (non-sensitive pipeline)
//
// validateWithParent (Step 5 cross-schema):
//   • decrypt field that does not exist in parent → BuildError
//   • decrypt field that exists but is not sensitive → BuildError
//   • decrypt field that is sensitive → OK
//   • straight schema → no-op
//
// Runtime constraint pass-through (Step 5a):
//   • Encrypted value on field with minValue   → no constraint violation (skipped)
//   • Encrypted value on field with maxLength  → no constraint violation (skipped)
//   • Encrypted value on constant field        → no false-positive fatal (skipped)
//   • Encrypted value on required field        → passes required check (not absent)
//   • Null on required sensitive field         → RequiredFieldMissing (absent check still fires)
// =============================================================================

import io.github.datakore.jsont.crypto.AlgoVersion;
import io.github.datakore.jsont.crypto.CryptoContext;
import io.github.datakore.jsont.crypto.KekMode;
import io.github.datakore.jsont.crypto.PassthroughCryptoConfig;
import io.github.datakore.jsont.diagnostic.DiagnosticEvent;
import io.github.datakore.jsont.diagnostic.DiagnosticEventKind;
import io.github.datakore.jsont.error.BuildError;
import io.github.datakore.jsont.internal.diagnostic.MemorySink;
import io.github.datakore.jsont.model.FieldPath;
import io.github.datakore.jsont.model.JsonTExpression;
import io.github.datakore.jsont.model.JsonTField;
import io.github.datakore.jsont.model.JsonTRow;
import io.github.datakore.jsont.model.JsonTSchema;
import io.github.datakore.jsont.model.RenamePair;
import io.github.datakore.jsont.model.JsonTValue;
import io.github.datakore.jsont.model.ScalarType;
import io.github.datakore.jsont.model.SchemaOperation;
import io.github.datakore.jsont.validate.ValidationPipeline;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DataflowTest {

    // ── schema helpers ────────────────────────────────────────────────────────

    /** Person: str~:ssn, str:name */
    static JsonTSchema personSchema() throws BuildError {
        return JsonTSchemaBuilder.straight("Person")
                .fieldFrom(JsonTFieldBuilder.scalar("ssn",  ScalarType.STR).sensitive())
                .fieldFrom(JsonTFieldBuilder.scalar("name", ScalarType.STR))
                .build();
    }

    /** Encode bytes as plain-base64 wire-format value (no prefix). */
    static JsonTValue b64Wire(byte[] plaintext) {
        return JsonTValue.text(Base64.getEncoder().encodeToString(plaintext));
    }

    // ── run helpers ───────────────────────────────────────────────────────────

    private static final PassthroughCryptoConfig PASSTHROUGH = new PassthroughCryptoConfig();

    static CryptoContext decCtx() throws Exception {
        try (CryptoContext enc = CryptoContext.forEncrypt(AlgoVersion.AES_GCM, KekMode.PUBLIC_KEY, PASSTHROUGH)) {
            return CryptoContext.forDecrypt(enc.version(), enc.encDek(), PASSTHROUGH);
        }
    }

    static List<JsonTRow> runSilent(JsonTSchema schema, List<JsonTRow> rows) throws Exception {
        try (CryptoContext ctx = decCtx()) {
            var pipeline = ValidationPipeline.builder(schema).withoutConsole().withCryptoContext(ctx).build();
            return pipeline.validateRows(rows);
        }
    }

    record RunResult(List<JsonTRow> clean, List<DiagnosticEvent> events) {}

    static RunResult runWithEvents(JsonTSchema schema, List<JsonTRow> rows) throws Exception {
        var sink = new MemorySink();
        try (CryptoContext ctx = decCtx()) {
            var pipeline = ValidationPipeline.builder(schema).withoutConsole().withSink(sink).withCryptoContext(ctx).build();
            List<JsonTRow> clean = pipeline.validateRows(rows);
            return new RunResult(clean, sink.events());
        }
    }

    // =========================================================================
    // Build-time ordering checks — Step 5
    // =========================================================================

    @Test
    void transform_before_decrypt_on_sensitive_field_is_build_error() {
        // salary appears in Decrypt later → transform before decrypt is an error
        var ex = assertThrows(BuildError.class, () ->
                JsonTSchemaBuilder.derived("Analytics", "Employee")
                        .operation(SchemaOperation.transform(
                                FieldPath.single("salary"),
                                JsonTExpression.fieldName("salary")))
                        .operation(SchemaOperation.decrypt("salary"))
                        .build());
        assertTrue(ex.getMessage().contains("salary"),
                "error should name the field: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("encrypted") || ex.getMessage().contains("decrypt"),
                "error should mention encryption: " + ex.getMessage());
    }

    @Test
    void filter_before_decrypt_on_sensitive_field_is_build_error() {
        var ex = assertThrows(BuildError.class, () ->
                JsonTSchemaBuilder.derived("Analytics", "Employee")
                        .operation(SchemaOperation.filter(JsonTExpression.fieldName("ssn")))
                        .operation(SchemaOperation.decrypt("ssn"))
                        .build());
        assertTrue(ex.getMessage().contains("ssn"),
                "error should name the field: " + ex.getMessage());
    }

    @Test
    void decrypt_then_transform_is_ok() throws BuildError {
        var result = JsonTSchemaBuilder.derived("Analytics", "Employee")
                .operation(SchemaOperation.decrypt("salary"))
                .operation(SchemaOperation.transform(
                        FieldPath.single("salary"),
                        JsonTExpression.fieldName("salary")))
                .build();
        assertNotNull(result, "decrypt before transform should succeed");
    }

    @Test
    void decrypt_then_filter_is_ok() throws BuildError {
        var result = JsonTSchemaBuilder.derived("Analytics", "Employee")
                .operation(SchemaOperation.decrypt("ssn"))
                .operation(SchemaOperation.filter(JsonTExpression.fieldName("ssn")))
                .build();
        assertNotNull(result, "decrypt before filter should succeed");
    }

    @Test
    void rename_before_decrypt_is_ok() throws BuildError {
        var result = JsonTSchemaBuilder.derived("Analytics", "Employee")
                .operation(SchemaOperation.rename(
                        RenamePair.of("ssn", "social_security")))
                .operation(SchemaOperation.decrypt("ssn"))
                .build();
        assertNotNull(result, "rename before decrypt is identity op — should succeed");
    }

    @Test
    void exclude_before_decrypt_is_ok() throws BuildError {
        var result = JsonTSchemaBuilder.derived("Analytics", "Employee")
                .operation(SchemaOperation.exclude(FieldPath.single("dept")))
                .operation(SchemaOperation.decrypt("ssn"))
                .build();
        assertNotNull(result, "exclude before decrypt is OK");
    }

    @Test
    void no_decrypt_op_no_check_fires() throws BuildError {
        // Transform but no Decrypt → nothing known-sensitive → no error
        var result = JsonTSchemaBuilder.derived("Summary", "Employee")
                .operation(SchemaOperation.transform(
                        FieldPath.single("dept"),
                        JsonTExpression.fieldName("dept")))
                .build();
        assertNotNull(result, "no Decrypt op means no sensitive field detected");
    }

    @Test
    void second_sensitive_field_used_before_its_decrypt_is_error() {
        // salary decrypted first; ssn decrypted second.
        // Filter on ssn BEFORE ssn's Decrypt → error.
        var ex = assertThrows(BuildError.class, () ->
                JsonTSchemaBuilder.derived("Analytics", "Employee")
                        .operation(SchemaOperation.decrypt("salary"))
                        .operation(SchemaOperation.filter(JsonTExpression.fieldName("ssn")))
                        .operation(SchemaOperation.decrypt("ssn"))
                        .build());
        assertTrue(ex.getMessage().contains("ssn"),
                "error should name ssn: " + ex.getMessage());
    }

    // =========================================================================
    // validateWithParent — Step 5 cross-schema checks
    // =========================================================================

    @Test
    void validate_with_parent_decrypt_nonexistent_field_is_error() throws Exception {
        JsonTSchema parent = personSchema();

        JsonTSchema derived = JsonTSchemaBuilder.derived("Analytics", "Person")
                .operation(SchemaOperation.decrypt("nonexistent"))
                .build();

        var ex = assertThrows(BuildError.class, () -> derived.validateWithParent(parent));
        assertTrue(ex.getMessage().contains("nonexistent"),
                "error should name the missing field: " + ex.getMessage());
    }

    @Test
    void validate_with_parent_decrypt_non_sensitive_field_is_error() throws Exception {
        JsonTSchema parent = personSchema(); // "name" is not sensitive

        JsonTSchema derived = JsonTSchemaBuilder.derived("Analytics", "Person")
                .operation(SchemaOperation.decrypt("name"))
                .build();

        var ex = assertThrows(BuildError.class, () -> derived.validateWithParent(parent));
        assertTrue(ex.getMessage().contains("name"),
                "error should name the field: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("sensitive") || ex.getMessage().contains("~"),
                "error should mention sensitivity: " + ex.getMessage());
    }

    @Test
    void validate_with_parent_decrypt_sensitive_field_is_ok() throws Exception {
        JsonTSchema parent = personSchema(); // "ssn" is sensitive

        JsonTSchema derived = JsonTSchemaBuilder.derived("Analytics", "Person")
                .operation(SchemaOperation.decrypt("ssn"))
                .build();

        assertDoesNotThrow(() -> derived.validateWithParent(parent),
                "decrypt on a sensitive field should pass validateWithParent");
    }

    @Test
    void validate_with_parent_on_straight_schema_is_noop() throws Exception {
        JsonTSchema parent = personSchema();
        JsonTSchema straight = personSchema();
        assertDoesNotThrow(() -> straight.validateWithParent(parent),
                "validateWithParent on a straight schema is a no-op");
    }

    // ── Full field-scope simulation ───────────────────────────────────────────

    /** Employee: str~:ssn, str:name, str:dept (3 fields) */
    static JsonTSchema employeeParentSchema() throws BuildError {
        return JsonTSchemaBuilder.straight("Employee")
                .fieldFrom(JsonTFieldBuilder.scalar("ssn",  ScalarType.STR).sensitive())
                .fieldFrom(JsonTFieldBuilder.scalar("name", ScalarType.STR))
                .fieldFrom(JsonTFieldBuilder.scalar("dept", ScalarType.STR))
                .build();
    }

    @Test
    void project_then_transform_on_excluded_field_is_error() throws BuildError {
        // Project keeps only (name, dept); ssn is dropped.
        // Transform then references ssn → should fail.
        JsonTSchema parent = employeeParentSchema();
        JsonTSchema derived = JsonTSchemaBuilder.derived("Analytics", "Employee")
                .operation(SchemaOperation.project(
                        FieldPath.single("name"), FieldPath.single("dept")))
                .operation(SchemaOperation.transform(
                        FieldPath.single("name"),
                        JsonTExpression.fieldName("ssn"))) // ssn not in scope
                .build();

        var ex = assertThrows(BuildError.class, () -> derived.validateWithParent(parent));
        assertTrue(ex.getMessage().contains("ssn"),
                "error should name ssn: " + ex.getMessage());
    }

    @Test
    void exclude_then_transform_on_excluded_field_is_error() throws BuildError {
        JsonTSchema parent = employeeParentSchema();
        JsonTSchema derived = JsonTSchemaBuilder.derived("Analytics", "Employee")
                .operation(SchemaOperation.exclude(FieldPath.single("ssn")))
                .operation(SchemaOperation.transform(
                        FieldPath.single("name"),
                        JsonTExpression.fieldName("ssn"))) // ssn was excluded
                .build();

        var ex = assertThrows(BuildError.class, () -> derived.validateWithParent(parent));
        assertTrue(ex.getMessage().contains("ssn"),
                "error should name ssn: " + ex.getMessage());
    }

    @Test
    void rename_old_name_no_longer_in_scope() throws BuildError {
        // Rename ssn → social; then Transform references ssn (old name) → error
        JsonTSchema parent = employeeParentSchema();
        JsonTSchema derived = JsonTSchemaBuilder.derived("Analytics", "Employee")
                .operation(SchemaOperation.rename(RenamePair.of("ssn", "social")))
                .operation(SchemaOperation.transform(
                        FieldPath.single("name"),
                        JsonTExpression.fieldName("ssn"))) // old name, gone after rename
                .build();

        var ex = assertThrows(BuildError.class, () -> derived.validateWithParent(parent));
        assertTrue(ex.getMessage().contains("ssn"),
                "error should name ssn: " + ex.getMessage());
    }

    @Test
    void rename_to_conflicting_name_is_error() throws BuildError {
        // Rename ssn → name, but name already exists → collision
        JsonTSchema parent = employeeParentSchema();
        JsonTSchema derived = JsonTSchemaBuilder.derived("Analytics", "Employee")
                .operation(SchemaOperation.rename(RenamePair.of("ssn", "name")))
                .build();

        var ex = assertThrows(BuildError.class, () -> derived.validateWithParent(parent));
        assertTrue(ex.getMessage().contains("name"),
                "error should mention the conflicting field: " + ex.getMessage());
    }

    @Test
    void filter_referencing_projected_away_field_is_error() throws BuildError {
        JsonTSchema parent = employeeParentSchema();
        JsonTSchema derived = JsonTSchemaBuilder.derived("Analytics", "Employee")
                .operation(SchemaOperation.project(FieldPath.single("name")))
                .operation(SchemaOperation.filter(JsonTExpression.fieldName("dept"))) // dept was projected away
                .build();

        var ex = assertThrows(BuildError.class, () -> derived.validateWithParent(parent));
        assertTrue(ex.getMessage().contains("dept"),
                "error should name dept: " + ex.getMessage());
    }

    @Test
    void full_valid_pipeline_passes() throws BuildError {
        // decrypt(ssn) → transform(name) using name → filter on dept → exclude ssn
        JsonTSchema parent = employeeParentSchema();
        JsonTSchema derived = JsonTSchemaBuilder.derived("Analytics", "Employee")
                .operation(SchemaOperation.decrypt("ssn"))
                .operation(SchemaOperation.transform(
                        FieldPath.single("name"), JsonTExpression.fieldName("name")))
                .operation(SchemaOperation.filter(JsonTExpression.fieldName("dept")))
                .operation(SchemaOperation.exclude(FieldPath.single("ssn")))
                .build();

        assertDoesNotThrow(() -> derived.validateWithParent(parent),
                "valid pipeline should pass full dataflow check");
    }

    // =========================================================================
    // Runtime constraint pass-through — Step 5a
    // =========================================================================

    @Test
    void encrypted_value_skips_min_value_constraint() throws Exception {
        // salary has minValue=50000 — Encrypted value must skip this check
        JsonTSchema schema = JsonTSchemaBuilder.straight("Employee")
                .fieldFrom(JsonTFieldBuilder.scalar("salary", ScalarType.D64)
                        .sensitive().minValue(50000))
                .build();

        JsonTRow row = JsonTRow.of(b64Wire("30000.00".getBytes())); // below minValue but encrypted
        var result = runWithEvents(schema, List.of(row));

        assertEquals(1, result.clean().size(),
                "encrypted row should pass through despite apparent minValue violation");
        boolean hasConstraintWarning = result.events().stream()
                .anyMatch(e -> e.kind() instanceof DiagnosticEventKind.ConstraintViolation cv
                        && cv.field().equals("salary"));
        assertFalse(hasConstraintWarning,
                "no constraint warning expected for encrypted value, got: " + result.events());
    }

    @Test
    void encrypted_value_skips_max_length_constraint() throws Exception {
        JsonTSchema schema = JsonTSchemaBuilder.straight("Person")
                .fieldFrom(JsonTFieldBuilder.scalar("ssn", ScalarType.STR)
                        .sensitive().maxLength(3)) // artificially small
                .build();

        JsonTRow row = JsonTRow.of(b64Wire("123-45-6789".getBytes())); // longer than 3 chars but encrypted
        var result = runWithEvents(schema, List.of(row));

        assertEquals(1, result.clean().size(),
                "encrypted value should bypass maxLength constraint");
        boolean hasLengthWarning = result.events().stream()
                .anyMatch(e -> e.kind() instanceof DiagnosticEventKind.ConstraintViolation cv
                        && cv.constraint().contains("maxLength"));
        assertFalse(hasLengthWarning,
                "no maxLength warning expected for encrypted value, got: " + result.events());
    }

    @Test
    void encrypted_value_skips_constant_check() throws Exception {
        // Constant check would fire Fatal for any non-matching value — must be skipped for Encrypted
        JsonTSchema schema = JsonTSchemaBuilder.straight("Config")
                .fieldFrom(JsonTFieldBuilder.scalar("secret", ScalarType.STR)
                        .sensitive().constantValue(JsonTValue.text("fixed")))
                .build();

        JsonTRow row = JsonTRow.of(b64Wire("some-secret".getBytes()));
        var result = runWithEvents(schema, List.of(row));

        assertEquals(1, result.clean().size(),
                "encrypted value should not trigger constant-check fatal");
        boolean hasFatalConstant = result.events().stream()
                .anyMatch(e -> e.isFatal()
                        && e.kind() instanceof DiagnosticEventKind.ConstraintViolation cv
                        && cv.field().equals("secret"));
        assertFalse(hasFatalConstant,
                "constant check must not fire for encrypted value, got: " + result.events());
    }

    @Test
    void encrypted_value_satisfies_required_check() throws Exception {
        JsonTSchema schema = personSchema(); // ssn required and sensitive

        JsonTRow row = JsonTRow.of(b64Wire("123-45-6789".getBytes()), JsonTValue.text("Alice"));
        List<JsonTRow> clean = runSilent(schema, List.of(row));

        assertEquals(1, clean.size(), "encrypted value satisfies required check");
        assertTrue(clean.get(0).values().get(0).isEncrypted(), "ssn should be Encrypted");
    }

    @Test
    void null_on_required_sensitive_field_still_fails() throws Exception {
        JsonTSchema schema = personSchema(); // ssn required and sensitive

        JsonTRow row = JsonTRow.of(JsonTValue.nullValue(), JsonTValue.text("Alice"));
        var result = runWithEvents(schema, List.of(row));

        assertTrue(result.clean().isEmpty(),
                "null on required sensitive field → row rejected");
        boolean hasRequiredMissing = result.events().stream()
                .anyMatch(e -> e.kind() instanceof DiagnosticEventKind.RequiredFieldMissing rf
                        && rf.field().equals("ssn"));
        assertTrue(hasRequiredMissing,
                "expected RequiredFieldMissing for ssn, got: " + result.events());
    }
}
