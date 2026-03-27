package io.github.datakore.jsont;

import io.github.datakore.jsont.builder.JsonTSchemaBuilder;
import io.github.datakore.jsont.model.JsonTNamespace;
import io.github.datakore.jsont.model.JsonTRow;
import io.github.datakore.jsont.model.JsonTSchema;
import io.github.datakore.jsont.stringify.JsonTStringifier;
import io.github.datakore.jsont.stringify.RowWriter;
import io.github.datakore.jsont.stringify.StringifyOptions;

import java.io.IOException;
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
}
