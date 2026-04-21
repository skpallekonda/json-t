package io.github.datakore.jsont.crypto;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/** ChaCha20-Poly1305 field cipher handler (algo_ver = 2). Uses JCA built-in (Java 11+). */
final class ChaCha20CipherHandler implements FieldCipherHandler {

    static final ChaCha20CipherHandler INSTANCE = new ChaCha20CipherHandler();

    private static final int IV_LEN = 12;

    private ChaCha20CipherHandler() {}

    @Override public int algoVer() { return 2; }
    @Override public int ivLen()   { return IV_LEN; }

    @Override
    public byte[] encrypt(byte[] key, byte[] iv, byte[] plaintext) throws CryptoError.EncryptFailed {
        try {
            Cipher cipher = Cipher.getInstance("ChaCha20-Poly1305");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "ChaCha20"), new IvParameterSpec(iv));
            return cipher.doFinal(plaintext);
        } catch (Exception e) {
            throw new CryptoError.EncryptFailed("", "ChaCha20-Poly1305 encryption failed: " + e.getMessage(), e);
        }
    }

    @Override
    public byte[] decrypt(byte[] key, byte[] iv, byte[] ciphertext) throws CryptoError.DecryptFailed {
        try {
            Cipher cipher = Cipher.getInstance("ChaCha20-Poly1305");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "ChaCha20"), new IvParameterSpec(iv));
            return cipher.doFinal(ciphertext);
        } catch (Exception e) {
            throw new CryptoError.DecryptFailed("", "ChaCha20-Poly1305 decryption failed: " + e.getMessage(), e);
        }
    }
}
