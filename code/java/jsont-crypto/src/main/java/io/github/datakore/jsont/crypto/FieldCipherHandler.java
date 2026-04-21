package io.github.datakore.jsont.crypto;

/**
 * Strategy interface for a single symmetric AEAD algorithm used in JsonT crypto.
 *
 * <p>Implementations cover field-level encryption (using the DEK) and DEK wrapping
 * (using the KEK) with the same cipher logic. The caller decides which key to pass.
 *
 * <p>IV generation is the caller's responsibility — use {@link #ivLen()} to allocate
 * the correct number of bytes before invoking {@link #encrypt}.
 */
interface FieldCipherHandler {

    /** The {@code algo_ver} nibble (bits 6..3 of the version field) this handler owns. */
    int algoVer();

    /** Required IV length in bytes for this algorithm. */
    int ivLen();

    /**
     * Encrypt {@code plaintext} with {@code key} and {@code iv}.
     *
     * @param key       symmetric key bytes (DEK or KEK — caller's choice)
     * @param iv        freshly generated nonce of length {@link #ivLen()}
     * @param plaintext data to encrypt
     * @return ciphertext + authentication tag (concatenated)
     * @throws CryptoError.EncryptFailed if the cipher operation fails
     */
    byte[] encrypt(byte[] key, byte[] iv, byte[] plaintext) throws CryptoError.EncryptFailed;

    /**
     * Decrypt {@code ciphertext} (including authentication tag) with {@code key} and {@code iv}.
     *
     * @param key        symmetric key bytes (DEK or KEK — caller's choice)
     * @param iv         the nonce stored alongside the ciphertext
     * @param ciphertext ciphertext + authentication tag
     * @return plaintext bytes
     * @throws CryptoError.DecryptFailed if authentication fails or the cipher operation fails
     */
    byte[] decrypt(byte[] key, byte[] iv, byte[] ciphertext) throws CryptoError.DecryptFailed;
}
