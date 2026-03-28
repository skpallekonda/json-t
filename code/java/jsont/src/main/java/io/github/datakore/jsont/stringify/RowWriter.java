package io.github.datakore.jsont.stringify;

import io.github.datakore.jsont.model.JsonTRow;
import io.github.datakore.jsont.model.JsonTValue;

import java.io.IOException;
import java.io.Writer;
import java.util.Iterator;
import java.util.List;

/**
 * Direct-write API for serialising {@link JsonTRow} data to any {@link Writer}
 * (e.g. {@link java.io.StringWriter}, {@link java.io.BufferedWriter}).
 *
 * <p>Unlike {@link JsonTStringifier}, no intermediate {@code String} is allocated per
 * row — characters are written directly to the underlying writer, giving much higher
 * throughput for large datasets.
 *
 * <pre>{@code
 *   // StringWriter (testing / small data)
 *   StringWriter sw = new StringWriter();
 *   RowWriter.writeRow(row, sw);
 *
 *   // BufferedWriter backed by FileWriter (large data)
 *   try (BufferedWriter bw = new BufferedWriter(new FileWriter("out.jsont"))) {
 *       RowWriter.writeRows(rows, bw);
 *   }
 * }</pre>
 */
public final class RowWriter {

    private RowWriter() {}

    /**
     * Writes one data row in compact JsonT format: {@code {v1,v2,...}}.
     *
     * @param row the row to serialise
     * @param w   the destination writer (not closed by this method)
     * @throws IOException on write failure
     */
    public static void writeRow(JsonTRow row, Writer w) throws IOException {
        w.write('{');
        List<JsonTValue> values = row.values();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) w.write(',');
            writeValue(values.get(i), w);
        }
        w.write('}');
    }

    /**
     * Writes all rows from an {@link Iterable}, separated by {@code ,\n}.
     * No trailing newline is written after the last row.
     *
     * @param rows the rows to serialise
     * @param w    the destination writer (not closed by this method)
     * @throws IOException on write failure
     */
    public static void writeRows(Iterable<JsonTRow> rows, Writer w) throws IOException {
        Iterator<JsonTRow> it = rows.iterator();
        boolean first = true;
        while (it.hasNext()) {
            if (!first) w.write(",\n");
            first = false;
            writeRow(it.next(), w);
        }
    }

    /**
     * Writes all rows from an array, separated by {@code ,\n}.
     *
     * @param rows the rows to serialise
     * @param w    the destination writer (not closed by this method)
     * @throws IOException on write failure
     */
    public static void writeRows(JsonTRow[] rows, Writer w) throws IOException {
        for (int i = 0; i < rows.length; i++) {
            if (i > 0) w.write(",\n");
            writeRow(rows[i], w);
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private static void writeValue(JsonTValue v, Writer w) throws IOException {
        if (v instanceof JsonTValue.Null)        { w.write("null"); return; }
        if (v instanceof JsonTValue.Bool  b)     { w.write(Boolean.toString(b.value())); return; }
        if (v instanceof JsonTValue.I16   n)     { w.write(Short.toString(n.value())); return; }
        if (v instanceof JsonTValue.I32   n)     { w.write(Integer.toString(n.value())); return; }
        if (v instanceof JsonTValue.I64   n)     { w.write(Long.toString(n.value())); return; }
        if (v instanceof JsonTValue.U16   n)     { w.write(Integer.toString(n.value())); return; }
        if (v instanceof JsonTValue.U32   n)     { w.write(Long.toString(n.value())); return; }
        if (v instanceof JsonTValue.U64   n)     { w.write(Long.toUnsignedString(n.value())); return; }
        if (v instanceof JsonTValue.D32   n)     { w.write(Float.toString(n.value())); return; }
        if (v instanceof JsonTValue.D64   n)     { w.write(Double.toString(n.value())); return; }
        if (v instanceof JsonTValue.D128  n)     { w.write(n.value().toPlainString()); return; }
        if (v instanceof JsonTValue.Text      t) { writeQuotedString(t.value(), w); return; }
        if (v instanceof JsonTValue.Nstr      t) { writeQuotedString(t.value(), w); return; }
        if (v instanceof JsonTValue.Uuid      t) { writeQuotedString(t.value(), w); return; }
        if (v instanceof JsonTValue.Uri       t) { writeQuotedString(t.value(), w); return; }
        if (v instanceof JsonTValue.Email     t) { writeQuotedString(t.value(), w); return; }
        if (v instanceof JsonTValue.Hostname  t) { writeQuotedString(t.value(), w); return; }
        if (v instanceof JsonTValue.Ipv4      t) { writeQuotedString(t.value(), w); return; }
        if (v instanceof JsonTValue.Ipv6      t) { writeQuotedString(t.value(), w); return; }
        if (v instanceof JsonTValue.Date      t) { writeQuotedString(t.value(), w); return; }
        if (v instanceof JsonTValue.Time      t) { writeQuotedString(t.value(), w); return; }
        if (v instanceof JsonTValue.DateTime  t) { writeQuotedString(t.value(), w); return; }
        if (v instanceof JsonTValue.Timestamp t) { writeQuotedString(t.value(), w); return; }
        if (v instanceof JsonTValue.Tsz       t) { writeQuotedString(t.value(), w); return; }
        if (v instanceof JsonTValue.Inst      t) { writeQuotedString(t.value(), w); return; }
        if (v instanceof JsonTValue.Duration  t) { writeQuotedString(t.value(), w); return; }
        if (v instanceof JsonTValue.Base64    t) { writeQuotedString(t.value(), w); return; }
        if (v instanceof JsonTValue.Hex       t) { writeQuotedString(t.value(), w); return; }
        if (v instanceof JsonTValue.Oid       t) { writeQuotedString(t.value(), w); return; }
        if (v instanceof JsonTValue.Enum      e) { w.write(e.value()); return; }
        if (v instanceof JsonTValue.Unspecified) { w.write('_'); return; }
        if (v instanceof JsonTValue.Array     a) { writeArray(a.elements(), w); return; }
        throw new IllegalArgumentException("Unknown JsonTValue: " + v);
    }

    private static void writeQuotedString(String s, Writer w) throws IOException {
        w.write('"');
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '\\') { w.write('\\'); w.write('\\'); }
            else if (ch == '"') { w.write('\\'); w.write('"'); }
            else w.write(ch);
        }
        w.write('"');
    }

    private static void writeArray(List<JsonTValue> elements, Writer w) throws IOException {
        w.write('[');
        for (int i = 0; i < elements.size(); i++) {
            if (i > 0) w.write(", ");
            writeValue(elements.get(i), w);
        }
        w.write(']');
    }
}
