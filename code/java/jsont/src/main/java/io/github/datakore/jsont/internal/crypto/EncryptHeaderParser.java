package io.github.datakore.jsont.internal.crypto;

import io.github.datakore.jsont.crypto.CryptoContext;
import io.github.datakore.jsont.model.JsonTNumber;
import io.github.datakore.jsont.model.JsonTRow;
import io.github.datakore.jsont.model.JsonTString;
import io.github.datakore.jsont.model.JsonTValue;

import java.util.Base64;
import java.util.Optional;

/**
 * Parses and builds the {@code EncryptHeader} row that begins an encrypted
 * JsonT stream.
 *
 * <h2>Wire shape</h2>
 * <pre>
 *   {"ENCRYPTED_HEADER", &lt;version:u16&gt;, &lt;length:u32&gt;, "&lt;base64:enc_dek&gt;"}
 * </pre>
 *
 * <p>The row scanner emits all numbers as {@link JsonTNumber.D64} (no schema
 * context), so both typed integer variants ({@code U16}/{@code U32}) and
 * {@code D64} are accepted when extracting version and length.
 */
public final class EncryptHeaderParser {

    private EncryptHeaderParser() {}

    /**
     * Raw wire data extracted from an {@code EncryptHeader} row.
     * The caller supplies a {@link io.github.datakore.jsont.crypto.CryptoConfig}
     * and calls {@link CryptoContext#forDecrypt} to complete context construction.
     *
     * @param version the packed version field (u16)
     * @param encDek  the wrapped DEK bytes as read from the wire
     */
    public record ParsedHeader(int version, byte[] encDek) {
        public ParsedHeader {
            encDek = encDek.clone();
        }
    }

    /**
     * If {@code row} is a valid {@code EncryptHeader} row, returns the parsed
     * header wire data; otherwise returns {@link Optional#empty()}.
     *
     * <p>The caller is responsible for constructing a {@link CryptoContext} via
     * {@link CryptoContext#forDecrypt(int, byte[], io.github.datakore.jsont.crypto.CryptoConfig)}.
     *
     * @param row the candidate row (typically the first row of a stream)
     * @return the parsed header data if the row matches, or empty
     */
    public static Optional<ParsedHeader> tryParse(JsonTRow row) {
        if (row.size() != 4) return Optional.empty();

        // Field 0: type constant "ENCRYPTED_HEADER"
        if (!(row.get(0) instanceof JsonTString.Plain p
                && "ENCRYPTED_HEADER".equals(p.value()))) {
            return Optional.empty();
        }

        // Field 1: version (U16 from builder, or D64 from scanner)
        int version = extractU16(row.get(1));
        if (version < 0) return Optional.empty();

        // Field 2: enc_dek byte count (U32 from builder, or D64 from scanner)
        long expectedLen = extractU32(row.get(2));
        if (expectedLen < 0) return Optional.empty();

        // Field 3: enc_dek as plain base64 string
        if (!(row.get(3) instanceof JsonTString.Plain b64Val)) return Optional.empty();
        byte[] encDek;
        try {
            encDek = Base64.getDecoder().decode(b64Val.value());
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }

        if (encDek.length != expectedLen) return Optional.empty();

        return Optional.of(new ParsedHeader(version, encDek));
    }

    /**
     * Builds the {@code EncryptHeader} row for writing at the top of an
     * encrypted stream.
     *
     * <p>Uses typed integer values ({@code U16}/{@code U32}) so the writer
     * emits integer literals rather than floats.
     *
     * @param ctx the {@link CryptoContext} to encode
     * @return a {@link JsonTRow} representing the header
     */
    public static JsonTRow buildRow(CryptoContext ctx) {
        byte[] encDek = ctx.encDek();
        String b64 = Base64.getEncoder().encodeToString(encDek);
        return JsonTRow.of(
                new JsonTString.Plain("ENCRYPTED_HEADER"),
                new JsonTNumber.U16(ctx.version()),
                new JsonTNumber.U32((long) encDek.length),
                new JsonTString.Plain(b64)
        );
    }

    // ── Numeric extraction helpers ────────────────────────────────────────────

    /**
     * Extract a u16 value from a {@link JsonTValue} that may be a {@code U16}
     * (from a builder) or {@code D64} (from the row scanner).
     *
     * @return the value as an {@code int}, or {@code -1} if the value is not
     *         a valid u16
     */
    private static int extractU16(JsonTValue v) {
        if (v instanceof JsonTNumber.U16 u) return u.value();
        if (v instanceof JsonTNumber.D64 d) {
            double f = d.value();
            if (f % 1.0 == 0.0 && f >= 0 && f <= 0xFFFF) return (int) f;
        }
        return -1;
    }

    /**
     * Extract a u32 value from a {@link JsonTValue} that may be a {@code U32}
     * (from a builder) or {@code D64} (from the row scanner).
     *
     * @return the value as a {@code long}, or {@code -1L} if the value is not
     *         a valid u32
     */
    private static long extractU32(JsonTValue v) {
        if (v instanceof JsonTNumber.U32 u) return u.value();
        if (v instanceof JsonTNumber.D64 d) {
            double f = d.value();
            if (f % 1.0 == 0.0 && f >= 0 && f <= 0xFFFFFFFFL) return (long) f;
        }
        return -1L;
    }
}
