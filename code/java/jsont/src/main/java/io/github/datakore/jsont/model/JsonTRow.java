package io.github.datakore.jsont.model;

import io.github.datakore.jsont.crypto.CryptoContext;
import io.github.datakore.jsont.crypto.CryptoError;

import java.util.List;
import java.util.Objects;

/**
 * An ordered, positional row of {@link JsonTValue}s — the runtime data unit.
 *
 * <p>Values are stored by position; field names come from the associated schema.
 * The row index tracks the row's position within a dataset (0-based).
 *
 * <pre>{@code
 *   JsonTRow row = JsonTRow.of(
 *       JsonTValue.i64(1L),
 *       JsonTValue.text("Widget A"),
 *       JsonTValue.i32(10),
 *       JsonTValue.d64(9.99)
 *   );
 *
 *   row.size()     // 4
 *   row.get(1)     // Text("Widget A")
 *   row.index()    // 0L
 * }</pre>
 */
public record JsonTRow(long index, List<JsonTValue> values) {

    public JsonTRow {
        Objects.requireNonNull(values, "values must not be null");
        values = List.copyOf(values);
    }

    // ─── Factory methods ──────────────────────────────────────────────────────

    /** Creates a row at index 0 from varargs. */
    public static JsonTRow of(JsonTValue... values) {
        return new JsonTRow(0L, List.of(values));
    }

    /** Creates a row at the specified index from varargs. */
    public static JsonTRow at(long index, JsonTValue... values) {
        return new JsonTRow(index, List.of(values));
    }

    /** Creates a row at the specified index from a list. */
    public static JsonTRow at(long index, List<JsonTValue> values) {
        return new JsonTRow(index, values);
    }

    // ─── Accessors ────────────────────────────────────────────────────────────

    /** Returns the number of values in this row. */
    public int size() {
        return values.size();
    }

    /**
     * Returns the value at position {@code i} (0-based).
     *
     * @throws IndexOutOfBoundsException if {@code i} is out of range
     */
    public JsonTValue get(int i) {
        return values.get(i);
    }

    /** Returns {@code true} if this row has no values. */
    public boolean isEmpty() {
        return values.isEmpty();
    }

    // ─── Transformation helpers ───────────────────────────────────────────────

    /** Returns a copy of this row with a different index (values unchanged). */
    public JsonTRow withIndex(long newIndex) {
        return new JsonTRow(newIndex, values);
    }

    /** Returns a copy of this row with different values (index unchanged). */
    public JsonTRow withValues(List<JsonTValue> newValues) {
        return new JsonTRow(index, newValues);
    }

    // ── On-demand decrypt ─────────────────────────────────────────────────────

    /**
     * Decrypt the value at position {@code index} and return the plaintext as a UTF-8 string.
     *
     * <p>Uses the stream-level {@link CryptoContext} (from the EncryptHeader) to unwrap
     * the DEK and decrypt.
     *
     * @param index     the 0-based position of the field in this row
     * @param fieldName the field name (used in error messages)
     * @param ctx       the {@link CryptoContext} produced from the stream's EncryptHeader
     * @return the decrypted string, or {@link java.util.Optional#empty()} if the value
     *         at {@code index} is not encrypted (already plaintext, null, etc.)
     * @throws IndexOutOfBoundsException if {@code index} is out of range
     * @throws CryptoError               if crypto, digest verification, or UTF-8 decoding fails
     */
    public java.util.Optional<String> decryptField(
            int index, String fieldName, CryptoContext ctx)
            throws CryptoError {
        return values.get(index).decryptStr(fieldName, ctx);
    }

    @Override
    public String toString() {
        var sb = new StringBuilder("{");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(values.get(i));
        }
        sb.append("}");
        return sb.toString();
    }
}
