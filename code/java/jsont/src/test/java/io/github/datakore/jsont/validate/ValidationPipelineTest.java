package io.github.datakore.jsont.validate;

import io.github.datakore.jsont.builder.JsonTFieldBuilder;
import io.github.datakore.jsont.builder.JsonTSchemaBuilder;
import io.github.datakore.jsont.builder.JsonTValidationBlockBuilder;
import io.github.datakore.jsont.diagnostic.DiagnosticEvent;
import io.github.datakore.jsont.diagnostic.DiagnosticSeverity;
import io.github.datakore.jsont.internal.diagnostic.MemorySink;
import io.github.datakore.jsont.model.BinaryOp;
import io.github.datakore.jsont.model.FieldPath;
import io.github.datakore.jsont.model.JsonTExpression;
import io.github.datakore.jsont.model.JsonTRow;
import io.github.datakore.jsont.model.JsonTSchema;
import io.github.datakore.jsont.model.JsonTValue;
import io.github.datakore.jsont.model.ScalarType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ValidationPipelineTest {

    // ─── Helper to build a simple schema ──────────────────────────────────────

    private static JsonTSchema simpleSchema() throws Exception {
        return JsonTSchemaBuilder.straight("Test")
                .fieldFrom(JsonTFieldBuilder.scalar("id", ScalarType.I64))
                .fieldFrom(JsonTFieldBuilder.scalar("name", ScalarType.STR))
                .build();
    }

    // ─── Builder ──────────────────────────────────────────────────────────────

    @Test
    void builder_createsPipeline() throws Exception {
        ValidationPipeline p = ValidationPipeline.builder(simpleSchema())
                .withoutConsole()
                .build();
        assertNotNull(p);
    }

    @Test
    void withoutConsole_suppressesConsole() throws Exception {
        MemorySink mem = new MemorySink();
        ValidationPipeline p = ValidationPipeline.builder(simpleSchema())
                .withoutConsole()
                .withSink(mem)
                .build();
        p.validateRows(List.of(JsonTRow.of(JsonTValue.i64(1), JsonTValue.text("Alice"))));
        assertFalse(mem.events().isEmpty());
    }

    // ─── validateRows ─────────────────────────────────────────────────────────

    @Test
    void validateRows_returnsCleanRows() throws Exception {
        MemorySink mem = new MemorySink();
        ValidationPipeline p = ValidationPipeline.builder(simpleSchema())
                .withoutConsole()
                .withSink(mem)
                .build();
        List<JsonTRow> rows = List.of(
                JsonTRow.of(JsonTValue.i64(1), JsonTValue.text("Alice")),
                JsonTRow.of(JsonTValue.i64(2), JsonTValue.text("Bob"))
        );
        List<JsonTRow> clean = p.validateRows(rows);
        assertEquals(2, clean.size());
    }

    @Test
    void shapeMismatch_rejectsRow() throws Exception {
        MemorySink mem = new MemorySink();
        ValidationPipeline p = ValidationPipeline.builder(simpleSchema())
                .withoutConsole()
                .withSink(mem)
                .build();
        // row with only 1 value, schema expects 2
        List<JsonTRow> clean = p.validateRows(List.of(
                JsonTRow.of(JsonTValue.i64(1))
        ));
        assertEquals(0, clean.size());
        assertFalse(mem.fatalEvents().isEmpty());
    }

    @Test
    void requiredField_missing_rejectsRow() throws Exception {
        JsonTSchema schema = JsonTSchemaBuilder.straight("Test")
                .fieldFrom(JsonTFieldBuilder.scalar("id", ScalarType.I64).required())
                .fieldFrom(JsonTFieldBuilder.scalar("name", ScalarType.STR))
                .build();
        MemorySink mem = new MemorySink();
        ValidationPipeline p = ValidationPipeline.builder(schema)
                .withoutConsole()
                .withSink(mem)
                .build();
        // id is null (absent)
        List<JsonTRow> clean = p.validateRows(List.of(
                JsonTRow.of(JsonTValue.nullValue(), JsonTValue.text("Alice"))
        ));
        assertEquals(0, clean.size());
        assertFalse(mem.fatalEvents().isEmpty());
    }

    @Test
    void nonOptionalField_null_rejectsRow() throws Exception {
        // Field declared non-optional but value is Null
        MemorySink mem = new MemorySink();
        ValidationPipeline p = ValidationPipeline.builder(simpleSchema())
                .withoutConsole()
                .withSink(mem)
                .build();
        List<JsonTRow> clean = p.validateRows(List.of(
                JsonTRow.of(JsonTValue.nullValue(), JsonTValue.text("Alice"))
        ));
        assertEquals(0, clean.size());
    }

    @Test
    void optionalField_absent_passesThrough() throws Exception {
        JsonTSchema schema = JsonTSchemaBuilder.straight("Test")
                .fieldFrom(JsonTFieldBuilder.scalar("id", ScalarType.I64))
                .fieldFrom(JsonTFieldBuilder.scalar("name", ScalarType.STR).optional())
                .build();
        MemorySink mem = new MemorySink();
        ValidationPipeline p = ValidationPipeline.builder(schema)
                .withoutConsole()
                .withSink(mem)
                .build();
        List<JsonTRow> clean = p.validateRows(List.of(
                JsonTRow.of(JsonTValue.i64(1), JsonTValue.nullValue())
        ));
        assertEquals(1, clean.size());
    }

    @Test
    void minValue_violation_producesWarning() throws Exception {
        JsonTSchema schema = JsonTSchemaBuilder.straight("Test")
                .fieldFrom(JsonTFieldBuilder.scalar("qty", ScalarType.I32).minValue(1))
                .build();
        MemorySink mem = new MemorySink();
        ValidationPipeline p = ValidationPipeline.builder(schema)
                .withoutConsole()
                .withSink(mem)
                .build();
        List<JsonTRow> clean = p.validateRows(List.of(
                JsonTRow.of(JsonTValue.i32(0))
        ));
        // row passes (warning, not fatal)
        assertEquals(1, clean.size());
        assertFalse(mem.warningEvents().isEmpty());
    }

    @Test
    void maxValue_violation_producesWarning() throws Exception {
        JsonTSchema schema = JsonTSchemaBuilder.straight("Test")
                .fieldFrom(JsonTFieldBuilder.scalar("qty", ScalarType.I32).maxValue(100))
                .build();
        MemorySink mem = new MemorySink();
        ValidationPipeline p = ValidationPipeline.builder(schema)
                .withoutConsole()
                .withSink(mem)
                .build();
        List<JsonTRow> clean = p.validateRows(List.of(
                JsonTRow.of(JsonTValue.i32(200))
        ));
        assertEquals(1, clean.size());
        assertFalse(mem.warningEvents().isEmpty());
    }

    @Test
    void minLength_violation_producesWarning() throws Exception {
        JsonTSchema schema = JsonTSchemaBuilder.straight("Test")
                .fieldFrom(JsonTFieldBuilder.scalar("name", ScalarType.STR).minLength(3))
                .build();
        MemorySink mem = new MemorySink();
        ValidationPipeline p = ValidationPipeline.builder(schema)
                .withoutConsole()
                .withSink(mem)
                .build();
        List<JsonTRow> clean = p.validateRows(List.of(
                JsonTRow.of(JsonTValue.text("AB"))
        ));
        assertEquals(1, clean.size());
        assertFalse(mem.warningEvents().isEmpty());
    }

    @Test
    void maxLength_violation_producesWarning() throws Exception {
        JsonTSchema schema = JsonTSchemaBuilder.straight("Test")
                .fieldFrom(JsonTFieldBuilder.scalar("name", ScalarType.STR).maxLength(5))
                .build();
        MemorySink mem = new MemorySink();
        ValidationPipeline p = ValidationPipeline.builder(schema)
                .withoutConsole()
                .withSink(mem)
                .build();
        List<JsonTRow> clean = p.validateRows(List.of(
                JsonTRow.of(JsonTValue.text("TooLongString"))
        ));
        assertEquals(1, clean.size());
        assertFalse(mem.warningEvents().isEmpty());
    }

    @Test
    void pattern_match_passes() throws Exception {
        JsonTSchema schema = JsonTSchemaBuilder.straight("Test")
                .fieldFrom(JsonTFieldBuilder.scalar("code", ScalarType.STR).pattern("^[A-Z]+$"))
                .build();
        MemorySink mem = new MemorySink();
        ValidationPipeline p = ValidationPipeline.builder(schema)
                .withoutConsole()
                .withSink(mem)
                .build();
        List<JsonTRow> clean = p.validateRows(List.of(
                JsonTRow.of(JsonTValue.text("ABC"))
        ));
        assertEquals(1, clean.size());
        assertTrue(mem.warningEvents().isEmpty());
    }

    @Test
    void pattern_noMatch_producesWarning() throws Exception {
        JsonTSchema schema = JsonTSchemaBuilder.straight("Test")
                .fieldFrom(JsonTFieldBuilder.scalar("code", ScalarType.STR).pattern("^[A-Z]+$"))
                .build();
        MemorySink mem = new MemorySink();
        ValidationPipeline p = ValidationPipeline.builder(schema)
                .withoutConsole()
                .withSink(mem)
                .build();
        List<JsonTRow> clean = p.validateRows(List.of(
                JsonTRow.of(JsonTValue.text("abc123"))
        ));
        assertEquals(1, clean.size()); // warning, not fatal
        assertFalse(mem.warningEvents().isEmpty());
    }

    @Test
    void uniqueness_violation_rejectsSecondDuplicate() throws Exception {
        JsonTSchema schema = JsonTSchemaBuilder.straight("Test")
                .fieldFrom(JsonTFieldBuilder.scalar("id", ScalarType.I64))
                .validationFrom(JsonTValidationBlockBuilder.create().unique("id"))
                .build();
        MemorySink mem = new MemorySink();
        ValidationPipeline p = ValidationPipeline.builder(schema)
                .withoutConsole()
                .withSink(mem)
                .build();
        List<JsonTRow> clean = p.validateRows(List.of(
                JsonTRow.of(JsonTValue.i64(1)),
                JsonTRow.of(JsonTValue.i64(1))  // duplicate
        ));
        assertEquals(1, clean.size()); // only first passes
        assertFalse(mem.fatalEvents().isEmpty());
    }

    @Test
    void ruleExpression_false_producesWarningButPasses() throws Exception {
        // Rule: id > 10 — we pass id=5 which is false → warning but row still passes
        JsonTSchema schema = JsonTSchemaBuilder.straight("Test")
                .fieldFrom(JsonTFieldBuilder.scalar("id", ScalarType.I64))
                .validationFrom(JsonTValidationBlockBuilder.create()
                        .rule(JsonTExpression.binary(BinaryOp.GT,
                                JsonTExpression.fieldName("id"),
                                JsonTExpression.literal(JsonTValue.i64(10)))))
                .build();
        MemorySink mem = new MemorySink();
        ValidationPipeline p = ValidationPipeline.builder(schema)
                .withoutConsole()
                .withSink(mem)
                .build();
        List<JsonTRow> clean = p.validateRows(List.of(
                JsonTRow.of(JsonTValue.i64(5))
        ));
        assertEquals(1, clean.size()); // passes with warning
        assertFalse(mem.warningEvents().isEmpty());
    }

    @Test
    void validateOne_processesRowWithoutLifecycleEvents() throws Exception {
        MemorySink mem = new MemorySink();
        ValidationPipeline p = ValidationPipeline.builder(simpleSchema())
                .withoutConsole()
                .withSink(mem)
                .build();
        List<JsonTRow> clean = new java.util.ArrayList<>();
        p.validateOne(JsonTRow.of(JsonTValue.i64(1), JsonTValue.text("Alice")), clean::add);
        assertEquals(1, clean.size());
        // No ProcessStarted / ProcessCompleted events
        boolean hasProcess = mem.events().stream()
                .anyMatch(e -> e.kind() instanceof io.github.datakore.jsont.diagnostic.DiagnosticEventKind.ProcessStarted);
        assertFalse(hasProcess);
    }

    @Test
    void finish_flushesSinks() throws Exception {
        MemorySink mem = new MemorySink();
        ValidationPipeline p = ValidationPipeline.builder(simpleSchema())
                .withoutConsole()
                .withSink(mem)
                .build();
        assertDoesNotThrow(p::finish);
    }

    @Test
    void validateRows_withMemorySink_capturesAllEvents() throws Exception {
        MemorySink mem = new MemorySink();
        ValidationPipeline p = ValidationPipeline.builder(simpleSchema())
                .withoutConsole()
                .withSink(mem)
                .build();
        p.validateRows(List.of(
                JsonTRow.of(JsonTValue.i64(1), JsonTValue.text("Alice")),
                JsonTRow.of(JsonTValue.i64(2), JsonTValue.text("Bob"))
        ));
        // Should have at least ProcessStarted + 2 RowAccepted + ProcessCompleted
        assertTrue(mem.size() >= 4);
    }
}
