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
import io.github.datakore.jsont.parse.JsonTParser;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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

    // ─── constantValue (P1) ───────────────────────────────────────────────────

    @Test
    void constantValue_match_passes() throws Exception {
        JsonTSchema schema = JsonTSchemaBuilder.straight("Test")
                .fieldFrom(JsonTFieldBuilder.scalar("status", ScalarType.I32)
                        .constantValue(JsonTValue.i32(1)))
                .build();
        MemorySink mem = new MemorySink();
        ValidationPipeline p = ValidationPipeline.builder(schema)
                .withoutConsole()
                .withSink(mem)
                .build();
        List<JsonTRow> clean = p.validateRows(List.of(
                JsonTRow.of(JsonTValue.i32(1))
        ));
        assertEquals(1, clean.size());
        assertTrue(mem.fatalEvents().isEmpty());
    }

    @Test
    void constantValue_mismatch_rejectsRow() throws Exception {
        JsonTSchema schema = JsonTSchemaBuilder.straight("Test")
                .fieldFrom(JsonTFieldBuilder.scalar("status", ScalarType.I32)
                        .constantValue(JsonTValue.i32(1)))
                .build();
        MemorySink mem = new MemorySink();
        ValidationPipeline p = ValidationPipeline.builder(schema)
                .withoutConsole()
                .withSink(mem)
                .build();
        List<JsonTRow> clean = p.validateRows(List.of(
                JsonTRow.of(JsonTValue.i32(99))
        ));
        assertEquals(0, clean.size());
        assertFalse(mem.fatalEvents().isEmpty());
    }

    // ─── ConditionalRequirement (P1) ─────────────────────────────────────────

    @Test
    void conditionalRule_notTriggered_passes() throws Exception {
        // condition: qty > 100 — qty=5 is false → required field "note" not enforced
        JsonTSchema schema = JsonTSchemaBuilder.straight("Test")
                .fieldFrom(JsonTFieldBuilder.scalar("qty", ScalarType.I32))
                .fieldFrom(JsonTFieldBuilder.scalar("note", ScalarType.STR).optional())
                .validationFrom(JsonTValidationBlockBuilder.create()
                        .conditionalRule(
                                JsonTExpression.binary(BinaryOp.GT,
                                        JsonTExpression.fieldName("qty"),
                                        JsonTExpression.literal(JsonTValue.i32(100))),
                                FieldPath.single("note")))
                .build();
        MemorySink mem = new MemorySink();
        ValidationPipeline p = ValidationPipeline.builder(schema)
                .withoutConsole()
                .withSink(mem)
                .build();
        List<JsonTRow> clean = p.validateRows(List.of(
                JsonTRow.of(JsonTValue.i32(5), JsonTValue.nullValue())
        ));
        assertEquals(1, clean.size());
        assertTrue(mem.fatalEvents().isEmpty());
    }

    @Test
    void conditionalRule_triggered_rejectsRow() throws Exception {
        // condition: qty > 100 — qty=200 is true → "note" is null → ConditionalRequirementViolation
        JsonTSchema schema = JsonTSchemaBuilder.straight("Test")
                .fieldFrom(JsonTFieldBuilder.scalar("qty", ScalarType.I32))
                .fieldFrom(JsonTFieldBuilder.scalar("note", ScalarType.STR).optional())
                .validationFrom(JsonTValidationBlockBuilder.create()
                        .conditionalRule(
                                JsonTExpression.binary(BinaryOp.GT,
                                        JsonTExpression.fieldName("qty"),
                                        JsonTExpression.literal(JsonTValue.i32(100))),
                                FieldPath.single("note")))
                .build();
        MemorySink mem = new MemorySink();
        ValidationPipeline p = ValidationPipeline.builder(schema)
                .withoutConsole()
                .withSink(mem)
                .build();
        List<JsonTRow> clean = p.validateRows(List.of(
                JsonTRow.of(JsonTValue.i32(200), JsonTValue.nullValue())
        ));
        assertEquals(0, clean.size());
        assertFalse(mem.fatalEvents().isEmpty());
    }

    // ─── validateStream — batch-style collection ──────────────────────────────

    @Test
    void stream_batch_allValid_allEmitted() throws Exception {
        ValidationPipeline p = ValidationPipeline.builder(simpleSchema())
                .withoutConsole().workers(2).build();
        List<JsonTRow> input = List.of(
                JsonTRow.at(0, JsonTValue.i64(1), JsonTValue.text("Alice")),
                JsonTRow.at(1, JsonTValue.i64(2), JsonTValue.text("Bob")),
                JsonTRow.at(2, JsonTValue.i64(3), JsonTValue.text("Carol"))
        );
        List<JsonTRow> result;
        try (Stream<JsonTRow> s = p.validateStream(input.stream())) {
            result = s.collect(Collectors.toList());
        }
        assertEquals(3, result.size());
    }

    @Test
    void stream_batch_fatalRows_filtered() throws Exception {
        ValidationPipeline p = ValidationPipeline.builder(simpleSchema())
                .withoutConsole().workers(2).build();
        // Row 1: valid; Row 2: shape mismatch (1 value instead of 2) → fatal; Row 3: valid
        List<JsonTRow> input = List.of(
                JsonTRow.at(0, JsonTValue.i64(1), JsonTValue.text("Alice")),
                JsonTRow.at(1, JsonTValue.i64(2)),                           // missing name — shape mismatch
                JsonTRow.at(2, JsonTValue.i64(3), JsonTValue.text("Carol"))
        );
        List<JsonTRow> result;
        try (Stream<JsonTRow> s = p.validateStream(input.stream())) {
            result = s.collect(Collectors.toList());
        }
        assertEquals(2, result.size());
    }

    @Test
    void stream_batch_warningRows_included() throws Exception {
        JsonTSchema schema = JsonTSchemaBuilder.straight("Test")
                .fieldFrom(JsonTFieldBuilder.scalar("qty", ScalarType.I32).minValue(5))
                .build();
        CopyOnWriteArrayList<DiagnosticEvent> events = new CopyOnWriteArrayList<>();
        ValidationPipeline p = ValidationPipeline.builder(schema)
                .withoutConsole().withSink(events::add).workers(2).build();
        // qty=1 violates minValue(5) → warning, but row still passes
        List<JsonTRow> result;
        try (Stream<JsonTRow> s = p.validateStream(Stream.of(JsonTRow.at(0, JsonTValue.i32(1))))) {
            result = s.collect(Collectors.toList());
        }
        assertEquals(1, result.size());
        assertTrue(events.stream().anyMatch(e -> e.severity() == DiagnosticSeverity.WARNING));
    }

    @Test
    void stream_batch_orderPreserved() throws Exception {
        // 20 rows with sequential ids — output must appear in the same order
        ValidationPipeline p = ValidationPipeline.builder(simpleSchema())
                .withoutConsole().workers(4).bufferCapacity(8).build();
        List<JsonTRow> input = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            input.add(JsonTRow.at(i, JsonTValue.i64(i), JsonTValue.text("u" + i)));
        }
        List<JsonTRow> result;
        try (Stream<JsonTRow> s = p.validateStream(input.stream())) {
            result = s.collect(Collectors.toList());
        }
        assertEquals(20, result.size());
        for (int i = 0; i < 20; i++) {
            assertEquals(i, result.get(i).get(0).toDouble(), "order mismatch at position " + i);
        }
    }

    // ─── validateStream — parallel / non-batch ────────────────────────────────

    @Test
    void stream_parallel_largeDataset_allValidRowsPresent() throws Exception {
        ValidationPipeline p = ValidationPipeline.builder(simpleSchema())
                .withoutConsole().workers(4).bufferCapacity(64).build();
        int total = 200;
        List<JsonTRow> input = new ArrayList<>();
        for (int i = 0; i < total; i++) {
            input.add(JsonTRow.at(i, JsonTValue.i64(i), JsonTValue.text("user" + i)));
        }
        long count;
        try (Stream<JsonTRow> s = p.validateStream(input.stream())) {
            count = s.count();
        }
        assertEquals(total, count);
    }

    @Test
    void stream_parallel_diagnosticEvents_emittedForFatal() throws Exception {
        CopyOnWriteArrayList<DiagnosticEvent> events = new CopyOnWriteArrayList<>();
        ValidationPipeline p = ValidationPipeline.builder(simpleSchema())
                .withoutConsole().withSink(events::add).workers(3).build();
        // Two bad rows (shape mismatch) mixed with one valid row
        List<JsonTRow> input = List.of(
                JsonTRow.at(0, JsonTValue.i64(1)),          // missing name → fatal
                JsonTRow.at(1, JsonTValue.i64(2), JsonTValue.text("Bob")),
                JsonTRow.at(2, JsonTValue.i64(3))            // missing name → fatal
        );
        List<JsonTRow> result;
        try (Stream<JsonTRow> s = p.validateStream(input.stream())) {
            result = s.collect(Collectors.toList());
        }
        assertEquals(1, result.size());
        long fatalCount = events.stream().filter(DiagnosticEvent::isFatal).count();
        assertTrue(fatalCount >= 2, "expected at least 2 fatal events, got " + fatalCount);
    }

    @Test
    void stream_parallel_uniquenessCheck_oneDuplicateRejected() throws Exception {
        JsonTSchema schema = JsonTSchemaBuilder.straight("Test")
                .fieldFrom(JsonTFieldBuilder.scalar("id", ScalarType.I64))
                .validationFrom(JsonTValidationBlockBuilder.create().unique("id"))
                .build();
        CopyOnWriteArrayList<DiagnosticEvent> events = new CopyOnWriteArrayList<>();
        ValidationPipeline p = ValidationPipeline.builder(schema)
                .withoutConsole().withSink(events::add).workers(2).build();
        List<JsonTRow> input = List.of(
                JsonTRow.at(0, JsonTValue.i64(42)),
                JsonTRow.at(1, JsonTValue.i64(42))  // duplicate
        );
        List<JsonTRow> result;
        try (Stream<JsonTRow> s = p.validateStream(input.stream())) {
            result = s.collect(Collectors.toList());
        }
        // Exactly one of the two duplicate rows must be rejected
        assertEquals(1, result.size());
        assertTrue(events.stream().anyMatch(DiagnosticEvent::isFatal));
    }

    @Test
    void stream_integratesWithParserStream() throws Exception {
        // Build a raw row string and pipe JsonTParser.parseRowsStreaming → validateStream
        String rows = "{1,\"Alice\"},{2,\"Bob\"},{3,\"Carol\"}";
        ValidationPipeline p = ValidationPipeline.builder(simpleSchema())
                .withoutConsole().workers(2).build();
        List<JsonTRow> result;
        try (Stream<JsonTRow> parsed = JsonTParser.parseRowsStreaming(new StringReader(rows));
             Stream<JsonTRow> validated = p.validateStream(parsed)) {
            result = validated.collect(Collectors.toList());
        }
        assertEquals(3, result.size());
    }
}
