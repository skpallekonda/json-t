package io.github.datakore.jsont.stringify;

import io.github.datakore.jsont.builder.SchemaResolver;
import io.github.datakore.jsont.crypto.CryptoContext;
import io.github.datakore.jsont.crypto.CryptoError;
import io.github.datakore.jsont.crypto.EncryptedField;
import io.github.datakore.jsont.internal.crypto.EncryptHeaderParser;
import io.github.datakore.jsont.internal.crypto.FieldPayload;
import io.github.datakore.jsont.model.JsonTField;
import io.github.datakore.jsont.model.JsonTNumber;
import io.github.datakore.jsont.model.JsonTRow;
import io.github.datakore.jsont.model.JsonTSchema;
import io.github.datakore.jsont.model.JsonTString;
import io.github.datakore.jsont.model.JsonTValue;

import java.io.IOException;
import java.io.Writer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

/**
 * Direct-write API for serialising {@link JsonTRow} data to any {@link Writer}.
 *
 * <p>Unlike {@link JsonTStringifier}, no intermediate {@code String} is allocated per
 * row — characters are written directly to the underlying writer.
 *
 * <h2>Schema-bound writer (encrypted or plain)</h2>
 * <p>Construct with a {@link JsonTSchema}. If the schema hierarchy contains sensitive
 * fields, a {@link CryptoContext} is required — the constructor throws
 * {@link IllegalArgumentException} if one is missing. Encryption is handled
 * transparently; callers do not need separate code paths for plain vs encrypted schemas.
 * <pre>{@code
 *   CryptoConfig cfg = new PublicKeyCryptoConfig("JSONT_PUB", "JSONT_PRIV");
 *   try (CryptoContext ctx = CryptoContext.forEncrypt(AlgoVersion.AES_GCM, KekMode.PUBLIC_KEY, cfg)) {
 *       new RowWriter(schema, ctx, registry).writeStream(rows, out);
 *   }
 * }</pre>
 *
 * <h2>Schema-free (plain only)</h2>
 * <p>Use the static helpers {@link #writeRow(JsonTRow, Writer)} and
 * {@link #writeRows(Iterable, Writer)} directly — no instance needed.
 */
public final class RowWriter implements AutoCloseable {

    private final List<JsonTField> fields;
    private final CryptoContext context;    // null when schema has no sensitive fields
    private final SchemaResolver registry;  // null if no nested object support needed

    /**
     * Schema-bound writer without crypto or registry support.
     * Throws if the schema contains sensitive fields (a {@link CryptoContext} is then required).
     */
    public RowWriter(JsonTSchema schema) {
        this(schema, null, null);
    }

    /**
     * Schema-bound writer with a {@link CryptoContext} but no nested-schema registry.
     * Throws if the schema has sensitive fields and {@code context} is {@code null}.
     */
    public RowWriter(JsonTSchema schema, CryptoContext context) {
        this(schema, context, null);
    }

    /**
     * Schema-bound writer with full control over crypto and nested-schema resolution.
     *
     * <p>If the schema hierarchy (including nested schemas reachable via {@code registry})
     * contains sensitive fields, {@code context} must not be {@code null}.
     *
     * @param schema   data schema; determines field order and sensitivity
     * @param context  crypto context for encryption; {@code null} only if no sensitive fields exist
     * @param registry resolver for nested object schemas; may be {@code null}
     * @throws IllegalArgumentException if sensitive fields exist but {@code context} is {@code null}
     */
    public RowWriter(JsonTSchema schema, CryptoContext context, SchemaResolver registry) {
        Objects.requireNonNull(schema, "schema must not be null");
        this.fields   = schema.fields();
        this.context  = context;
        this.registry = registry;
        if (hasSensitiveField(this.fields, registry) && context == null) {
            throw new IllegalArgumentException(
                "Schema '" + schema.name() + "' contains sensitive fields — supply a CryptoContext");
        }
    }

    /** Closes this writer. Does not close the underlying {@link CryptoContext}. */
    @Override
    public void close() { /* context ownership stays with caller */ }

    // ── Schema-free static writers ────────────────────────────────────────────

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

    // ── Schema-bound instance methods ─────────────────────────────────────────

    /**
     * Write a complete stream using this writer's schema.
     *
     * <p>If sensitive fields exist, emits the {@code EncryptHeader} row first,
     * then each data row separated by {@code ,\n}, with sensitive fields encrypted.
     * Plain schemas write rows directly without a header — both paths use the same call.
     *
     * @param rows the data rows to write
     * @param w    the destination writer
     * @throws IOException  on write failure
     * @throws CryptoError  if field encryption fails
     */
    public void writeStream(Iterable<JsonTRow> rows, Writer w) throws IOException, CryptoError {
        if (context != null && hasSensitiveField(fields, registry)) {
            RowWriter.writeRow(EncryptHeaderParser.buildRow(context), w);
            for (JsonTRow row : rows) {
                w.write(",\n");
                writeOneRow(row, w);
            }
        } else {
            writeRows(rows, w);
        }
    }

    private void writeOneRow(JsonTRow row, Writer w) throws IOException, CryptoError {
        w.write('{');
        writeEncryptedValues(row.values(), fields, w);
        w.write('}');
    }

    /** Returns {@code true} if any field — or any field in a recursively referenced schema — is sensitive. */
    static boolean hasSensitiveField(List<JsonTField> fields, SchemaResolver registry) {
        for (JsonTField f : fields) {
            if (f.sensitive()) return true;
            if (f.kind().isObject() && registry != null) {
                JsonTSchema nested = registry.resolve(f.objectRef()).orElse(null);
                if (nested != null && hasSensitiveField(nested.fields(), registry)) return true;
            }
        }
        return false;
    }

    private void writeEncryptedValues(List<JsonTValue> values, List<JsonTField> fields, Writer w)
            throws IOException, CryptoError {
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) w.write(',');
            JsonTValue v     = values.get(i);
            JsonTField field = fields.get(i);
            if (context != null && field.sensitive()) {
                byte[] payload;
                if (v instanceof JsonTValue.Encrypted enc) {
                    payload = enc.envelope();
                } else {
                    byte[] plaintext = valueToText(v).getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    byte[] digest    = sha256(plaintext);
                    EncryptedField ef = context.encryptField(plaintext);
                    payload = FieldPayload.assemble(ef.iv(), digest, ef.encContent());
                }
                writeQuotedString(Base64.getEncoder().encodeToString(payload), w);
            } else if (field.kind().isObject() && v instanceof JsonTValue.Array arr && registry != null) {
                JsonTSchema nested = registry.resolve(field.objectRef()).orElse(null);
                if (nested != null) {
                    w.write('{');
                    writeEncryptedValues(arr.elements(), nested.fields(), w);
                    w.write('}');
                } else {
                    writeValue(v, w);
                }
            } else {
                writeValue(v, w);
            }
        }
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
