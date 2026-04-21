package io.github.datakore.jsont.crypto;

import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.crypto.agreement.ECDHBasicAgreement;
import org.bouncycastle.crypto.generators.HKDFBytesGenerator;
import org.bouncycastle.crypto.params.AsymmetricKeyParameter;
import org.bouncycastle.crypto.params.HKDFParameters;
import org.bouncycastle.crypto.util.PrivateKeyFactory;
import org.bouncycastle.crypto.util.PublicKeyFactory;
import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemReader;

import java.io.IOException;
import java.io.StringReader;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * {@link CryptoConfig} that wraps and unwraps the DEK using ECDH P-256 key agreement
 * followed by HKDF-SHA256 key derivation.
 *
 * <h2>Protocol</h2>
 * <ol>
 *   <li>ECDH agreement: {@code sharedSecret = ECDH(host_priv, peer_pub)}</li>
 *   <li>KEK derivation: {@code kek = HKDF-SHA256(sharedSecret, salt=version_bytes, info="jsont-dek")}</li>
 *   <li>DEK wrap: encrypt DEK with the cipher selected by {@code algo_ver} in {@code version}
 *       using the KEK via {@link FieldCipherRegistry}; the result is {@code iv || ciphertext+tag}.</li>
 *   <li>DEK unwrap: derive the same KEK, split {@code enc_dek} into {@code iv} and
 *       {@code ciphertext+tag}, decrypt — AEAD auth failure → {@link CryptoError.DekMismatch}.</li>
 * </ol>
 *
 * <h2>Key material</h2>
 * <ul>
 *   <li><b>Peer public key</b> — supplied as DER-encoded {@code SubjectPublicKeyInfo} byte array
 *       (the other party's EC P-256 public key).</li>
 *   <li><b>Host private key</b> — read from the named environment variable at call time
 *       (PKCS#8 PEM or stripped Base64-DER).</li>
 * </ul>
 */
public final class EcdhCryptoConfig implements CryptoConfig {

    private static final int KEK_LEN = 32;

    private static final byte[] HKDF_INFO = "jsont-dek".getBytes(StandardCharsets.UTF_8);

    /** DER-encoded SubjectPublicKeyInfo of the peer's EC P-256 public key. */
    private final byte[] peerPublicKeyDer;

    /** Name of the env var holding the host's PKCS#8 EC private key. */
    private final String hostPrivKeyVar;

    /** Non-null only when the key is supplied directly via {@link #ofKeys}. */
    private final String hostPrivKeyPem;

    /**
     * Env-var-based constructor. The host private key is read from the named environment
     * variable at each {@link #wrapDek} / {@link #unwrapDek} call.
     *
     * @param peerPublicKeyDer DER-encoded SubjectPublicKeyInfo of the peer's EC public key
     * @param hostPrivKeyVar   environment variable name containing the host's PKCS#8 EC private key
     */
    public EcdhCryptoConfig(byte[] peerPublicKeyDer, String hostPrivKeyVar) {
        this.peerPublicKeyDer = peerPublicKeyDer.clone();
        this.hostPrivKeyVar   = hostPrivKeyVar;
        this.hostPrivKeyPem   = null;
    }

    private EcdhCryptoConfig(byte[] peerPublicKeyDer, String hostPrivKeyVar, String hostPrivKeyPem) {
        this.peerPublicKeyDer = peerPublicKeyDer.clone();
        this.hostPrivKeyVar   = hostPrivKeyVar;
        this.hostPrivKeyPem   = hostPrivKeyPem;
    }

    /**
     * Creates an instance from key bytes directly — useful for tests and programmatic
     * contexts where environment variables are unavailable.
     *
     * @param peerPublicKeyDer DER-encoded SubjectPublicKeyInfo of the peer's EC public key
     * @param hostPrivKeyPem   PKCS#8 PEM or stripped Base64-DER private key of the host
     */
    public static EcdhCryptoConfig ofKeys(byte[] peerPublicKeyDer, String hostPrivKeyPem) {
        return new EcdhCryptoConfig(peerPublicKeyDer, "<direct>", hostPrivKeyPem);
    }

    // ── CryptoConfig ──────────────────────────────────────────────────────────

    /**
     * Derive KEK via ECDH + HKDF-SHA256, then encrypt DEK with the algo selected by {@code version}.
     *
     * <p>Returns {@code iv || ciphertext+tag}.
     */
    @Override
    public byte[] wrapDek(int version, byte[] dek) throws CryptoError {
        byte[] kek = deriveKek(version);
        try {
            int algoVer = (version >> 3) & 0x0F;
            FieldCipherHandler handler = FieldCipherRegistry.find(algoVer);
            byte[] iv = new byte[handler.ivLen()];
            new SecureRandom().nextBytes(iv);
            byte[] ciphertext;
            try {
                ciphertext = handler.encrypt(kek, iv, dek);
            } catch (CryptoError.EncryptFailed e) {
                throw new CryptoError.DekWrapFailed("DEK wrap failed: " + e.getMessage(), e);
            }
            byte[] result = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv,         0, result, 0,         iv.length);
            System.arraycopy(ciphertext, 0, result, iv.length, ciphertext.length);
            return result;
        } finally {
            Arrays.fill(kek, (byte) 0);
        }
    }

    /**
     * Derive KEK via ECDH + HKDF-SHA256, split {@code encDek} into iv + ciphertext, decrypt.
     *
     * <p>AEAD authentication failure throws {@link CryptoError.DekMismatch}.
     */
    @Override
    public byte[] unwrapDek(int version, byte[] encDek) throws CryptoError {
        byte[] kek = deriveKek(version);
        try {
            int algoVer = (version >> 3) & 0x0F;
            FieldCipherHandler handler = FieldCipherRegistry.find(algoVer);
            int ivLen = handler.ivLen();
            if (encDek.length <= ivLen) {
                throw new CryptoError.DekUnwrapFailed("enc_dek too short for ECDH mode");
            }
            byte[] iv         = Arrays.copyOfRange(encDek, 0,     ivLen);
            byte[] ciphertext = Arrays.copyOfRange(encDek, ivLen, encDek.length);
            try {
                return handler.decrypt(kek, iv, ciphertext);
            } catch (CryptoError.DecryptFailed e) {
                throw new CryptoError.DekMismatch("DEK mismatch: " + e.getMessage(), e);
            }
        } finally {
            Arrays.fill(kek, (byte) 0);
        }
    }

    // ── ECDH + HKDF key derivation ────────────────────────────────────────────

    private byte[] deriveKek(int version) throws CryptoError {
        AsymmetricKeyParameter peerPub  = parsePeerPublicKey();
        AsymmetricKeyParameter hostPriv = loadHostPrivateKey();

        ECDHBasicAgreement agreement = new ECDHBasicAgreement();
        agreement.init(hostPriv);
        BigInteger sharedSecretInt;
        try {
            sharedSecretInt = agreement.calculateAgreement(peerPub);
        } catch (Exception e) {
            throw new CryptoError.DekWrapFailed("ECDH agreement failed: " + e.getMessage(), e);
        }
        byte[] sharedSecret = bigIntToFixedBytes(sharedSecretInt, 32);

        byte[] salt = { (byte) (version >> 8), (byte) (version & 0xFF) };
        HKDFBytesGenerator hkdf = new HKDFBytesGenerator(
                new org.bouncycastle.crypto.digests.SHA256Digest());
        hkdf.init(new HKDFParameters(sharedSecret, salt, HKDF_INFO));
        byte[] kek = new byte[KEK_LEN];
        hkdf.generateBytes(kek, 0, KEK_LEN);
        Arrays.fill(sharedSecret, (byte) 0);
        return kek;
    }

    // ── Key parsing ───────────────────────────────────────────────────────────

    private AsymmetricKeyParameter parsePeerPublicKey() throws CryptoError {
        try {
            SubjectPublicKeyInfo spki = SubjectPublicKeyInfo.getInstance(peerPublicKeyDer);
            return PublicKeyFactory.createKey(spki);
        } catch (IOException e) {
            throw new CryptoError.InvalidKey("failed to parse peer EC public key: " + e.getMessage(), e);
        }
    }

    private AsymmetricKeyParameter loadHostPrivateKey() throws CryptoError {
        if (hostPrivKeyPem != null) return parsePrivateKey(hostPrivKeyPem.trim());
        String val = System.getenv(hostPrivKeyVar);
        if (val == null || val.isBlank())
            throw new CryptoError.KeyNotFound(hostPrivKeyVar, "environment variable not set");
        return parsePrivateKey(val.trim());
    }

    private static AsymmetricKeyParameter parsePrivateKey(String s) throws CryptoError {
        try {
            byte[] der = s.startsWith("-----") ? pemToDer(s) : Base64.getDecoder().decode(s);
            PrivateKeyInfo info = PrivateKeyInfo.getInstance(der);
            return PrivateKeyFactory.createKey(info);
        } catch (IOException e) {
            throw new CryptoError.InvalidKey("failed to parse EC private key: " + e.getMessage(), e);
        }
    }

    private static byte[] pemToDer(String pem) throws CryptoError {
        try (PemReader reader = new PemReader(new StringReader(pem))) {
            PemObject obj = reader.readPemObject();
            if (obj == null) throw new CryptoError.InvalidKey("PEM block was empty");
            return obj.getContent();
        } catch (IOException e) {
            throw new CryptoError.InvalidKey("PEM parse error: " + e.getMessage(), e);
        }
    }

    /** Convert BigInteger to a fixed-length big-endian byte array (zero-padded or truncated). */
    private static byte[] bigIntToFixedBytes(BigInteger n, int len) {
        byte[] raw = n.toByteArray();
        if (raw.length == len) return raw;
        byte[] fixed = new byte[len];
        if (raw.length > len) {
            System.arraycopy(raw, raw.length - len, fixed, 0, len);
        } else {
            System.arraycopy(raw, 0, fixed, len - raw.length, raw.length);
        }
        return fixed;
    }
}
