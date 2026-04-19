package io.github.datakore.jsont.crypto;

import org.bouncycastle.crypto.engines.AsconEngine;
import org.bouncycastle.crypto.params.AEADParameters;
import org.bouncycastle.crypto.params.KeyParameter;

import java.util.Arrays;

/**
 * ASCON-128a field cipher handler (algo_ver = 3). Uses BouncyCastle lightweight API.
 *
 * <p>ASCON-128a requires a 128-bit key. Only the first 16 bytes of the supplied key
 * (DEK or KEK) are used; the copy is zeroed in a {@code finally} block.
 */
final class Ascon128aCipherHandler implements FieldCipherHandler {

    static final Ascon128aCipherHandler INSTANCE = new Ascon128aCipherHandler();

    private static final int IV_LEN   = 16;
    private static final int KEY_LEN  = 16; // ASCON-128a: 128-bit key (first half of 32-byte DEK/KEK)
    private static final int TAG_BITS = 128;

    private Ascon128aCipherHandler() {}

    @Override public int algoVer() { return 3; }
    @Override public int ivLen()   { return IV_LEN; }

    @Override
    public byte[] encrypt(byte[] key, byte[] iv, byte[] plaintext) throws CryptoError.EncryptFailed {
        byte[] k = Arrays.copyOf(key, KEY_LEN);
        try {
            AsconEngine ascon = new AsconEngine(AsconEngine.AsconParameters.ascon128a);
            ascon.init(true, new AEADParameters(new KeyParameter(k), TAG_BITS, iv));
            byte[] out = new byte[ascon.getOutputSize(plaintext.length)];
            int n = ascon.processBytes(plaintext, 0, plaintext.length, out, 0);
            ascon.doFinal(out, n);
            return out;
        } catch (Exception e) {
            throw new CryptoError.EncryptFailed("", "ASCON-128a encryption failed: " + e.getMessage(), e);
        } finally {
            Arrays.fill(k, (byte) 0);
        }
    }

    @Override
    public byte[] decrypt(byte[] key, byte[] iv, byte[] ciphertext) throws CryptoError.DecryptFailed {
        byte[] k = Arrays.copyOf(key, KEY_LEN);
        try {
            AsconEngine ascon = new AsconEngine(AsconEngine.AsconParameters.ascon128a);
            ascon.init(false, new AEADParameters(new KeyParameter(k), TAG_BITS, iv));
            byte[] out = new byte[ascon.getOutputSize(ciphertext.length)];
            int n = ascon.processBytes(ciphertext, 0, ciphertext.length, out, 0);
            ascon.doFinal(out, n);
            return out;
        } catch (Exception e) {
            throw new CryptoError.DecryptFailed("", "ASCON-128a decryption failed: " + e.getMessage(), e);
        } finally {
            Arrays.fill(k, (byte) 0);
        }
    }
}
