package io.github.datakore.jsont.crypto;

import java.util.List;

/**
 * Registry of all supported {@link FieldCipherHandler} implementations.
 *
 * <p>Adding a new algorithm requires only a new {@link FieldCipherHandler} implementation
 * and a single entry here — no changes to {@link CryptoContext} or {@link EcdhCryptoConfig}.
 */
final class FieldCipherRegistry {

    private static final List<FieldCipherHandler> HANDLERS = List.of(
            AesGcmCipherHandler.INSTANCE,
            ChaCha20CipherHandler.INSTANCE,
            Ascon128aCipherHandler.INSTANCE
    );

    private FieldCipherRegistry() {}

    /**
     * Find the handler for the given {@code algo_ver} nibble.
     *
     * @throws CryptoError.UnsupportedAlgorithm if no handler is registered for {@code algoVer}
     */
    static FieldCipherHandler find(int algoVer) throws CryptoError.UnsupportedAlgorithm {
        for (FieldCipherHandler h : HANDLERS) {
            if (h.algoVer() == algoVer) return h;
        }
        throw new CryptoError.UnsupportedAlgorithm(algoVer, "unknown algo_ver");
    }
}
