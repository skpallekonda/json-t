package io.github.datakore.jsont.crypto;

/**
 * Identity {@link CryptoConfig} — bytes are returned unchanged.
 *
 * <p>Useful for tests and for pipelines that only need the {@code base64:}
 * wire format without actual encryption (bytes are stored as-is).
 *
 * <pre>{@code
 *   CryptoConfig cfg = new PassthroughCryptoConfig();
 *   byte[] ct = cfg.encrypt("ssn", "123-45-6789".getBytes());
 *   byte[] pt = cfg.decrypt("ssn", ct);
 *   assert Arrays.equals(pt, "123-45-6789".getBytes());
 * }</pre>
 */
public final class PassthroughCryptoConfig implements CryptoConfig {

    @Override
    public byte[] encrypt(String field, byte[] plaintext) {
        return plaintext.clone();
    }

    @Override
    public byte[] decrypt(String field, byte[] ciphertext) {
        return ciphertext.clone();
    }
}
