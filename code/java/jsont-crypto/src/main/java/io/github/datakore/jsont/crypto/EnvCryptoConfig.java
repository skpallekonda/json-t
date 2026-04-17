package io.github.datakore.jsont.crypto;

import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.crypto.AsymmetricBlockCipher;
import org.bouncycastle.crypto.CipherParameters;
import org.bouncycastle.crypto.InvalidCipherTextException;
import org.bouncycastle.crypto.engines.AESEngine;
import org.bouncycastle.crypto.engines.RSABlindedEngine;
import org.bouncycastle.crypto.encodings.OAEPEncoding;
import org.bouncycastle.crypto.modes.GCMBlockCipher;
import org.bouncycastle.crypto.modes.GCMModeCipher;
import org.bouncycastle.crypto.params.AEADParameters;
import org.bouncycastle.crypto.params.AsymmetricKeyParameter;
import org.bouncycastle.crypto.params.KeyParameter;
import org.bouncycastle.crypto.util.PrivateKeyFactory;
import org.bouncycastle.crypto.util.PublicKeyFactory;
import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemReader;

import java.io.IOException;
import java.io.StringReader;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * {@link CryptoConfig} that reads RSA key material from environment variables
 * (stream-level DEK model).
 *
 * <h2>Stream-level layout</h2>
 * <ul>
 *   <li>{@link #wrapDek} — RSA-OAEP-SHA256 wraps the raw DEK with the receiver's public key.</li>
 *   <li>{@link #unwrapDek} — RSA-OAEP-SHA256 unwraps with the receiver's private key.</li>
 *   <li>{@link #encryptField} — AES-256-GCM with a fresh IV per field.</li>
 *   <li>{@link #decryptField} — AES-256-GCM using the supplied IV and ciphertext+tag.</li>
 * </ul>
 *
 * <h2>Key format</h2>
 * <p>Each environment variable must contain either a full PKCS#8 PEM block
 * ({@code -----BEGIN PUBLIC KEY-----} / {@code -----BEGIN PRIVATE KEY-----})
 * or the same content as Base64-encoded DER with headers and newlines stripped.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 *   // Keys set as environment variables before the JVM starts, or:
 *   CryptoConfig cfg = new EnvCryptoConfig("JSONT_PUBLIC_KEY", "JSONT_PRIVATE_KEY");
 * }</pre>
 */
public final class EnvCryptoConfig implements CryptoConfig {

    private static final int DEK_LEN      = 32;  // AES-256 key size in bytes
    private static final int IV_LEN       = 12;  // AES-GCM 96-bit nonce
    private static final int GCM_TAG_BITS = 128;

    /** Name of the env var holding the receiver's PKCS#8 public key (for DEK wrap). */
    private final String publicKeyVar;
    /** Name of the env var holding the receiver's PKCS#8 private key (for DEK unwrap). */
    private final String privateKeyVar;

    /**
     * @param publicKeyVar  environment variable name containing the PKCS#8 public key
     * @param privateKeyVar environment variable name containing the PKCS#8 private key
     */
    public EnvCryptoConfig(String publicKeyVar, String privateKeyVar) {
        this.publicKeyVar  = publicKeyVar;
        this.privateKeyVar = privateKeyVar;
    }

    /** Name of the environment variable holding the public key. */
    public String publicKeyVar()  { return publicKeyVar; }

    /** Name of the environment variable holding the private key. */
    public String privateKeyVar() { return privateKeyVar; }

    // ── CryptoConfig ─────────────────────────────────────────────────────────

    /** Wrap {@code dek} with the receiver's public key (RSA-OAEP-SHA256). */
    @Override
    public byte[] wrapDek(int version, byte[] dek) throws CryptoError {
        AsymmetricKeyParameter pubKey = loadPublicKey();
        SecureRandom rng = new SecureRandom();
        return rsaOaepEncrypt(pubKey, dek, rng);
    }

    /** Unwrap {@code encDek} with the receiver's private key (RSA-OAEP-SHA256). */
    @Override
    public byte[] unwrapDek(int version, byte[] encDek) throws CryptoError {
        AsymmetricKeyParameter privKey = loadPrivateKey();
        byte[] dek = rsaOaepDecrypt(privKey, encDek);
        if (dek.length != DEK_LEN) {
            throw new CryptoError.DekUnwrapFailed(
                    "unexpected DEK length " + dek.length + ", expected " + DEK_LEN);
        }
        return dek;
    }

    /**
     * Generate a fresh IV and AES-256-GCM encrypt {@code plaintext} with {@code dek}.
     *
     * <p>Returns an {@link EncryptedField} holding the IV and ciphertext+tag.
     */
    @Override
    public EncryptedField encryptField(byte[] dek, byte[] plaintext) throws CryptoError {
        SecureRandom rng = new SecureRandom();
        byte[] iv = new byte[IV_LEN];
        rng.nextBytes(iv);
        byte[] encContent = aesGcmEncrypt(dek, iv, plaintext);
        return new EncryptedField(iv, encContent);
    }

    /** AES-256-GCM decrypt using the supplied {@code dek}, {@code iv}, and {@code encContent}. */
    @Override
    public byte[] decryptField(byte[] dek, byte[] iv, byte[] encContent) throws CryptoError {
        return aesGcmDecrypt(dek, iv, encContent);
    }

    // ── Key loading ───────────────────────────────────────────────────────────

    private AsymmetricKeyParameter loadPublicKey() throws CryptoError {
        String val = readEnvVar(publicKeyVar);
        return parsePublicKey(val.trim());
    }

    private AsymmetricKeyParameter loadPrivateKey() throws CryptoError {
        String val = readEnvVar(privateKeyVar);
        return parsePrivateKey(val.trim());
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

    /**
     * Auto-detects PEM vs stripped Base64-DER.
     * Full PEM starts with "-----BEGIN"; otherwise treated as Base64-encoded DER.
     */
    private static byte[] toDer(String s) throws CryptoError {
        if (s.startsWith("-----")) {
            try (var reader = new PemReader(new StringReader(s))) {
                PemObject obj = reader.readPemObject();
                if (obj == null) {
                    throw new CryptoError.InvalidKey("PEM block was empty or unreadable");
                }
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

    // ── Crypto primitives ─────────────────────────────────────────────────────

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
        } catch (InvalidCipherTextException e) {
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
            oaep.init(true, new org.bouncycastle.crypto.params.ParametersWithRandom(params, rng));
        } else {
            oaep.init(forEncryption, params);
        }
        return oaep;
    }

    private static byte[] aesGcmEncrypt(byte[] dek, byte[] iv, byte[] plaintext)
            throws CryptoError {
        try {
            GCMModeCipher gcm = GCMBlockCipher.newInstance(AESEngine.newInstance());
            gcm.init(true, new AEADParameters(new KeyParameter(dek), GCM_TAG_BITS, iv));
            byte[] out = new byte[gcm.getOutputSize(plaintext.length)];
            int n = gcm.processBytes(plaintext, 0, plaintext.length, out, 0);
            gcm.doFinal(out, n);
            return out;
        } catch (Exception e) {
            throw new CryptoError.EncryptFailed("", "AES-GCM encryption failed: " + e.getMessage(), e);
        }
    }

    private static byte[] aesGcmDecrypt(byte[] dek, byte[] iv, byte[] ciphertext)
            throws CryptoError {
        try {
            GCMModeCipher gcm = GCMBlockCipher.newInstance(AESEngine.newInstance());
            gcm.init(false, new AEADParameters(new KeyParameter(dek), GCM_TAG_BITS, iv));
            byte[] out = new byte[gcm.getOutputSize(ciphertext.length)];
            int n = gcm.processBytes(ciphertext, 0, ciphertext.length, out, 0);
            gcm.doFinal(out, n);
            return out;
        } catch (Exception e) {
            throw new CryptoError.DecryptFailed("",
                    "AES-GCM decryption failed (auth tag mismatch or corrupt data)", e);
        }
    }

    // ── Zero-on-close helper ──────────────────────────────────────────────────

    /** Zero a DEK array. Call after the DEK is no longer needed. */
    public static void zeroDek(byte[] dek) {
        Arrays.fill(dek, (byte) 0);
    }
}
