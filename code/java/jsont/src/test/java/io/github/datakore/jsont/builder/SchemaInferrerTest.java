package io.github.datakore.jsont.builder;

import io.github.datakore.jsont.error.BuildError;
import io.github.datakore.jsont.model.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SchemaInferrerTest {

    // ── helpers ───────────────────────────────────────────────────────────────

    /** Three rows: i64, str, bool-with-null column. */
    static List<JsonTRow> sampleRows() {
        return List.of(
                JsonTRow.of(JsonTValue.i64(1L),  JsonTValue.text("Alice"), JsonTValue.bool(true)),
                JsonTRow.of(JsonTValue.i64(2L),  JsonTValue.text("Bob"),   JsonTValue.bool(false)),
                JsonTRow.of(JsonTValue.i64(3L),  JsonTValue.text("Carol"), JsonTValue.nullValue())
        );
    }

    // ── auto-generated field names ────────────────────────────────────────────

    @Test void infer_autoNames() throws BuildError {
        JsonTSchema schema = SchemaInferrer.create()
                .schemaName("Person")
                .infer(sampleRows());

        assertEquals("Person", schema.name());
        assertTrue(schema.isStraight());
        assertEquals(3, schema.fieldCount());
        assertEquals("field_0", schema.fields().get(0).name());
        assertEquals("field_1", schema.fields().get(1).name());
        assertEquals("field_2", schema.fields().get(2).name());
    }

    // ── type inference ────────────────────────────────────────────────────────

    @Test void infer_types_intAndStrAndBool() throws BuildError {
        JsonTSchema schema = SchemaInferrer.create().infer(sampleRows());
        assertEquals(ScalarType.I64, schema.fields().get(0).scalarType());
        assertEquals(ScalarType.STR, schema.fields().get(1).scalarType());
        // third column has a null — still Bool because all non-null values are Bool
        assertEquals(ScalarType.BOOL, schema.fields().get(2).scalarType());
    }

    @Test void infer_types_floatWidening() throws BuildError {
        List<JsonTRow> rows = List.of(
                JsonTRow.of(JsonTValue.d32(1.0f)),
                JsonTRow.of(JsonTValue.d64(2.0))
        );
        JsonTSchema schema = SchemaInferrer.create().infer(rows);
        assertEquals(ScalarType.D64, schema.fields().get(0).scalarType());
    }

    @Test void infer_types_mixedIntAndFloat_widenedToD64() throws BuildError {
        List<JsonTRow> rows = List.of(
                JsonTRow.of(JsonTValue.i32(1)),
                JsonTRow.of(JsonTValue.d64(2.0))
        );
        JsonTSchema schema = SchemaInferrer.create().infer(rows);
        assertEquals(ScalarType.D64, schema.fields().get(0).scalarType());
    }

    @Test void infer_types_stringWinsOverNumeric() throws BuildError {
        List<JsonTRow> rows = List.of(
                JsonTRow.of(JsonTValue.i32(1)),
                JsonTRow.of(JsonTValue.text("hello"))
        );
        JsonTSchema schema = SchemaInferrer.create().infer(rows);
        assertEquals(ScalarType.STR, schema.fields().get(0).scalarType());
    }

    // ── nullable columns ──────────────────────────────────────────────────────

    @Test void infer_nullableColumn_markedOptional_withZeroThreshold() throws BuildError {
        // Third column has one null out of 3 rows — fraction = 0.33 > 0.0 (default)
        JsonTSchema schema = SchemaInferrer.create().infer(sampleRows());
        assertFalse(schema.fields().get(0).optional());  // no nulls
        assertFalse(schema.fields().get(1).optional());  // no nulls
        assertTrue(schema.fields().get(2).optional());   // has one null
    }

    @Test void infer_nullable_notOptional_whenThresholdNotExceeded() throws BuildError {
        // Threshold 0.5 means > 50% nulls needed; 1/3 = 0.33 < 0.5 → not optional
        JsonTSchema schema = SchemaInferrer.create()
                .nullableThreshold(0.5)
                .infer(sampleRows());
        assertFalse(schema.fields().get(2).optional());
    }

    // ── name hints ────────────────────────────────────────────────────────────

    @Test void inferWithNames_appliesHints() throws BuildError {
        JsonTSchema schema = SchemaInferrer.create()
                .schemaName("Person")
                .inferWithNames(sampleRows(), List.of("id", "name", "active"));
        assertEquals("id",     schema.fields().get(0).name());
        assertEquals("name",   schema.fields().get(1).name());
        assertEquals("active", schema.fields().get(2).name());
    }

    @Test void inferWithNames_mismatchedHints_throwsBuildError() {
        assertThrows(BuildError.class, () ->
                SchemaInferrer.create().inferWithNames(sampleRows(), List.of("id", "name")));
    }

    // ── empty rows ────────────────────────────────────────────────────────────

    @Test void infer_emptyRows_returnsZeroFieldSchema() throws BuildError {
        JsonTSchema schema = SchemaInferrer.create()
                .schemaName("Empty")
                .infer(List.of());
        assertEquals("Empty", schema.name());
        assertTrue(schema.isStraight());
        assertEquals(0, schema.fieldCount());
    }

    // ── sample size limit ─────────────────────────────────────────────────────

    @Test void infer_sampleSizeLimit_onlyExaminesFirstNRows() throws BuildError {
        // Rows 1-2: i32; row 3: text — with sampleSize=2 only i32 rows are seen
        List<JsonTRow> rows = List.of(
                JsonTRow.of(JsonTValue.i32(1)),
                JsonTRow.of(JsonTValue.i32(2)),
                JsonTRow.of(JsonTValue.text("three")),
                JsonTRow.of(JsonTValue.text("four")),
                JsonTRow.of(JsonTValue.text("five"))
        );
        JsonTSchema schema = SchemaInferrer.create().sampleSize(2).infer(rows);
        assertEquals(ScalarType.I32, schema.fields().get(0).scalarType());
    }

    // ── integer widening ─────────────────────────────────────────────────────

    @Test void infer_integerWidening_i16_to_i32_to_i64() throws BuildError {
        List<JsonTRow> rows = List.of(
                JsonTRow.of(JsonTValue.i16((short) 1)),
                JsonTRow.of(JsonTValue.i32(2)),
                JsonTRow.of(JsonTValue.i64(3L))
        );
        JsonTSchema schema = SchemaInferrer.create().infer(rows);
        assertEquals(ScalarType.I64, schema.fields().get(0).scalarType());
    }
}
