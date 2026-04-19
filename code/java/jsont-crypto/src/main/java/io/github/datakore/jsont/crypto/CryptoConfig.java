package io.github.datakore.jsont.crypto;

/**
 * Pluggable key-material provider for the stream-level DEK model.
 *
 * <p>Implementations are responsible for DEK wrapping and unwrapping only.
 * Field-level cipher selection and execution live in {@link CryptoContext},
 * which dispatches based on the {@code algo_ver} bits in the version field.
 *
 * <h2>Implementations</h2>
 * <ul>
 *   <li>{@link PublicKeyCryptoConfig} — RSA-OAEP-SHA256 wrap/unwrap via env-var keys</li>
 *   <li>{@link EcdhCryptoConfig} — HKDF-SHA256 shared secret + algo-cipher DEK wrap/unwrap</li>
 *   <li>{@link PassthroughCryptoConfig} — identity (tests only)</li>
 * </ul>
 */
public interface CryptoConfig {

    /**
     * Wrap a raw DEK for writing to the {@code EncryptHeader} row.
     *
     * <p>For {@link KekMode#PUBLIC_KEY}: RSA-OAEP encrypts {@code dek} with the receiver's public key.
     * <br>For {@link KekMode#ECDH}: derives a KEK via HKDF-SHA256 and encrypts {@code dek}
     * with the cipher selected by {@code algo_ver} bits in {@code version}.
     *
     * @param version the packed version field (carries {@code algo_ver} and {@code kek_mode} bits)
     * @param dek     the raw plaintext DEK bytes (32 bytes for AES-256)
     * @return the wrapped (encrypted) DEK bytes
     * @throws CryptoError if wrapping fails
     */
    byte[] wrapDek(int version, byte[] dek) throws CryptoError;

    /**
     * Unwrap {@code encDek} from the {@code EncryptHeader} to recover the raw DEK.
     *
     * <p>For {@link KekMode#PUBLIC_KEY}: RSA-OAEP decrypts with the receiver's private key.
     * <br>For {@link KekMode#ECDH}: derives the same KEK via HKDF-SHA256 and decrypts —
     * AEAD authentication failure throws {@link CryptoError.DekMismatch}.
     *
     * <p>The caller (i.e., {@link CryptoContext}) must zero the returned array after use.
     *
     * @param version the version field from the header (must match the value used during wrap)
     * @param encDek  the wrapped DEK bytes as read from the wire
     * @return the raw plaintext DEK bytes
     * @throws CryptoError if unwrapping or authentication fails
     */
    byte[] unwrapDek(int version, byte[] encDek) throws CryptoError;
}
