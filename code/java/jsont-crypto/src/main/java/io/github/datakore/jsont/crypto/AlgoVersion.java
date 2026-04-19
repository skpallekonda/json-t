package io.github.datakore.jsont.crypto;

/**
 * Cipher algorithm version encoded in bits 6..3 of the {@code version} field.
 *
 * <pre>
 *   AES_GCM            = 1  (AES-256-GCM, default)
 *   CHACHA20_POLY1305  = 2  (ChaCha20-Poly1305, software-optimised)
 *   ASCON              = 3  (ASCON-128a, NIST SP 800-232, constrained devices)
 * </pre>
 */
public enum AlgoVersion {

    AES_GCM(1),
    CHACHA20_POLY1305(2),
    ASCON(3);

    /** Raw 4-bit value stored in bits 6..3 of the {@code version} field. */
    public final int bits;

    AlgoVersion(int bits) {
        this.bits = bits;
    }

    /**
     * Resolves the enum constant for the given raw nibble, or {@code null} if unknown.
     *
     * @param bits the 4-bit algo_ver value extracted from the version field
     * @return the matching constant, or {@code null} for reserved/unknown values
     */
    public static AlgoVersion fromBits(int bits) {
        for (AlgoVersion v : values()) {
            if (v.bits == bits) return v;
        }
        return null;
    }
}
