package io.github.datakore.jsont.json;

import io.github.datakore.jsont.error.JsonTError;
import io.github.datakore.jsont.model.*;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Iterator;
import java.util.List;

/**
 * Serialises {@link JsonTRow} values to standard JSON, guided by a {@link JsonTSchema}.
 *
 * <p>Field names come from the schema; values come from the row by position.
 *
 * <pre>{@code
 *   JsonWriter writer = JsonWriter.withSchema(schema)
 *       .mode(JsonOutputMode.NDJSON)
 *       .build();
 *
 *   try (Writer out = new FileWriter("orders.json")) {
 *       writer.writeRows(rows, out);
 *   }
 * }</pre>
 *
 * <h2>Schema requirement</h2>
 * <p>Only <em>straight</em> schemas are supported directly.
 */
public final class JsonWriter {

    private final JsonTSchema   schema;
    private final JsonOutputMode mode;
    private final boolean        pretty;

    JsonWriter(JsonTSchema schema, JsonOutputMode mode, boolean pretty) {
        this.schema = schema;
        this.mode   = mode;
        this.pretty = pretty;
    }

    // ── Static factory ────────────────────────────────────────────────────────

    /**
     * Create a builder for a writer bound to the given schema.
     *
     * @param schema the schema that maps field positions to JSON key names
     * @return a new {@link JsonWriterBuilder}
     */
    public static JsonWriterBuilder withSchema(JsonTSchema schema) {
        return new JsonWriterBuilder(schema);
    }

    // ── Writing ───────────────────────────────────────────────────────────────

    /**
     * Serialise a single row to a JSON object string.
     *
     * @param row the row to serialise
     * @return a JSON object string
     * @throws JsonTError.Stringify on schema or value errors
     */
    public String writeRow(JsonTRow row) {
        StringWriter sw = new StringWriter();
        try { writeRow(row, sw); } catch (IOException e) { throw new AssertionError(e); }
        return sw.toString();
    }

    /**
     * Write a single row as a JSON object directly to {@code w}.
     *
     * @param row the row to serialise
     * @param w   destination writer (not closed)
     * @throws IOException          on write failure
     * @throws JsonTError.Stringify on schema or value errors
     */
    public void writeRow(JsonTRow row, Writer w) throws IOException {
        List<JsonTField> fields = resolvedFields();
        writeJsonObject(row, fields, pretty, 0, w);
    }

    /**
     * Write multiple rows to {@code w}.
     * <ul>
     *   <li>NDJSON mode: rows separated by {@code \n}</li>
     *   <li>ARRAY mode: rows wrapped in {@code [...]}</li>
     * </ul>
     *
     * @param rows an iterable of rows
     * @param w    destination writer (not closed)
     * @throws IOException          on write failure
     * @throws JsonTError.Stringify on schema or value errors
     */
    public void writeRows(Iterable<JsonTRow> rows, Writer w) throws IOException {
        List<JsonTField> fields = resolvedFields();
        if (mode == JsonOutputMode.NDJSON) {
            writeNdjson(rows.iterator(), fields, w);
        } else {
            writeArray(rows.iterator(), fields, w);
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private List<JsonTField> resolvedFields() {
        if (!schema.isStraight()) {
            throw new JsonTError.Stringify(
                    "JsonWriter requires a straight schema; resolve derived schemas first");
        }
        return schema.fields();
    }

    private void writeNdjson(Iterator<JsonTRow> rows, List<JsonTField> fields, Writer w)
            throws IOException {
        boolean first = true;
        while (rows.hasNext()) {
            if (!first) w.write('\n');
            first = false;
            writeJsonObject(rows.next(), fields, pretty, 0, w);
        }
    }

    private void writeArray(Iterator<JsonTRow> rows, List<JsonTField> fields, Writer w)
            throws IOException {
        w.write('[');
        if (pretty) w.write('\n');
        boolean first = true;
        while (rows.hasNext()) {
            if (!first) {
                w.write(',');
                if (pretty) w.write('\n');
            }
            first = false;
            if (pretty) w.write("  ");
            writeJsonObject(rows.next(), fields, pretty, 1, w);
        }
        if (pretty) w.write('\n');
        w.write(']');
    }

    private static void writeJsonObject(JsonTRow row, List<JsonTField> fields,
                                        boolean pretty, int depth, Writer w) throws IOException {
        String innerIndent = pretty ? "  ".repeat(depth + 1) : "";
        String closeIndent = pretty ? "  ".repeat(depth)     : "";
        String kvSep       = pretty ? ": " : ":";
        String nl          = pretty ? "\n"  : "";
        String comma       = pretty ? ",\n" : ",";

        w.write('{');
        w.write(nl);

        int count = Math.min(fields.size(), row.values().size());
        for (int i = 0; i < count; i++) {
            if (i > 0) w.write(comma);
            w.write(innerIndent);
            writeJsonStringLit(fields.get(i).name(), w);
            w.write(kvSep);
            writeJsonValue(row.values().get(i), pretty, depth + 1, w);
        }

        w.write(nl);
        w.write(closeIndent);
        w.write('}');
    }

    private static void writeJsonValue(JsonTValue value, boolean pretty, int depth, Writer w)
            throws IOException {
        if (value instanceof JsonTValue.Null || value instanceof JsonTValue.Unspecified) {
            w.write("null");
        } else if (value instanceof JsonTValue.Bool b) {
            w.write(b.value() ? "true" : "false");
        } else if (value instanceof JsonTNumber n) {
            writeJsonNumber(n, w);
        } else if (value instanceof JsonTString s) {
            writeJsonStringLit(s.value(), w);
        } else if (value instanceof JsonTValue.Enum e) {
            writeJsonStringLit(e.value(), w);
        } else if (value instanceof JsonTValue.Array a) {
            writeJsonArray(a.elements(), pretty, depth, w);
        } else {
            // Fallback for any value not handled (e.g. nested Object without schema)
            w.write("null");
        }
    }

    private static void writeJsonNumber(JsonTNumber n, Writer w) throws IOException {
        if      (n instanceof JsonTNumber.I16 v)       w.write(String.valueOf(v.value()));
        else if (n instanceof JsonTNumber.I32 v)       w.write(String.valueOf(v.value()));
        else if (n instanceof JsonTNumber.I64 v)       w.write(String.valueOf(v.value()));
        else if (n instanceof JsonTNumber.U16 v)       w.write(String.valueOf(v.value()));
        else if (n instanceof JsonTNumber.U32 v)       w.write(String.valueOf(v.value()));
        else if (n instanceof JsonTNumber.U64 v)       w.write(Long.toUnsignedString(v.value()));
        else if (n instanceof JsonTNumber.D32 v)       w.write(String.valueOf(v.value()));
        else if (n instanceof JsonTNumber.D64 v)       w.write(String.valueOf(v.value()));
        else if (n instanceof JsonTNumber.D128 v)      w.write(v.value().toPlainString());
        else if (n instanceof JsonTNumber.Date v)      w.write(String.valueOf(v.value()));
        else if (n instanceof JsonTNumber.Time v)      w.write(String.valueOf(v.value()));
        else if (n instanceof JsonTNumber.DateTime v)  w.write(String.valueOf(v.value()));
        else if (n instanceof JsonTNumber.Timestamp v) w.write(String.valueOf(v.value()));
    }

    private static void writeJsonStringLit(String s, Writer w) throws IOException {
        w.write('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"'  -> w.write("\\\"");
                case '\\' -> w.write("\\\\");
                case '\n' -> w.write("\\n");
                case '\r' -> w.write("\\r");
                case '\t' -> w.write("\\t");
                default   -> {
                    if (c < 0x20) w.write(String.format("\\u%04x", (int) c));
                    else          w.write(c);
                }
            }
        }
        w.write('"');
    }

    private static void writeJsonArray(List<JsonTValue> items, boolean pretty, int depth, Writer w)
            throws IOException {
        w.write('[');
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) w.write(',');
            writeJsonValue(items.get(i), pretty, depth, w);
        }
        w.write(']');
    }
}
