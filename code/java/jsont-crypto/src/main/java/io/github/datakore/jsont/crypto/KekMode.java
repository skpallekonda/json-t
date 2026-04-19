package io.github.datakore.jsont.crypto;

/**
 * Key-encryption-key (KEK) derivation mode encoded in bit 0 of the {@code version} field.
 *
 * <pre>
 *   PUBLIC_KEY = 0  — DEK wrapped with receiver's RSA public certificate
 *   ECDH       = 1  — DEK wrapped with symmetric key derived via ECDH + HKDF-SHA256
 * </pre>
 */
public enum KekMode {

    PUBLIC_KEY(0),
    ECDH(1);

    /** Raw 1-bit value stored in bit 0 of the {@code version} field. */
    public final int bit;

    KekMode(int bit) {
        this.bit = bit;
    }

    /**
     * Resolves the enum constant for the given raw bit value.
     *
     * @param bit the kek_mode bit (0 or 1) extracted from the version field
     * @return {@link #PUBLIC_KEY} for 0, {@link #ECDH} for 1
     */
    public static KekMode fromBit(int bit) {
        return bit == 0 ? PUBLIC_KEY : ECDH;
    }
}
