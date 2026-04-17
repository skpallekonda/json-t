package io.github.datakore.jsont.stringify;

import io.github.datakore.jsont.crypto.CryptoConfig;
import io.github.datakore.jsont.crypto.CryptoContext;
import io.github.datakore.jsont.crypto.CryptoError;
import io.github.datakore.jsont.internal.crypto.EncryptHeaderParser;
import io.github.datakore.jsont.internal.crypto.FieldPayload;
import io.github.datakore.jsont.model.JsonTField;
import io.github.datakore.jsont.model.JsonTNumber;
import io.github.datakore.jsont.model.JsonTRow;
import io.github.datakore.jsont.model.JsonTString;
import io.github.datakore.jsont.model.JsonTValue;

import java.io.IOException;
import java.io.Writer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Iterator;
import java.util.List;

/**
 * Direct-write API for serialising {@link JsonTRow} data to any {@link Writer}.
 *
 * <p>Unlike {@link JsonTStringifier}, no intermediate {@code String} is allocated per
 * row — characters are written directly to the underlying writer.
 *
 * <h2>Encrypted stream (Step 5)</h2>
 * <p>Use {@link #writeEncryptedStream} to write a complete encrypted stream:
 * <pre>{@code
 *   CryptoConfig crypto = new EnvCryptoConfig("JSONT_PUB", "JSONT_PRIV");
 *   try (BufferedWriter bw = Files.newBufferedWriter(path)) {
 *       RowWriter.writeEncryptedStream(rows, schema.fields(), crypto,
 *                                      CryptoContext.VERSION_AES_PUBKEY, bw);
 *   }
 * }</pre>
 */
public final class RowWriter {

    private static final int DEK_LEN = 32; // AES-256 key size in bytes

    private RowWriter() {}

    // ── Schema-free writers ───────────────────────────────────────────────────

    /**
     * Writes one data row in compact JsonT format: {@code {v1,v2,...}}.
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
     */
    public static void writeRows(JsonTRow[] rows, Writer w) throws IOException {
        for (int i = 0; i < rows.length; i++) {
            if (i > 0) w.write(",\n");
            writeRow(rows[i], w);
        }
    }

    // ── Encrypted stream writer (Step 5) ─────────────────────────────────────

    /**
     * Write a complete encrypted JsonT stream:
     *
     * <ol>
     *   <li>Generate a random 256-bit DEK.</li>
     *   <li>Wrap the DEK via {@code crypto.wrapDek(version, dek)} and write the
     *       {@code EncryptHeader} row.</li>
     *   <li>For each data row, write it with schema-aware encryption using the
     *       shared DEK. Each sensitive field gets a fresh IV; the per-field payload
     *       carries {@code [len_iv][len_digest][iv][SHA-256(plaintext)][enc_content]}.</li>
     *   <li>Zero the DEK before returning.</li>
     * </ol>
     *
     * <p>The header row and data rows are separated by {@code ,\n}.
     *
     * @param rows    the data rows to write
     * @param fields  schema field descriptors (same order as row values)
     * @param crypto  the crypto implementation
     * @param version the {@code EncryptHeader} version field (e.g. {@link CryptoContext#VERSION_AES_PUBKEY})
     * @param w       the destination writer
     * @throws IOException  on write failure
     * @throws CryptoError  if DEK wrapping or field encryption fails
     */
    public static void writeEncryptedStream(
            Iterable<JsonTRow> rows,
            List<JsonTField> fields,
            CryptoConfig crypto,
            int version,
            Writer w) throws IOException, CryptoError {

        byte[] dek = new byte[DEK_LEN];
        new SecureRandom().nextBytes(dek);
        try {
            byte[] encDek = crypto.wrapDek(version, dek);
            CryptoContext ctx = new CryptoContext(version, encDek);
            writeRow(EncryptHeaderParser.buildRow(ctx), w);

            for (JsonTRow row : rows) {
                w.write(",\n");
                writeRowWithDek(row, fields, dek, crypto, w);
            }
        } finally {
            Arrays.fill(dek, (byte) 0);
        }
    }

    /**
     * Write one data row using a pre-unwrapped DEK for field encryption.
     *
     * <ul>
     *   <li>Sensitive field with a non-{@link io.github.datakore.jsont.model.JsonTValue.Encrypted Encrypted}
     *       value — encrypt via {@code crypto.encryptField}, compute SHA-256(plaintext),
     *       assemble the per-field payload, write as plain base64.</li>
     *   <li>Sensitive field already holding an {@code Encrypted} value — the stored
     *       payload bytes are re-encoded as plain base64 (no crypto call).</li>
     *   <li>Non-sensitive fields — written normally.</li>
     * </ul>
     *
     * @param row    the row to serialise
     * @param fields schema field descriptors
     * @param dek    the raw plaintext DEK
     * @param crypto the crypto implementation (for {@code encryptField})
     * @param w      the destination writer
     * @throws IOException  on write failure
     * @throws CryptoError  if field encryption fails
     */
    public static void writeRowWithDek(
            JsonTRow row,
            List<JsonTField> fields,
            byte[] dek,
            CryptoConfig crypto,
            Writer w) throws IOException, CryptoError {
        w.write('{');
        List<JsonTValue> values = row.values();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) w.write(',');
            JsonTValue v     = values.get(i);
            JsonTField field = fields.get(i);
            if (field.sensitive()) {
                byte[] payload;
                if (v instanceof JsonTValue.Encrypted enc) {
                    // Already a fully-assembled payload — re-encode as-is.
                    payload = enc.envelope();
                } else {
                    // Plaintext — encrypt and assemble payload.
                    byte[] plaintext = valueToText(v).getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    byte[] digest    = sha256(plaintext);
                    CryptoConfig.EncryptedField ef = crypto.encryptField(dek, plaintext);
                    payload = FieldPayload.assemble(ef.iv(), digest, ef.encContent());
                }
                writeQuotedString(Base64.getEncoder().encodeToString(payload), w);
            } else {
                writeValue(v, w);
            }
        }
        w.write('}');
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static byte[] sha256(byte[] data) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(data);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /** Produce the plain-text wire representation of a non-encrypted value. */
    static String valueToText(JsonTValue v) {
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
