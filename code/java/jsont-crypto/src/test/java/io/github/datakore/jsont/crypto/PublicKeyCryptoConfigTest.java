package io.github.datakore.jsont.crypto;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link PublicKeyCryptoConfig}: RSA-OAEP DEK wrap/unwrap round-trips.
 * Keys are generated once per test class and supplied via {@link PublicKeyCryptoConfig#ofKeys}.
 */
class PublicKeyCryptoConfigTest {

    private static String publicKeyPem;
    private static String privateKeyPem;

    @BeforeAll
    static void generateKeys() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair kp = kpg.generateKeyPair();

        publicKeyPem  = toPem("PUBLIC KEY",  kp.getPublic().getEncoded());
        privateKeyPem = toPem("PRIVATE KEY", kp.getPrivate().getEncoded());
    }

    private static String toPem(String type, byte[] der) {
        String b64 = Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(der);
        return "-----BEGIN " + type + "-----\n" + b64 + "\n-----END " + type + "-----\n";
    }

    // ── wrapDek / unwrapDek round-trip ────────────────────────────────────────

    @Test
    void wrapDek_unwrapDek_roundTrip() throws CryptoError {
        PublicKeyCryptoConfig cfg = PublicKeyCryptoConfig.ofKeys(publicKeyPem, privateKeyPem);

        byte[] dek = new byte[32];
        for (int i = 0; i < 32; i++) dek[i] = (byte) (i + 1);

        byte[] wrapped   = cfg.wrapDek(CryptoContext.VERSION_AES_PUBKEY, dek);
        byte[] unwrapped = cfg.unwrapDek(CryptoContext.VERSION_AES_PUBKEY, wrapped);

        assertArrayEquals(dek, unwrapped);
    }

    @Test
    void wrapDek_producesNonDeterministicOutput() throws CryptoError {
        PublicKeyCryptoConfig cfg = PublicKeyCryptoConfig.ofKeys(publicKeyPem, privateKeyPem);
        byte[] dek = new byte[32];

        byte[] wrapped1 = cfg.wrapDek(CryptoContext.VERSION_AES_PUBKEY, dek);
        byte[] wrapped2 = cfg.wrapDek(CryptoContext.VERSION_AES_PUBKEY, dek);

        // RSA-OAEP is probabilistic — same plaintext, different ciphertext each time
        assertFalse(java.util.Arrays.equals(wrapped1, wrapped2),
                "RSA-OAEP must produce different output each call");
    }

    @Test
    void unwrapDek_withWrongKey_throws() throws Exception {
        PublicKeyCryptoConfig cfg = PublicKeyCryptoConfig.ofKeys(publicKeyPem, privateKeyPem);

        // Generate a different key pair
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair other = kpg.generateKeyPair();
        String otherPrivPem = toPem("PRIVATE KEY", other.getPrivate().getEncoded());
        PublicKeyCryptoConfig wrongKey = PublicKeyCryptoConfig.ofKeys(publicKeyPem, otherPrivPem);

        byte[] dek     = new byte[32];
        byte[] wrapped = cfg.wrapDek(CryptoContext.VERSION_AES_PUBKEY, dek);

        assertThrows(CryptoError.DekUnwrapFailed.class,
                () -> wrongKey.unwrapDek(CryptoContext.VERSION_AES_PUBKEY, wrapped));
    }

    // ── CryptoContext integration ─────────────────────────────────────────────

    @Test
    void forEncrypt_forDecrypt_fullRoundTrip() throws CryptoError {
        PublicKeyCryptoConfig cfg = PublicKeyCryptoConfig.ofKeys(publicKeyPem, privateKeyPem);
        byte[] plaintext = "sensitive data".getBytes(java.nio.charset.StandardCharsets.UTF_8);

        byte[] encDekOnWire;
        int versionOnWire;
        EncryptedField ef;

        try (CryptoContext enc = CryptoContext.forEncrypt(AlgoVersion.AES_GCM, KekMode.PUBLIC_KEY, cfg)) {
            ef            = enc.encryptField(plaintext);
            encDekOnWire  = enc.encDek();
            versionOnWire = enc.version();
        }

        try (CryptoContext dec = CryptoContext.forDecrypt(versionOnWire, encDekOnWire, cfg)) {
            byte[] recovered = dec.decryptField(ef.iv(), ef.encContent());
            assertArrayEquals(plaintext, recovered);
        }
    }

    // ── KeyNotFound ───────────────────────────────────────────────────────────

    @Test
    void missingEnvVar_throwsKeyNotFound() {
        PublicKeyCryptoConfig cfg = new PublicKeyCryptoConfig(
                "JSONT_MISSING_PUB_KEY_XYZ", "JSONT_MISSING_PRIV_KEY_XYZ");
        byte[] dek = new byte[32];
        assertThrows(CryptoError.KeyNotFound.class,
                () -> cfg.wrapDek(CryptoContext.VERSION_AES_PUBKEY, dek));
    }
}
