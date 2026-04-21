package io.github.datakore.jsont.internal.crypto;

import io.github.datakore.jsont.crypto.CryptoError;

import java.util.Arrays;

/**
 * Per-field binary payload framing for encrypted JsonT streams.
 *
 * <h2>Binary layout (big-endian)</h2>
 * <pre>
 *   [2 bytes : len_iv  (u16) ]  — byte count of IV
 *   [4 bytes : len_digest (u32)] — BIT count of digest (256 for SHA-256)
 *   [len_iv bytes  : iv         ]  — unique nonce for this field
 *   [len_digest/8 bytes: digest  ]  — SHA-256 hash of original plaintext
 *   [remaining     : enc_content ]  — ciphertext + authentication tag
 * </pre>
 *
 * <p>The {@code enc_content} length is inferred:
 * {@code total_bytes − 2 − 4 − len_iv − (len_digest / 8)}.
 */
public final class FieldPayload {

    private FieldPayload() {}

    /**
     * Assemble the per-field binary payload from its components.
     *
     * @param iv         the unique nonce used for this field
     * @param digest     the SHA-256 hash of the original plaintext (32 bytes)
     * @param encContent the ciphertext + authentication tag
     * @return the assembled payload bytes
     */
    public static byte[] assemble(byte[] iv, byte[] digest, byte[] encContent) {
        int lenIv          = iv.length;
        int lenDigestBits  = digest.length * 8;
        byte[] out = new byte[2 + 4 + lenIv + digest.length + encContent.length];
        int pos = 0;
        out[pos++] = (byte) (lenIv >>> 8);
        out[pos++] = (byte)  lenIv;
        out[pos++] = (byte) (lenDigestBits >>> 24);
        out[pos++] = (byte) (lenDigestBits >>> 16);
        out[pos++] = (byte) (lenDigestBits >>>  8);
        out[pos++] = (byte)  lenDigestBits;
        System.arraycopy(iv,         0, out, pos, lenIv);          pos += lenIv;
        System.arraycopy(digest,     0, out, pos, digest.length);   pos += digest.length;
        System.arraycopy(encContent, 0, out, pos, encContent.length);
        return out;
    }

    /**
     * Parse a per-field binary payload into its components.
     *
     * @param payload   the raw payload bytes
     * @param fieldName the field name (used in error messages)
     * @return a {@link Parsed} record holding the IV, digest, and enc_content slices
     * @throws CryptoError.MalformedPayload if the payload is too short or inconsistent
     */
    public static Parsed parse(byte[] payload, String fieldName) throws CryptoError {
        if (payload.length < 6) {
            throw new CryptoError.MalformedPayload(fieldName,
                    "payload too short: " + payload.length + " bytes (need at least 6)");
        }
        int lenIv         = ((payload[0] & 0xFF) << 8) | (payload[1] & 0xFF);
        int lenDigestBits = ((payload[2] & 0xFF) << 24)
                          | ((payload[3] & 0xFF) << 16)
                          | ((payload[4] & 0xFF) <<  8)
                          |  (payload[5] & 0xFF);
        int lenDigest  = lenDigestBits / 8;
        int ivStart    = 6;
        int ivEnd      = ivStart + lenIv;
        int digestEnd  = ivEnd + lenDigest;

        if (payload.length < digestEnd) {
            throw new CryptoError.MalformedPayload(fieldName,
                    "payload too short for iv+digest: need " + digestEnd
                    + " bytes, have " + payload.length);
        }

        byte[] iv         = Arrays.copyOfRange(payload, ivStart,   ivEnd);
        byte[] digest     = Arrays.copyOfRange(payload, ivEnd,     digestEnd);
        byte[] encContent = Arrays.copyOfRange(payload, digestEnd, payload.length);
        return new Parsed(iv, digest, encContent);
    }

    /**
     * The components extracted from a parsed per-field payload.
     *
     * @param iv         the per-field IV
     * @param digest     the stored SHA-256 digest of the original plaintext
     * @param encContent the ciphertext + authentication tag
     */
    public record Parsed(byte[] iv, byte[] digest, byte[] encContent) {}
}
