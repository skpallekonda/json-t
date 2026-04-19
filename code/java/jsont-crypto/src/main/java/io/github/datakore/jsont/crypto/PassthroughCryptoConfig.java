package io.github.datakore.jsont.crypto;

/**
 * Identity {@link CryptoConfig} — all key-material operations are identity functions.
 *
 * <ul>
 *   <li>{@link #wrapDek} returns the input DEK bytes unchanged.</li>
 *   <li>{@link #unwrapDek} returns the enc_dek bytes unchanged.</li>
 * </ul>
 *
 * <p>Field encryption/decryption is handled by {@link CryptoContext}, which dispatches
 * based on {@code algo_ver}. Use {@link AlgoVersion#AES_GCM} with this config.
 *
 * <p><b>Not for production use.</b>
 */
public final class PassthroughCryptoConfig implements CryptoConfig {

    @Override
    public byte[] wrapDek(int version, byte[] dek) {
        return dek.clone();
    }

    @Override
    public byte[] unwrapDek(int version, byte[] encDek) {
        return encDek.clone();
    }
}
