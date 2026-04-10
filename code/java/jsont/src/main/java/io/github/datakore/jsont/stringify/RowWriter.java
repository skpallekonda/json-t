package io.github.datakore.jsont.stringify;

import io.github.datakore.jsont.crypto.CryptoConfig;
import io.github.datakore.jsont.crypto.CryptoError;
import io.github.datakore.jsont.model.JsonTField;
import io.github.datakore.jsont.model.JsonTNumber;
import io.github.datakore.jsont.model.JsonTRow;
import io.github.datakore.jsont.model.JsonTString;
import io.github.datakore.jsont.model.JsonTValue;

import java.io.IOException;
import java.io.Writer;
import java.util.Base64;
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

    /**
     * Schema-aware write: sensitive fields are encrypted before writing.
     *
     * <ul>
     *   <li>Sensitive field with a non-{@code Encrypted} value → plaintext bytes
     *       are passed to {@code crypto.encrypt} and written as plain base64
     *       (no prefix — the {@code ~} schema marker is the authority).</li>
     *   <li>Sensitive field already holding an {@code Encrypted} value → ciphertext
     *       bytes are re-encoded as plain base64 (no crypto call).</li>
     *   <li>Non-sensitive fields → written normally.</li>
     * </ul>
     *
     * @param row    the row to serialise
     * @param fields schema field descriptors in the same order as row values
     * @param crypto the crypto implementation to use for encryption
     * @param w      the destination writer
     * @throws IOException  on write failure
     * @throws CryptoError  if encryption fails for any field
     */
    public static void writeRow(JsonTRow row, List<JsonTField> fields,
                                CryptoConfig crypto, Writer w)
            throws IOException, CryptoError {
        w.write('{');
        List<JsonTValue> values = row.values();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) w.write(',');
            JsonTValue v     = values.get(i);
            JsonTField field = fields.get(i);
            if (field.sensitive()) {
                byte[] ciphertext;
                if (v instanceof JsonTValue.Encrypted enc) {
                    // Already ciphertext — re-encode directly.
                    ciphertext = enc.envelope();
                } else {
                    // Plaintext — stringify then encrypt.
                    byte[] plaintext = valueToText(v).getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    ciphertext = crypto.encrypt(field.name(), plaintext);
                }
                writeQuotedString(Base64.getEncoder().encodeToString(ciphertext), w);
            } else {
                writeValue(v, w);
            }
        }
        w.write('}');
    }

    /** Produce the plain-text wire representation of a non-encrypted value. */
    private static String valueToText(JsonTValue v) {
        if (v instanceof JsonTValue.Null)        return "null";
        if (v instanceof JsonTValue.Unspecified) return "_";
        if (v instanceof JsonTValue.Bool   b)    return Boolean.toString(b.value());
        if (v instanceof JsonTString       s)    return s.value();
        if (v instanceof JsonTValue.Enum   e)    return e.value();
        if (v instanceof JsonTNumber.I16   n)    return String.valueOf(n.value());
        if (v instanceof JsonTNumber.I32   n)    return String.valueOf(n.value());
        if (v instanceof JsonTNumber.I64   n)    return String.valueOf(n.value());
        if (v instanceof JsonTNumber.U16   n)    return String.valueOf(n.value());
        if (v instanceof JsonTNumber.U32   n)    return String.valueOf(n.value());
        if (v instanceof JsonTNumber.U64   n)    return Long.toUnsignedString(n.value());
        if (v instanceof JsonTNumber.D32   n)    return String.valueOf(n.value());
        if (v instanceof JsonTNumber.D64   n)    return String.valueOf(n.value());
        if (v instanceof JsonTNumber.D128  n)    return n.value().toPlainString();
        if (v instanceof JsonTNumber.Date  n)    return String.valueOf(n.value());
        if (v instanceof JsonTNumber.Time  n)    return String.valueOf(n.value());
        if (v instanceof JsonTNumber.DateTime  n)  return String.valueOf(n.value());
        if (v instanceof JsonTNumber.Timestamp n)  return String.valueOf(n.value());
        return "<complex>";
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private static void writeValue(JsonTValue v, Writer w) throws IOException {
        if (v instanceof JsonTValue.Null)        { w.write("null"); return; }
        if (v instanceof JsonTValue.Bool  b)     { w.write(Boolean.toString(b.value())); return; }

        if (v instanceof JsonTNumber.I16 n)   { w.write(String.valueOf(n.value())); return; }
        if (v instanceof JsonTNumber.I32 n)   { w.write(String.valueOf(n.value())); return; }
        if (v instanceof JsonTNumber.I64 n)   { w.write(String.valueOf(n.value())); return; }
        if (v instanceof JsonTNumber.U16 n)   { w.write(String.valueOf(n.value())); return; }
        if (v instanceof JsonTNumber.U32 n)   { w.write(String.valueOf(n.value())); return; }
        if (v instanceof JsonTNumber.U64 n)   { w.write(Long.toUnsignedString(n.value())); return; }
        if (v instanceof JsonTNumber.D32 n)   { w.write(String.valueOf(n.value())); return; }
        if (v instanceof JsonTNumber.D64 n)   { w.write(String.valueOf(n.value())); return; }
        if (v instanceof JsonTNumber.D128 n)  { w.write(n.value().toPlainString()); return; }

        if (v instanceof JsonTNumber.Date n)      { w.write(String.valueOf(n.value())); return; }
        if (v instanceof JsonTNumber.Time n)      { w.write(String.valueOf(n.value())); return; }
        if (v instanceof JsonTNumber.DateTime n)  { w.write(String.valueOf(n.value())); return; }
        if (v instanceof JsonTNumber.Timestamp n) { w.write(String.valueOf(n.value())); return; }
        if (v instanceof JsonTString  s) { writeQuotedString(s.value(), w); return; }
        if (v instanceof JsonTValue.Enum      e) { w.write(e.value()); return; }
        if (v instanceof JsonTValue.Unspecified) { w.write('_'); return; }
        if (v instanceof JsonTValue.Array     a) { writeArray(a.elements(), w); return; }
        if (v instanceof JsonTValue.Encrypted e) {
            writeQuotedString(Base64.getEncoder().encodeToString(e.envelope()), w);
            return;
        }
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
