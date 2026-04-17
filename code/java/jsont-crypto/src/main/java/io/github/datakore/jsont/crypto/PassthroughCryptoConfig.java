package io.github.datakore.jsont.crypto;

/**
 * Identity {@link CryptoConfig} — all crypto operations are identity functions.
 *
 * <ul>
 *   <li>{@link #wrapDek} / {@link #unwrapDek} return the input bytes unchanged.</li>
 *   <li>{@link #encryptField} returns a fixed all-zero IV and the plaintext as the
 *       "ciphertext" — deterministic for tests.</li>
 *   <li>{@link #decryptField} returns {@code encContent} unchanged.</li>
 * </ul>
 *
 * <p>Digest verification still passes because SHA-256 is computed over the original
 * plaintext by the writer before being embedded in the per-field payload.
 *
 * <p><b>Not for production use.</b>
 */
public final class PassthroughCryptoConfig implements CryptoConfig {

    private static final int IV_LEN = 12;

    @Override
    public byte[] wrapDek(int version, byte[] dek) {
        return dek.clone();
    }

    @Override
    public byte[] unwrapDek(int version, byte[] encDek) {
        return encDek.clone();
    }

    /** Returns {@code (iv=[0,0,...,0], encContent=plaintext)} — deterministic for tests. */
    @Override
    public EncryptedField encryptField(byte[] dek, byte[] plaintext) {
        return new EncryptedField(new byte[IV_LEN], plaintext.clone());
    }

    @Override
    public byte[] decryptField(byte[] dek, byte[] iv, byte[] encContent) {
        return encContent.clone();
    }
}
