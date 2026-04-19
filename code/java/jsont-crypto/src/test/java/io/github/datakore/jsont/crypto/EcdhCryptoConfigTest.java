package io.github.datakore.jsont.crypto;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link EcdhCryptoConfig}: ECDH-derived KEK wrap/unwrap round-trips.
 *
 * <p>Two EC P-256 key pairs (Party A and Party B) are generated once per suite.
 * ECDH symmetry guarantees: ECDH(A_priv, B_pub) == ECDH(B_priv, A_pub), so
 * wrapping with (B_pub, A_priv) and unwrapping with (A_pub, B_priv) derives the same KEK.
 */
class EcdhCryptoConfigTest {

    private static byte[] partyAPubDer;
    private static String partyAPrivPem;
    private static byte[] partyBPubDer;
    private static String partyBPrivPem;

    @BeforeAll
    static void generateKeyPairs() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
        kpg.initialize(new ECGenParameterSpec("secp256r1"));

        KeyPair kpA = kpg.generateKeyPair();
        KeyPair kpB = kpg.generateKeyPair();

        partyAPubDer  = kpA.getPublic().getEncoded();   // DER SubjectPublicKeyInfo
        partyAPrivPem = toPem("PRIVATE KEY", kpA.getPrivate().getEncoded());
        partyBPubDer  = kpB.getPublic().getEncoded();
        partyBPrivPem = toPem("PRIVATE KEY", kpB.getPrivate().getEncoded());
    }

    private static String toPem(String type, byte[] der) {
        String b64 = Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(der);
        return "-----BEGIN " + type + "-----\n" + b64 + "\n-----END " + type + "-----\n";
    }

    // ── wrapDek / unwrapDek round-trip (AES-GCM) ─────────────────────────────

    @Test
    void wrapDek_unwrapDek_aesGcm_roundTrip() throws CryptoError {
        // A wraps for B: uses B's public key + A's private key
        EcdhCryptoConfig wrapper   = EcdhCryptoConfig.ofKeys(partyBPubDer, partyAPrivPem);
        // B unwraps: uses A's public key + B's private key
        EcdhCryptoConfig unwrapper = EcdhCryptoConfig.ofKeys(partyAPubDer, partyBPrivPem);

        byte[] dek = new byte[32];
        for (int i = 0; i < 32; i++) dek[i] = (byte) (i + 1);

        byte[] wrapped   = wrapper.wrapDek(CryptoContext.VERSION_AES_ECDH, dek);
        byte[] unwrapped = unwrapper.unwrapDek(CryptoContext.VERSION_AES_ECDH, wrapped);

        assertArrayEquals(dek, unwrapped);
    }

    @Test
    void wrapDek_unwrapDek_chaCha20_roundTrip() throws CryptoError {
        EcdhCryptoConfig wrapper   = EcdhCryptoConfig.ofKeys(partyBPubDer, partyAPrivPem);
        EcdhCryptoConfig unwrapper = EcdhCryptoConfig.ofKeys(partyAPubDer, partyBPrivPem);

        byte[] dek = new byte[32];
        for (int i = 0; i < 32; i++) dek[i] = (byte) (i * 2 + 3);

        byte[] wrapped   = wrapper.wrapDek(CryptoContext.VERSION_CHACHA_ECDH, dek);
        byte[] unwrapped = unwrapper.unwrapDek(CryptoContext.VERSION_CHACHA_ECDH, wrapped);

        assertArrayEquals(dek, unwrapped);
    }

    @Test
    void wrapDek_unwrapDek_ascon_roundTrip() throws CryptoError {
        EcdhCryptoConfig wrapper   = EcdhCryptoConfig.ofKeys(partyBPubDer, partyAPrivPem);
        EcdhCryptoConfig unwrapper = EcdhCryptoConfig.ofKeys(partyAPubDer, partyBPrivPem);

        byte[] dek = new byte[32];
        for (int i = 0; i < 32; i++) dek[i] = (byte) (i + 7);

        byte[] wrapped   = wrapper.wrapDek(CryptoContext.VERSION_ASCON_ECDH, dek);
        byte[] unwrapped = unwrapper.unwrapDek(CryptoContext.VERSION_ASCON_ECDH, wrapped);

        assertArrayEquals(dek, unwrapped);
    }

    // ── Wrong key must fail ───────────────────────────────────────────────────

    @Test
    void unwrapDek_withWrongKey_throws() throws CryptoError {
        EcdhCryptoConfig wrapper = EcdhCryptoConfig.ofKeys(partyBPubDer, partyAPrivPem);

        byte[] dek     = new byte[32];
        byte[] wrapped = wrapper.wrapDek(CryptoContext.VERSION_AES_ECDH, dek);

        // Unwrap with B's private key but A's public key swapped — uses wrong shared secret
        EcdhCryptoConfig wrongUnwrapper = EcdhCryptoConfig.ofKeys(partyBPubDer, partyBPrivPem);
        assertThrows(CryptoError.class,
                () -> wrongUnwrapper.unwrapDek(CryptoContext.VERSION_AES_ECDH, wrapped));
    }

    // ── CryptoContext full round-trip (ECDH mode) ─────────────────────────────

    @Test
    void forEncrypt_forDecrypt_ecdhMode_roundTrip() throws CryptoError {
        EcdhCryptoConfig encCfg = EcdhCryptoConfig.ofKeys(partyBPubDer, partyAPrivPem);
        EcdhCryptoConfig decCfg = EcdhCryptoConfig.ofKeys(partyAPubDer, partyBPrivPem);

        byte[] plaintext = "ECDH protected secret".getBytes(java.nio.charset.StandardCharsets.UTF_8);

        byte[] encDekOnWire;
        int    versionOnWire;
        EncryptedField ef;

        try (CryptoContext enc = CryptoContext.forEncrypt(AlgoVersion.AES_GCM, KekMode.ECDH, encCfg)) {
            ef            = enc.encryptField(plaintext);
            encDekOnWire  = enc.encDek();
            versionOnWire = enc.version();
        }

        try (CryptoContext dec = CryptoContext.forDecrypt(versionOnWire, encDekOnWire, decCfg)) {
            byte[] recovered = dec.decryptField(ef.iv(), ef.encContent());
            assertArrayEquals(plaintext, recovered);
        }
    }

    // ── Missing env var ───────────────────────────────────────────────────────

    @Test
    void missingEnvVar_throwsKeyNotFound() {
        EcdhCryptoConfig cfg = new EcdhCryptoConfig(partyBPubDer, "JSONT_MISSING_ECDH_PRIV_XYZ");
        byte[] dek = new byte[32];
        assertThrows(CryptoError.KeyNotFound.class,
                () -> cfg.wrapDek(CryptoContext.VERSION_AES_ECDH, dek));
    }
}
