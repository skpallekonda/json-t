package io.github.datakore.jsont.json;

import io.github.datakore.jsont.JsonT;
import io.github.datakore.jsont.builder.JsonTFieldBuilder;
import io.github.datakore.jsont.builder.JsonTSchemaBuilder;
import io.github.datakore.jsont.error.JsonTError;
import io.github.datakore.jsont.internal.json.JsonValueNode;
import io.github.datakore.jsont.internal.json.JsonValueParser;
import io.github.datakore.jsont.model.*;
import io.github.datakore.jsont.stringify.RowWriter;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for JSON interoperability: parser boundary conditions, writer output,
 * and full JsonT ↔ JSON round-trips.
 */
class JsonInteropTest {

    // ── Shared schema ─────────────────────────────────────────────────────────
    //
    // Order: id(i64) | product(str) | qty(i32) | active(bool) | price(d64, optional)

    private static JsonTSchema orderSchema() {
        return JsonTSchemaBuilder.straight("Order")
                .fieldFrom(JsonTFieldBuilder.scalar("id",      ScalarType.I64))
                .fieldFrom(JsonTFieldBuilder.scalar("product", ScalarType.STR))
                .fieldFrom(JsonTFieldBuilder.scalar("qty",     ScalarType.I32))
                .fieldFrom(JsonTFieldBuilder.scalar("active",  ScalarType.BOOL))
                .fieldFrom(JsonTFieldBuilder.scalar("price",   ScalarType.D64).optional())
                .build();
    }

    private static JsonTRow orderRow(long id, String product, int qty, boolean active, Double price) {
        return JsonTRow.of(
                JsonTValue.i64(id),
                JsonTValue.text(product),
                JsonTValue.i32(qty),
                JsonTValue.bool(active),
                price != null ? JsonTValue.d64(price) : JsonTValue.nullValue()
        );
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Stringify a list of rows to JsonT wire format. */
    private static String toJsonT(List<JsonTRow> rows) throws IOException {
        var sw = new StringWriter();
        RowWriter.writeRows(rows, sw);
        return sw.toString();
    }

    // ==========================================================================
    // 1. JSON parser boundary conditions
    // ==========================================================================

    // ── Internal JsonValueParser tests ────────────────────────────────────────

    @Test
    void parser_plain_integer() {
        var node = new JsonValueParser("42").parseValue();
        assertInstanceOf(JsonValueNode.IntNum.class, node);
        assertEquals(42L, ((JsonValueNode.IntNum) node).value());
    }

    @Test
    void parser_negative_integer() {
        var node = new JsonValueParser("-7").parseValue();
        assertInstanceOf(JsonValueNode.IntNum.class, node);
        assertEquals(-7L, ((JsonValueNode.IntNum) node).value());
    }

    @Test
    void parser_float_with_decimal() {
        var node = new JsonValueParser("3.14").parseValue();
        assertInstanceOf(JsonValueNode.FloatNum.class, node);
        assertEquals(3.14, ((JsonValueNode.FloatNum) node).value(), 1e-10);
    }

    @Test
    void parser_float_positive_exponent() {
        // 1.5e2 = 150.0
        var node = new JsonValueParser("1.5e2").parseValue();
        assertInstanceOf(JsonValueNode.FloatNum.class, node);
        assertEquals(150.0, ((JsonValueNode.FloatNum) node).value(), 1e-10);
    }

    @Test
    void parser_float_negative_exponent() {
        // 5e-1 = 0.5
        var node = new JsonValueParser("5e-1").parseValue();
        assertInstanceOf(JsonValueNode.FloatNum.class, node);
        assertEquals(0.5, ((JsonValueNode.FloatNum) node).value(), 1e-10);
    }

    @Test
    void parser_float_uppercase_exponent() {
        var node = new JsonValueParser("2E3").parseValue();
        assertInstanceOf(JsonValueNode.FloatNum.class, node);
        assertEquals(2000.0, ((JsonValueNode.FloatNum) node).value(), 1e-6);
    }

    @Test
    void parser_zero() {
        var node = new JsonValueParser("0").parseValue();
        assertInstanceOf(JsonValueNode.IntNum.class, node);
        assertEquals(0L, ((JsonValueNode.IntNum) node).value());
    }

    @Test
    void parser_large_integer_overflows_to_float() {
        // Long.MAX_VALUE + 1 cannot fit in long → falls back to FloatNum
        var node = new JsonValueParser("9223372036854775808").parseValue();
        assertInstanceOf(JsonValueNode.FloatNum.class, node,
                "oversized integer must fall back to FloatNum");
    }

    /** JSON does not allow numbers that start with a decimal point (.1234). */
    @Test
    void parser_rejects_leading_decimal_dot() {
        assertThrows(JsonTError.Parse.class,
                () -> new JsonValueParser(".1234").parseValue(),
                "leading-decimal .1234 is invalid JSON and must be rejected");
    }

    @Test
    void parser_rejects_empty_input() {
        assertThrows(JsonTError.Parse.class,
                () -> new JsonValueParser("").parseValue(),
                "empty input must be rejected");
    }

    @Test
    void parser_rejects_truncated_string() {
        assertThrows(JsonTError.Parse.class,
                () -> new JsonValueParser("\"hello").parseValue(),
                "unterminated string must fail");
    }

    @Test
    void parser_rejects_truncated_object() {
        assertThrows(JsonTError.Parse.class,
                () -> new JsonValueParser("{\"key\":").parseValue(),
                "truncated object must fail");
    }

    @Test
    void parser_unicode_escape_sequence() {
        var node = new JsonValueParser("\"Caf\\u00e9\"").parseValue();
        assertInstanceOf(JsonValueNode.Str.class, node);
        assertEquals("Café", ((JsonValueNode.Str) node).value());
    }

    @Test
    void parser_all_control_escapes() {
        // \b \f \n \r \t \" \\
        var node = new JsonValueParser("\"\\b\\f\\n\\r\\t\\\"\\\\\"").parseValue();
        assertInstanceOf(JsonValueNode.Str.class, node);
        String val = ((JsonValueNode.Str) node).value();
        assertTrue(val.contains("\b"), "\\b");
        assertTrue(val.contains("\f"), "\\f");
        assertTrue(val.contains("\n"), "\\n");
        assertTrue(val.contains("\r"), "\\r");
        assertTrue(val.contains("\t"), "\\t");
        assertTrue(val.contains("\""), "\\\"");
        assertTrue(val.contains("\\"), "\\\\");
    }

    @Test
    void parser_nested_object() {
        var node = new JsonValueParser("{\"a\":1,\"b\":true}").parseValue();
        assertInstanceOf(JsonValueNode.Obj.class, node);
        var pairs = ((JsonValueNode.Obj) node).pairs();
        assertEquals(2, pairs.size());
        assertEquals("a", pairs.get(0).getKey());
        assertEquals("b", pairs.get(1).getKey());
    }

    @Test
    void parser_empty_object() {
        var node = new JsonValueParser("{}").parseValue();
        assertInstanceOf(JsonValueNode.Obj.class, node);
        assertTrue(((JsonValueNode.Obj) node).pairs().isEmpty());
    }

    @Test
    void parser_empty_array() {
        var node = new JsonValueParser("[]").parseValue();
        assertInstanceOf(JsonValueNode.Arr.class, node);
        assertTrue(((JsonValueNode.Arr) node).items().isEmpty());
    }

    @Test
    void parser_bool_true_and_false() {
        assertInstanceOf(JsonValueNode.Bool.class, new JsonValueParser("true").parseValue());
        assertInstanceOf(JsonValueNode.Bool.class, new JsonValueParser("false").parseValue());
        assertTrue(((JsonValueNode.Bool) new JsonValueParser("true").parseValue()).value());
        assertFalse(((JsonValueNode.Bool) new JsonValueParser("false").parseValue()).value());
    }

    @Test
    void parser_null_literal() {
        assertInstanceOf(JsonValueNode.Null.class, new JsonValueParser("null").parseValue());
    }

    // ── Policy tests via JsonReader ───────────────────────────────────────────

    @Test
    void reader_unknown_field_rejected() {
        var reader = JsonReader.withSchema(orderSchema())
                .mode(JsonInputMode.OBJECT)
                .unknownFields(UnknownFieldPolicy.REJECT)
                .build();
        assertThrows(JsonTError.Parse.class,
                () -> reader.read(
                        "{\"id\":1,\"product\":\"X\",\"qty\":1,\"active\":true,\"price\":null,\"extra\":\"oops\"}",
                        _ -> {}));
    }

    @Test
    void reader_missing_field_rejected() {
        var reader = JsonReader.withSchema(orderSchema())
                .mode(JsonInputMode.OBJECT)
                .missingFields(MissingFieldPolicy.REJECT)
                .build();
        assertThrows(JsonTError.Parse.class,
                () -> reader.read("{\"id\":1,\"product\":\"X\"}", _ -> {}));
    }

    @Test
    void reader_missing_field_use_default_produces_null() {
        var reader = JsonReader.withSchema(orderSchema())
                .mode(JsonInputMode.OBJECT)
                .missingFields(MissingFieldPolicy.USE_DEFAULT)
                .build();
        List<JsonTRow> rows = new ArrayList<>();
        reader.read("{\"id\":1,\"product\":\"X\",\"qty\":5,\"active\":true}", rows::add);
        assertEquals(1, rows.size());
        assertInstanceOf(JsonTValue.Null.class, rows.get(0).get(4),
                "missing optional price must be null");
    }

    @Test
    void reader_ndjson_three_rows() {
        var reader = JsonReader.withSchema(orderSchema())
                .mode(JsonInputMode.NDJSON)
                .build();
        String input =
                "{\"id\":1,\"product\":\"A\",\"qty\":1,\"active\":true,\"price\":null}\n" +
                "{\"id\":2,\"product\":\"B\",\"qty\":2,\"active\":false,\"price\":2.5}\n" +
                "{\"id\":3,\"product\":\"C\",\"qty\":3,\"active\":true,\"price\":null}";
        List<JsonTRow> rows = new ArrayList<>();
        int n = reader.read(input, rows::add);
        assertEquals(3, n);
        assertInstanceOf(io.github.datakore.jsont.model.JsonTNumber.I64.class, rows.get(0).get(0));
        assertEquals(3L, ((JsonTNumber.I64) rows.get(2).get(0)).value());
    }

    @Test
    void reader_ndjson_skips_blank_lines() {
        var reader = JsonReader.withSchema(orderSchema())
                .mode(JsonInputMode.NDJSON)
                .build();
        String input = "\n{\"id\":1,\"product\":\"A\",\"qty\":1,\"active\":true,\"price\":null}\n\n" +
                       "{\"id\":2,\"product\":\"B\",\"qty\":2,\"active\":false,\"price\":null}\n";
        List<JsonTRow> rows = new ArrayList<>();
        assertEquals(2, reader.read(input, rows::add));
    }

    @Test
    void reader_array_mode() {
        var reader = JsonReader.withSchema(orderSchema())
                .mode(JsonInputMode.ARRAY)
                .build();
        String input = "[{\"id\":10,\"product\":\"P1\",\"qty\":5,\"active\":true,\"price\":null}," +
                       "{\"id\":20,\"product\":\"P2\",\"qty\":3,\"active\":false,\"price\":9.5}]";
        List<JsonTRow> rows = new ArrayList<>();
        assertEquals(2, reader.read(input, rows::add));
        assertEquals(10L, ((JsonTNumber.I64) rows.get(0).get(0)).value());
        assertEquals(20L, ((JsonTNumber.I64) rows.get(1).get(0)).value());
    }

    @Test
    void reader_streaming_ndjson() throws IOException {
        var reader = JsonReader.withSchema(orderSchema())
                .mode(JsonInputMode.NDJSON)
                .build();
        String input =
                "{\"id\":1,\"product\":\"A\",\"qty\":1,\"active\":true,\"price\":null}\n" +
                "{\"id\":2,\"product\":\"B\",\"qty\":2,\"active\":false,\"price\":null}";
        List<JsonTRow> rows = new ArrayList<>();
        int n = reader.readStreaming(new StringReader(input), rows::add);
        assertEquals(2, n);
    }

    // ==========================================================================
    // 2. JSON writer
    // ==========================================================================

    @Test
    void writer_single_row_contains_all_field_names() {
        var writer = JsonWriter.withSchema(orderSchema()).build();
        String json = writer.writeRow(orderRow(1, "Widget", 5, true, null));
        assertTrue(json.contains("\"id\""),      "must contain 'id'");
        assertTrue(json.contains("\"product\""), "must contain 'product'");
        assertTrue(json.contains("\"qty\""),     "must contain 'qty'");
        assertTrue(json.contains("\"active\""),  "must contain 'active'");
        assertTrue(json.contains("\"price\""),   "must contain 'price'");
    }

    @Test
    void writer_values_serialised_correctly() {
        var writer = JsonWriter.withSchema(orderSchema()).build();
        String json = writer.writeRow(orderRow(99, "Acme", 3, false, 7.5));
        assertTrue(json.contains("99"),      "id value");
        assertTrue(json.contains("\"Acme\""), "product value");
        assertTrue(json.contains("3"),       "qty value");
        assertTrue(json.contains("false"),   "active value");
        assertTrue(json.contains("7.5"),     "price value");
    }

    @Test
    void writer_null_field_emits_json_null() {
        var writer = JsonWriter.withSchema(orderSchema()).build();
        String json = writer.writeRow(orderRow(1, "X", 1, true, null));
        assertTrue(json.contains("\"price\":null"), "null field must serialize as JSON null");
    }

    @Test
    void writer_ndjson_one_line_per_row() throws IOException {
        var writer = JsonWriter.withSchema(orderSchema())
                .mode(JsonOutputMode.NDJSON)
                .build();
        List<JsonTRow> rows = List.of(
                orderRow(1, "A", 1, true,  null),
                orderRow(2, "B", 2, false, null),
                orderRow(3, "C", 3, true,  null)
        );
        var sw = new StringWriter();
        writer.writeRows(rows, sw);
        String[] lines = sw.toString().split("\n");
        assertEquals(3, lines.length, "NDJSON must produce one line per row");
    }

    @Test
    void writer_array_mode_wraps_in_brackets() throws IOException {
        var writer = JsonWriter.withSchema(orderSchema())
                .mode(JsonOutputMode.ARRAY)
                .build();
        List<JsonTRow> rows = List.of(orderRow(1, "A", 1, true, null), orderRow(2, "B", 2, false, null));
        var sw = new StringWriter();
        writer.writeRows(rows, sw);
        String text = sw.toString().strip();
        assertTrue(text.startsWith("["), "Array mode must start with '['");
        assertTrue(text.endsWith("]"),   "Array mode must end with ']'");
    }

    @Test
    void writer_pretty_mode_multi_line() {
        var writer = JsonWriter.withSchema(orderSchema()).pretty().build();
        String json = writer.writeRow(orderRow(1, "Gadget", 3, true, 9.0));
        assertTrue(json.lines().count() > 1, "pretty output must span multiple lines");
    }

    @Test
    void writer_string_escaping() {
        var writer = JsonWriter.withSchema(orderSchema()).build();
        // Product name contains double-quote, newline, tab, backslash
        String json = writer.writeRow(orderRow(1, "say \"hi\"\nline\ttab\\end", 1, true, null));
        assertTrue(json.contains("\\\""), "double-quote must be escaped");
        assertTrue(json.contains("\\n"),  "newline must be escaped");
        assertTrue(json.contains("\\t"),  "tab must be escaped");
        assertTrue(json.contains("\\\\"), "backslash must be escaped");
    }

    @Test
    void writer_boolean_literals() {
        var writer = JsonWriter.withSchema(orderSchema()).build();
        assertTrue(writer.writeRow(orderRow(1, "X", 1, true,  null)).contains("true"));
        assertTrue(writer.writeRow(orderRow(1, "X", 1, false, null)).contains("false"));
    }

    // ==========================================================================
    // 3. Round-trip: JsonT → JSON → JsonT (strings must be byte-for-byte equal)
    // ==========================================================================

    @Test
    void round_trip_ndjson() throws IOException {
        var schema = orderSchema();
        List<JsonTRow> rows = List.of(
                orderRow(1001, "Gadget",  7, true,  null),
                orderRow(1002, "Widget",  3, false, 8.5),
                orderRow(1003, "Doohick", 1, true,  null)
        );

        // Original JsonT wire string
        String original = toJsonT(rows);

        // Write to JSON (NDJSON)
        var sw = new StringWriter();
        JsonWriter.withSchema(schema).mode(JsonOutputMode.NDJSON).build()
                .writeRows(rows, sw);
        String jsonStr = sw.toString();

        // Read JSON back into rows
        List<JsonTRow> roundtripRows = new ArrayList<>();
        JsonReader.withSchema(schema).mode(JsonInputMode.NDJSON).build()
                .read(jsonStr, roundtripRows::add);
        assertEquals(3, roundtripRows.size());

        // Re-stringify and compare
        String roundtrip = toJsonT(roundtripRows);
        assertEquals(original, roundtrip,
                "JsonT strings must be identical after NDJSON round-trip\n" +
                "Original : " + original + "\nRoundtrip: " + roundtrip);
    }

    @Test
    void round_trip_array_mode() throws IOException {
        var schema = orderSchema();
        List<JsonTRow> rows = new ArrayList<>();
        for (int i = 1; i <= 4; i++) {
            rows.add(orderRow(i, "Item" + i, i * 10, i % 2 == 0, null));
        }
        String original = toJsonT(rows);

        var sw = new StringWriter();
        JsonWriter.withSchema(schema).mode(JsonOutputMode.ARRAY).build()
                .writeRows(rows, sw);

        List<JsonTRow> roundtripRows = new ArrayList<>();
        JsonReader.withSchema(schema).mode(JsonInputMode.ARRAY).build()
                .read(sw.toString(), roundtripRows::add);

        assertEquals(original, toJsonT(roundtripRows),
                "JsonT strings must match after Array round-trip");
    }

    @Test
    void round_trip_nulls_preserved() throws IOException {
        var schema = orderSchema();
        List<JsonTRow> rows = List.of(
                orderRow(1, "A", 1, true,  null),    // price = null
                orderRow(2, "B", 2, false, 1.0),     // price = 1.0
                orderRow(3, "C", 3, true,  null)     // price = null
        );
        String original = toJsonT(rows);

        var sw = new StringWriter();
        JsonWriter.withSchema(schema).mode(JsonOutputMode.NDJSON).build()
                .writeRows(rows, sw);

        List<JsonTRow> roundtripRows = new ArrayList<>();
        JsonReader.withSchema(schema).mode(JsonInputMode.NDJSON).build()
                .read(sw.toString(), roundtripRows::add);

        assertEquals(original, toJsonT(roundtripRows));
    }

    @Test
    void round_trip_special_chars_in_strings() throws IOException {
        var schema = orderSchema();
        // Product name contains characters that require JSON escaping
        List<JsonTRow> rows = List.of(orderRow(1, "O'Brien & \"Co\" \\ path", 1, true, null));
        String original = toJsonT(rows);

        var sw = new StringWriter();
        JsonWriter.withSchema(schema).build().writeRow(rows.get(0), sw);

        List<JsonTRow> roundtripRows = new ArrayList<>();
        JsonReader.withSchema(schema).mode(JsonInputMode.OBJECT).build()
                .read(sw.toString(), roundtripRows::add);

        assertEquals(original, toJsonT(roundtripRows),
                "special characters must survive JSON round-trip");
    }

    @Test
    void round_trip_via_jsont_facade() {
        // JsonT.fromJson / JsonT.toJson convenience methods
        var schema = orderSchema();
        JsonTRow original = orderRow(42, "Facade", 9, true, null);

        String json = JsonT.toJson(original, schema);
        JsonTRow parsed = JsonT.fromJson(json, schema);

        // Values must match position by position
        for (int i = 0; i < original.size(); i++) {
            assertEquals(original.get(i), parsed.get(i),
                    "field[" + i + "] mismatch after fromJson/toJson");
        }
    }
}
