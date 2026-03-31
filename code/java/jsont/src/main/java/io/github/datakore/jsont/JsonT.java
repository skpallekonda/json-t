package io.github.datakore.jsont;

import io.github.datakore.jsont.builder.JsonTSchemaBuilder;
import io.github.datakore.jsont.json.JsonReader;
import io.github.datakore.jsont.json.JsonReaderBuilder;
import io.github.datakore.jsont.json.JsonWriter;
import io.github.datakore.jsont.json.JsonWriterBuilder;
import io.github.datakore.jsont.model.JsonTNamespace;
import io.github.datakore.jsont.model.JsonTRow;
import io.github.datakore.jsont.model.JsonTSchema;
import io.github.datakore.jsont.parse.JsonTParser;
import io.github.datakore.jsont.parse.RowConsumer;
import io.github.datakore.jsont.parse.RowIter;
import io.github.datakore.jsont.stringify.JsonTStringifier;
import io.github.datakore.jsont.stringify.RowWriter;
import io.github.datakore.jsont.stringify.StringifyOptions;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;

/**
 * Entry point for the JsonT library.
 *
 * <p>Static utilities for row parsing and row writing will be added in the
 * <em>parse</em> phase. Schema building starts with {@link JsonTSchemaBuilder}:
 *
 * <pre>{@code
 *   JsonTSchema order = JsonTSchemaBuilder.straight("Order")
 *       .fieldFrom(JsonTFieldBuilder.scalar("id",      ScalarType.I64))
 *       .fieldFrom(JsonTFieldBuilder.scalar("product", ScalarType.STR).minLength(2))
 *       .fieldFrom(JsonTFieldBuilder.scalar("qty",     ScalarType.I32).minValue(1).maxValue(999))
 *       .fieldFrom(JsonTFieldBuilder.scalar("price",   ScalarType.D64).minValue(0.01))
 *       .build();
 * }</pre>
 */
public final class JsonT {
    private JsonT() {}

    // ── Stringify convenience ─────────────────────────────────────────────────

    /** Serialises a schema to compact text. */
    public static String stringify(JsonTSchema schema) {
        return JsonTStringifier.stringify(schema, StringifyOptions.compact());
    }

    /** Serialises a schema with explicit options. */
    public static String stringify(JsonTSchema schema, StringifyOptions opts) {
        return JsonTStringifier.stringify(schema, opts);
    }

    /** Serialises a namespace to compact text. */
    public static String stringify(JsonTNamespace namespace) {
        return JsonTStringifier.stringify(namespace, StringifyOptions.compact());
    }

    /** Serialises a namespace with explicit options. */
    public static String stringify(JsonTNamespace namespace, StringifyOptions opts) {
        return JsonTStringifier.stringify(namespace, opts);
    }

    /** Serialises a row to compact wire text: {@code {v1,v2,...}}. */
    public static String stringifyRow(JsonTRow row) {
        return JsonTStringifier.stringify(row);
    }

    /**
     * Writes one row directly to {@code w} — no intermediate String allocated.
     *
     * @throws IOException on write failure
     */
    public static void writeRow(JsonTRow row, Writer w) throws IOException {
        RowWriter.writeRow(row, w);
    }

    /**
     * Writes all rows to {@code w}, separated by {@code ,\n}.
     *
     * @throws IOException on write failure
     */
    public static void writeRows(Iterable<JsonTRow> rows, Writer w) throws IOException {
        RowWriter.writeRows(rows, w);
    }

    // ── Parse convenience ─────────────────────────────────────────────────────

    /** Parses a namespace schema DSL document. Throws {@link io.github.datakore.jsont.error.JsonTError.Parse} on invalid input. */
    public static JsonTNamespace parseNamespace(String input) {
        return JsonTParser.parseNamespace(input);
    }

    /** Parses data rows from a string. Returns row count. */
    public static int parseRows(String input, RowConsumer consumer) {
        return JsonTParser.parseRows(input, consumer);
    }

    /** Parses data rows from a Reader (streaming, O(1) memory). Throws {@link java.io.IOException}. */
    public static int parseRowsStreaming(Reader reader, RowConsumer consumer) throws IOException {
        return JsonTParser.parseRowsStreaming(reader, consumer);
    }

    /** Returns a lazy row iterator over {@code reader}. Caller must close the iterator. */
    public static RowIter rowIter(Reader reader) {
        return JsonTParser.rowIter(reader);
    }

    // ── JSON interoperability ─────────────────────────────────────────────────

    /**
     * Returns a builder for a {@link JsonReader} bound to the given schema.
     * Shortcut for {@code JsonReader.withSchema(schema)}.
     */
    public static JsonReaderBuilder jsonReader(JsonTSchema schema) {
        return JsonReader.withSchema(schema);
    }

    /**
     * Returns a builder for a {@link JsonWriter} bound to the given schema.
     * Shortcut for {@code JsonWriter.withSchema(schema)}.
     */
    public static JsonWriterBuilder jsonWriter(JsonTSchema schema) {
        return JsonWriter.withSchema(schema);
    }

    /**
     * Convenience: parse a single JSON object string into one {@link JsonTRow}.
     *
     * @param jsonObject a JSON object literal, e.g. {@code {"id":1,"name":"Alice"}}
     * @param schema     the schema that defines field names and types
     * @return a positional row
     * @throws io.github.datakore.jsont.error.JsonTError.Parse on malformed JSON
     */
    public static JsonTRow fromJson(String jsonObject, JsonTSchema schema) {
        JsonTRow[] holder = new JsonTRow[1];
        JsonReader.withSchema(schema).build().read(jsonObject, row -> holder[0] = row);
        return holder[0];
    }

    /**
     * Convenience: serialise a single {@link JsonTRow} to a JSON object string.
     *
     * @param row    the row to serialise
     * @param schema the schema that maps positions to JSON key names
     * @return a JSON object string
     * @throws io.github.datakore.jsont.error.JsonTError.Stringify on schema errors
     */
    public static String toJson(JsonTRow row, JsonTSchema schema) {
        return JsonWriter.withSchema(schema).build().writeRow(row);
    }
}
