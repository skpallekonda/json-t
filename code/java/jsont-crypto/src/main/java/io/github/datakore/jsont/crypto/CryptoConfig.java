package io.github.datakore.jsont.crypto;

/**
 * Pluggable crypto contract for encrypted JsonT streams (stream-level DEK model).
 *
 * <h2>Stream-level DEK model</h2>
 * <ol>
 *   <li>One DEK is generated per stream and wrapped once →
 *       written as the {@code EncryptHeader} row via {@link #wrapDek}.</li>
 *   <li>Every sensitive field is encrypted with that shared DEK
 *       via {@link #encryptField}.</li>
 *   <li>On read, the DEK is unwrapped once via {@link #unwrapDek} and reused
 *       for all subsequent {@link #decryptField} calls.</li>
 * </ol>
 *
 * <h2>Contract</h2>
 * <ul>
 *   <li>{@link #wrapDek} / {@link #unwrapDek} — round-trip must return the original DEK bytes.</li>
 *   <li>{@link #encryptField} must generate a fresh IV per call under AEAD ciphers;
 *       reusing an IV with the same DEK is catastrophic.</li>
 *   <li>{@link #encryptField} then {@link #decryptField} must round-trip the original plaintext.</li>
 * </ul>
 */
public interface CryptoConfig {

    /**
     * Wrap a raw DEK (plaintext) for writing to the {@code EncryptHeader} row.
     *
     * @param version the version field from the header (carries algo_ver and kek_mode bits)
     * @param dek     the raw plaintext DEK bytes
     * @return the wrapped (encrypted) DEK bytes
     * @throws CryptoError if wrapping fails
     */
    byte[] wrapDek(int version, byte[] dek) throws CryptoError;

    /**
     * Unwrap {@code encDek} from the {@code EncryptHeader} → raw plaintext DEK.
     *
     * <p>The caller must zero the returned array after use.
     *
     * @param version the version field from the header
     * @param encDek  the wrapped DEK bytes as read from the wire
     * @return the raw plaintext DEK bytes
     * @throws CryptoError if unwrapping fails
     */
    byte[] unwrapDek(int version, byte[] encDek) throws CryptoError;

    /**
     * Encrypt one field value with the shared DEK.
     *
     * <p>A fresh IV must be generated per call. The {@code encContent} returned
     * includes the authentication tag for AEAD ciphers.
     *
     * @param dek       the raw plaintext DEK
     * @param plaintext the field value bytes to encrypt
     * @return an {@link EncryptedField} holding the IV and ciphertext+tag
     * @throws CryptoError if encryption fails
     */
    EncryptedField encryptField(byte[] dek, byte[] plaintext) throws CryptoError;

    /**
     * Decrypt one field value using the shared DEK, the per-field IV, and the
     * raw ciphertext+tag bytes.
     *
     * @param dek        the raw plaintext DEK
     * @param iv         the per-field IV used during encryption
     * @param encContent the ciphertext+tag bytes
     * @return the plaintext bytes
     * @throws CryptoError if decryption or authentication fails
     */
    byte[] decryptField(byte[] dek, byte[] iv, byte[] encContent) throws CryptoError;

    /**
     * The result of a single field encryption: the per-field IV and ciphertext.
     *
     * @param iv         the nonce used for this field (must be unique per DEK)
     * @param encContent the ciphertext + authentication tag
     */
    record EncryptedField(byte[] iv, byte[] encContent) {
        public EncryptedField {
            iv         = iv.clone();
            encContent = encContent.clone();
        }
    }
}
