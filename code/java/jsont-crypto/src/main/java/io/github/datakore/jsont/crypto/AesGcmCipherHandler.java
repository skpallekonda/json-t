package io.github.datakore.jsont.crypto;

import org.bouncycastle.crypto.engines.AESEngine;
import org.bouncycastle.crypto.modes.GCMBlockCipher;
import org.bouncycastle.crypto.modes.GCMModeCipher;
import org.bouncycastle.crypto.params.AEADParameters;
import org.bouncycastle.crypto.params.KeyParameter;

/** AES-256-GCM field cipher handler (algo_ver = 1). Uses BouncyCastle lightweight API. */
final class AesGcmCipherHandler implements FieldCipherHandler {

    static final AesGcmCipherHandler INSTANCE = new AesGcmCipherHandler();

    private static final int IV_LEN   = 12;
    private static final int TAG_BITS = 128;

    private AesGcmCipherHandler() {}

    @Override public int algoVer() { return 1; }
    @Override public int ivLen()   { return IV_LEN; }

    @Override
    public byte[] encrypt(byte[] key, byte[] iv, byte[] plaintext) throws CryptoError.EncryptFailed {
        try {
            GCMModeCipher gcm = GCMBlockCipher.newInstance(AESEngine.newInstance());
            gcm.init(true, new AEADParameters(new KeyParameter(key), TAG_BITS, iv));
            byte[] out = new byte[gcm.getOutputSize(plaintext.length)];
            int n = gcm.processBytes(plaintext, 0, plaintext.length, out, 0);
            gcm.doFinal(out, n);
            return out;
        } catch (Exception e) {
            throw new CryptoError.EncryptFailed("", "AES-GCM encryption failed: " + e.getMessage(), e);
        }
    }

    @Override
    public byte[] decrypt(byte[] key, byte[] iv, byte[] ciphertext) throws CryptoError.DecryptFailed {
        try {
            GCMModeCipher gcm = GCMBlockCipher.newInstance(AESEngine.newInstance());
            gcm.init(false, new AEADParameters(new KeyParameter(key), TAG_BITS, iv));
            byte[] out = new byte[gcm.getOutputSize(ciphertext.length)];
            int n = gcm.processBytes(ciphertext, 0, ciphertext.length, out, 0);
            gcm.doFinal(out, n);
            return out;
        } catch (Exception e) {
            throw new CryptoError.DecryptFailed("", "AES-GCM decryption failed: " + e.getMessage(), e);
        }
    }
}
