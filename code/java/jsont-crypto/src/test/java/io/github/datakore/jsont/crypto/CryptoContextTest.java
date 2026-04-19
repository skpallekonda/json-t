package io.github.datakore.jsont.crypto;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link CryptoContext}: factory methods, algo dispatch, and round-trip correctness.
 * Uses {@link PassthroughCryptoConfig} to isolate CryptoContext from key-material concerns.
 */
class CryptoContextTest {

    private static final PassthroughCryptoConfig PASSTHROUGH = new PassthroughCryptoConfig();

    // ── forEncrypt / forDecrypt ───────────────────────────────────────────────

    @Test
    void forEncrypt_storesVersionAndEncDek() throws CryptoError {
        try (CryptoContext ctx = CryptoContext.forEncrypt(AlgoVersion.AES_GCM, KekMode.PUBLIC_KEY, PASSTHROUGH)) {
            assertEquals(CryptoContext.VERSION_AES_PUBKEY, ctx.version());
            assertNotNull(ctx.encDek());
            assertEquals(32, ctx.encDek().length); // passthrough returns DEK unchanged
        }
    }

    @Test
    void forEncrypt_chaCha20_setsCorrectVersion() throws CryptoError {
        try (CryptoContext ctx = CryptoContext.forEncrypt(AlgoVersion.CHACHA20_POLY1305, KekMode.PUBLIC_KEY, PASSTHROUGH)) {
            assertEquals(CryptoContext.VERSION_CHACHA_PUBKEY, ctx.version());
            assertEquals(2, ctx.algoVer());
            assertEquals(0, ctx.kekMode());
        }
    }

    @Test
    void forDecrypt_roundTripsEncDek() throws CryptoError {
        byte[] fakeEncDek = new byte[32];
        for (int i = 0; i < 32; i++) fakeEncDek[i] = (byte) i;

        try (CryptoContext ctx = CryptoContext.forDecrypt(CryptoContext.VERSION_AES_PUBKEY, fakeEncDek, PASSTHROUGH)) {
            assertEquals(CryptoContext.VERSION_AES_PUBKEY, ctx.version());
            assertArrayEquals(fakeEncDek, ctx.encDek());
        }
    }

    @Test
    void forDecrypt_failsFastOnWrongDekLength() {
        byte[] badDek = new byte[16]; // must be 32
        assertThrows(CryptoError.DekUnwrapFailed.class,
                () -> CryptoContext.forDecrypt(CryptoContext.VERSION_AES_PUBKEY, badDek, PASSTHROUGH));
    }

    // ── Version bit accessors ─────────────────────────────────────────────────

    @Test
    void versionBits_aesPublicKey() throws CryptoError {
        try (CryptoContext ctx = CryptoContext.forEncrypt(AlgoVersion.AES_GCM, KekMode.PUBLIC_KEY, PASSTHROUGH)) {
            assertEquals(1, ctx.algoVer());
            assertEquals(0, ctx.kekMode());
        }
    }

    @Test
    void versionBits_aesEcdh() throws CryptoError {
        try (CryptoContext ctx = CryptoContext.forEncrypt(AlgoVersion.AES_GCM, KekMode.ECDH, PASSTHROUGH)) {
            assertEquals(CryptoContext.VERSION_AES_ECDH, ctx.version());
            assertEquals(1, ctx.algoVer());
            assertEquals(1, ctx.kekMode());
        }
    }

    // ── AES-GCM field encrypt / decrypt ──────────────────────────────────────

    @Test
    void aesGcm_encryptDecrypt_roundTrip() throws CryptoError {
        byte[] plaintext = "hello world".getBytes(StandardCharsets.UTF_8);

        try (CryptoContext enc = CryptoContext.forEncrypt(AlgoVersion.AES_GCM, KekMode.PUBLIC_KEY, PASSTHROUGH)) {
            EncryptedField ef = enc.encryptField(plaintext);
            assertNotNull(ef.iv());
            assertEquals(12, ef.iv().length);
            assertFalse(java.util.Arrays.equals(plaintext, ef.encContent())); // must be ciphertext

            // Decrypt using same DEK — reconstruct forDecrypt with the enc_dek from forEncrypt
            try (CryptoContext dec = CryptoContext.forDecrypt(enc.version(), enc.encDek(), PASSTHROUGH)) {
                byte[] recovered = dec.decryptField(ef.iv(), ef.encContent());
                assertArrayEquals(plaintext, recovered);
            }
        }
    }

    @Test
    void aesGcm_ivIsUniquePerCall() throws CryptoError {
        byte[] plaintext = "data".getBytes(StandardCharsets.UTF_8);
        try (CryptoContext ctx = CryptoContext.forEncrypt(AlgoVersion.AES_GCM, KekMode.PUBLIC_KEY, PASSTHROUGH)) {
            EncryptedField ef1 = ctx.encryptField(plaintext);
            EncryptedField ef2 = ctx.encryptField(plaintext);
            assertFalse(java.util.Arrays.equals(ef1.iv(), ef2.iv()), "IVs must differ across calls");
        }
    }

    // ── ChaCha20-Poly1305 field encrypt / decrypt ─────────────────────────────

    @Test
    void chaCha20_encryptDecrypt_roundTrip() throws CryptoError {
        byte[] plaintext = "chacha test data".getBytes(StandardCharsets.UTF_8);

        try (CryptoContext enc = CryptoContext.forEncrypt(AlgoVersion.CHACHA20_POLY1305, KekMode.PUBLIC_KEY, PASSTHROUGH)) {
            EncryptedField ef = enc.encryptField(plaintext);
            assertEquals(12, ef.iv().length);

            try (CryptoContext dec = CryptoContext.forDecrypt(enc.version(), enc.encDek(), PASSTHROUGH)) {
                byte[] recovered = dec.decryptField(ef.iv(), ef.encContent());
                assertArrayEquals(plaintext, recovered);
            }
        }
    }

    // ── ASCON-128a field encrypt / decrypt ───────────────────────────────────

    @Test
    void ascon128a_encryptDecrypt_roundTrip() throws CryptoError {
        byte[] plaintext = "ascon test data".getBytes(StandardCharsets.UTF_8);

        try (CryptoContext enc = CryptoContext.forEncrypt(AlgoVersion.ASCON, KekMode.PUBLIC_KEY, PASSTHROUGH)) {
            assertEquals(CryptoContext.VERSION_ASCON_PUBKEY, enc.version());
            EncryptedField ef = enc.encryptField(plaintext);
            assertEquals(16, ef.iv().length, "ASCON-128a nonce must be 16 bytes");

            try (CryptoContext dec = CryptoContext.forDecrypt(enc.version(), enc.encDek(), PASSTHROUGH)) {
                byte[] recovered = dec.decryptField(ef.iv(), ef.encContent());
                assertArrayEquals(plaintext, recovered);
            }
        }
    }

    @Test
    void ascon128a_ivIsUniquePerCall() throws CryptoError {
        byte[] plaintext = "data".getBytes(StandardCharsets.UTF_8);
        try (CryptoContext ctx = CryptoContext.forEncrypt(AlgoVersion.ASCON, KekMode.PUBLIC_KEY, PASSTHROUGH)) {
            EncryptedField ef1 = ctx.encryptField(plaintext);
            EncryptedField ef2 = ctx.encryptField(plaintext);
            assertFalse(java.util.Arrays.equals(ef1.iv(), ef2.iv()), "IVs must differ across calls");
        }
    }

    // ── Unknown algo_ver throws ───────────────────────────────────────────────

    @Test
    void unknownAlgoVer_throwsUnsupportedAlgorithm() throws CryptoError {
        // Manually craft a version with algo_ver=15 (reserved/unknown)
        int unknownVersion = (15 << 3); // algo=15, kek_mode=0
        byte[] fakeEncDek = new byte[32];
        try (CryptoContext ctx = CryptoContext.forDecrypt(unknownVersion, fakeEncDek, PASSTHROUGH)) {
            assertThrows(CryptoError.UnsupportedAlgorithm.class,
                    () -> ctx.encryptField("x".getBytes(StandardCharsets.UTF_8)));
        }
    }
}
