package io.github.datakore.jsont.crypto;

import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.crypto.AsymmetricBlockCipher;
import org.bouncycastle.crypto.CipherParameters;
import org.bouncycastle.crypto.InvalidCipherTextException;
import org.bouncycastle.crypto.engines.RSABlindedEngine;
import org.bouncycastle.crypto.encodings.OAEPEncoding;
import org.bouncycastle.crypto.params.AsymmetricKeyParameter;
import org.bouncycastle.crypto.params.ParametersWithRandom;
import org.bouncycastle.crypto.util.PrivateKeyFactory;
import org.bouncycastle.crypto.util.PublicKeyFactory;
import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemReader;

import java.io.IOException;
import java.io.StringReader;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * {@link CryptoConfig} that wraps and unwraps the DEK using RSA-OAEP-SHA256.
 *
 * <p>Key material is read from environment variables at call time — no key bytes are
 * stored as fields. Alternatively, use {@link #ofKeys(String, String)} to supply PEM
 * strings directly (useful in tests and programmatic contexts).
 *
 * <h2>Key format</h2>
 * <p>Each source (env var or string) must contain either a full PKCS#8 PEM block or
 * the same content as Base64-encoded DER with headers and newlines stripped.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 *   CryptoConfig cfg = new PublicKeyCryptoConfig("JSONT_PUBLIC_KEY", "JSONT_PRIVATE_KEY");
 *   try (CryptoContext ctx = CryptoContext.forEncrypt(AlgoVersion.AES_GCM, KekMode.PUBLIC_KEY, cfg)) {
 *       writer.writeStream(rows, fields, out);
 *   }
 * }</pre>
 */
public final class PublicKeyCryptoConfig implements CryptoConfig {

    private final String publicKeyVar;
    private final String privateKeyVar;

    // Non-null only when keys are supplied directly (test / programmatic path).
    private final String publicKeyPem;
    private final String privateKeyPem;

    /**
     * Env-var-based constructor. Key bytes are read from the named environment
     * variables at each {@link #wrapDek} / {@link #unwrapDek} call.
     *
     * @param publicKeyVar  environment variable containing the PKCS#8 public key
     * @param privateKeyVar environment variable containing the PKCS#8 private key
     */
    public PublicKeyCryptoConfig(String publicKeyVar, String privateKeyVar) {
        this.publicKeyVar  = publicKeyVar;
        this.privateKeyVar = privateKeyVar;
        this.publicKeyPem  = null;
        this.privateKeyPem = null;
    }

    /**
     * Creates an instance from PEM strings directly — useful for tests and
     * programmatic key injection without environment variables.
     *
     * @param publicKeyPem  PKCS#8 PEM or stripped Base64-DER public key
     * @param privateKeyPem PKCS#8 PEM or stripped Base64-DER private key
     */
    public static PublicKeyCryptoConfig ofKeys(String publicKeyPem, String privateKeyPem) {
        return new PublicKeyCryptoConfig(publicKeyPem, privateKeyPem, publicKeyPem, privateKeyPem);
    }

    private PublicKeyCryptoConfig(
            String publicKeyVar, String privateKeyVar,
            String publicKeyPem, String privateKeyPem) {
        this.publicKeyVar  = publicKeyVar;
        this.privateKeyVar = privateKeyVar;
        this.publicKeyPem  = publicKeyPem;
        this.privateKeyPem = privateKeyPem;
    }

    /** Name of the environment variable holding the public key. */
    public String publicKeyVar()  { return publicKeyVar; }

    /** Name of the environment variable holding the private key. */
    public String privateKeyVar() { return privateKeyVar; }

    // ── CryptoConfig ──────────────────────────────────────────────────────────

    /** Wrap {@code dek} with the receiver's public key (RSA-OAEP-SHA256). */
    @Override
    public byte[] wrapDek(int version, byte[] dek) throws CryptoError {
        AsymmetricKeyParameter pubKey = loadPublicKey();
        return rsaOaepEncrypt(pubKey, dek, new SecureRandom());
    }

    /** Unwrap {@code encDek} with the receiver's private key (RSA-OAEP-SHA256). */
    @Override
    public byte[] unwrapDek(int version, byte[] encDek) throws CryptoError {
        AsymmetricKeyParameter privKey = loadPrivateKey();
        return rsaOaepDecrypt(privKey, encDek);
    }

    // ── Key loading ───────────────────────────────────────────────────────────

    private AsymmetricKeyParameter loadPublicKey() throws CryptoError {
        String src = publicKeyPem != null ? publicKeyPem : readEnvVar(publicKeyVar);
        return parsePublicKey(src.trim());
    }

    private AsymmetricKeyParameter loadPrivateKey() throws CryptoError {
        String src = privateKeyPem != null ? privateKeyPem : readEnvVar(privateKeyVar);
        return parsePrivateKey(src.trim());
    }

    private String readEnvVar(String name) throws CryptoError.KeyNotFound {
        String val = System.getenv(name);
        if (val == null || val.isBlank()) {
            throw new CryptoError.KeyNotFound(name, "environment variable not set");
        }
        return val;
    }

    private static AsymmetricKeyParameter parsePublicKey(String s) throws CryptoError {
        try {
            byte[] der = toDer(s);
            SubjectPublicKeyInfo info = SubjectPublicKeyInfo.getInstance(der);
            return PublicKeyFactory.createKey(info);
        } catch (IOException e) {
            throw new CryptoError.InvalidKey("failed to parse public key: " + e.getMessage(), e);
        }
    }

    private static AsymmetricKeyParameter parsePrivateKey(String s) throws CryptoError {
        try {
            byte[] der = toDer(s);
            PrivateKeyInfo info = PrivateKeyInfo.getInstance(der);
            return PrivateKeyFactory.createKey(info);
        } catch (IOException e) {
            throw new CryptoError.InvalidKey("failed to parse private key: " + e.getMessage(), e);
        }
    }

    /** Auto-detects PEM vs stripped Base64-DER. */
    private static byte[] toDer(String s) throws CryptoError {
        if (s.startsWith("-----")) {
            try (PemReader reader = new PemReader(new StringReader(s))) {
                PemObject obj = reader.readPemObject();
                if (obj == null) throw new CryptoError.InvalidKey("PEM block was empty or unreadable");
                return obj.getContent();
            } catch (IOException e) {
                throw new CryptoError.InvalidKey("PEM parse error: " + e.getMessage(), e);
            }
        } else {
            try {
                return Base64.getDecoder().decode(s);
            } catch (IllegalArgumentException e) {
                throw new CryptoError.InvalidKey("Base64 DER decode failed: " + e.getMessage(), e);
            }
        }
    }

    // ── RSA-OAEP primitives ───────────────────────────────────────────────────

    private static byte[] rsaOaepEncrypt(
            AsymmetricKeyParameter pubKey, byte[] dek, SecureRandom rng) throws CryptoError {
        try {
            OAEPEncoding cipher = buildOaep(true, pubKey, rng);
            return cipher.processBlock(dek, 0, dek.length);
        } catch (InvalidCipherTextException e) {
            throw new CryptoError.DekWrapFailed(e.getMessage(), e);
        }
    }

    private static byte[] rsaOaepDecrypt(
            AsymmetricKeyParameter privKey, byte[] wrapped) throws CryptoError {
        try {
            OAEPEncoding cipher = buildOaep(false, privKey, null);
            return cipher.processBlock(wrapped, 0, wrapped.length);
        } catch (InvalidCipherTextException | RuntimeException e) {
            // RuntimeException covers DataLengthException thrown by BC when a wrong
            // key produces a decrypted block too large for OAEP unpadding.
            throw new CryptoError.DekUnwrapFailed(e.getMessage(), e);
        }
    }

    private static OAEPEncoding buildOaep(
            boolean forEncryption, CipherParameters params, SecureRandom rng) {
        AsymmetricBlockCipher rsa = new RSABlindedEngine();
        OAEPEncoding oaep = new OAEPEncoding(rsa,
                new org.bouncycastle.crypto.digests.SHA256Digest(),
                new org.bouncycastle.crypto.digests.SHA256Digest(),
                null);
        if (forEncryption && rng != null) {
            oaep.init(true, new ParametersWithRandom(params, rng));
        } else {
            oaep.init(forEncryption, params);
        }
        return oaep;
    }
}
