package io.github.datakore.jsont.crypto;

/**
 * Pluggable encryption/decryption for sensitive ({@code ~}) fields.
 *
 * <p>Implementations plug in at three sites:
 * <ul>
 *   <li><b>Stringify</b> ({@code RowWriter.writeRow(row, fields, crypto, w)}) —
 *       encrypt plaintext values when writing to wire format.</li>
 *   <li><b>Transform</b> ({@code Decrypt} operation) —
 *       decrypt {@code Encrypted} values during a derived-schema pipeline.</li>
 *   <li><b>On-demand</b> ({@code JsonTRow.decryptField}) —
 *       caller-driven per-field decryption.</li>
 * </ul>
 *
 * <h2>Contract</h2>
 * <ul>
 *   <li>{@code encrypt(field, plaintext)} → ciphertext bytes. Written as
 *       {@code base64:<b64>} on the wire.</li>
 *   <li>{@code decrypt(field, ciphertext)} → plaintext bytes. Input is the raw
 *       bytes decoded from the {@code base64:} envelope (output of the
 *       corresponding {@code encrypt} call).</li>
 *   <li>Round-tripping {@code encrypt} then {@code decrypt} must return the
 *       original plaintext.</li>
 * </ul>
 *
 * <h2>Field name</h2>
 * <p>The {@code field} parameter lets implementations apply per-field key
 * derivation or access-control policies. Implementations that do not need it
 * may ignore it.
 */
public interface CryptoConfig {

    /**
     * Encrypt {@code plaintext} bytes for the named field.
     *
     * @param field     the field name (may be used for key derivation)
     * @param plaintext the raw UTF-8 bytes of the plaintext value
     * @return ciphertext bytes
     * @throws CryptoError if encryption fails
     */
    byte[] encrypt(String field, byte[] plaintext) throws CryptoError;

    /**
     * Decrypt {@code ciphertext} bytes for the named field.
     *
     * @param field      the field name (may be used for key derivation)
     * @param ciphertext the raw ciphertext bytes (decoded from the base64 envelope)
     * @return plaintext bytes
     * @throws CryptoError if decryption fails
     */
    byte[] decrypt(String field, byte[] ciphertext) throws CryptoError;
}
