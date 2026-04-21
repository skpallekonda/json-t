package io.github.datakore.jsont.crypto;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Objects;

/**
 * The single crypto handle for an encrypted JsonT stream.
 *
 * <p>A {@code CryptoContext} owns the plaintext DEK for the lifetime of the stream.
 * Callers must close it (or use try-with-resources) to zero the DEK from memory.
 *
 * <h2>Construction</h2>
 * <ul>
 *   <li>{@link #forEncrypt} — generates a fresh DEK, wraps it with the {@link CryptoConfig},
 *       and stores both the raw DEK and the wrapped {@code enc_dek}.</li>
 *   <li>{@link #forDecrypt} — unwraps {@code enc_dek} from the wire immediately (fail-fast)
 *       and stores the raw DEK.</li>
 * </ul>
 *
 * <h2>Field-level cipher dispatch</h2>
 * <p>The {@code algo_ver} nibble in the version field selects the symmetric cipher via
 * {@link FieldCipherRegistry}. Adding a new algorithm requires only a new
 * {@link FieldCipherHandler} and a registry entry — no changes here.
 * <ul>
 *   <li>1 → AES-256-GCM (BouncyCastle lightweight)</li>
 *   <li>2 → ChaCha20-Poly1305 (JCA built-in, Java 11+)</li>
 *   <li>3 → ASCON-128a (BouncyCastle lightweight)</li>
 * </ul>
 *
 * <h2>Version bit layout</h2>
 * <pre>
 *   bit 15       : reserved
 *   bits 14..11  : format_ver
 *   bits 10..7   : cert_ver
 *   bits  6..3   : algo_ver   (1=AES-GCM, 2=ChaCha20, 3=ASCON)
 *   bits  2..1   : unused
 *   bit   0      : kek_mode   (0=public-key, 1=ECDH)
 * </pre>
 */
public final class CryptoContext implements AutoCloseable {

    // ── Well-known version constants ─────────────────────────────────────────

    /** AES-256-GCM with RSA public-key-wrapped DEK. */
    public static final int VERSION_AES_PUBKEY    = 0x0008;
    /** AES-256-GCM with ECDH-derived KEK. */
    public static final int VERSION_AES_ECDH      = 0x0009;
    /** ChaCha20-Poly1305 with RSA public-key-wrapped DEK. */
    public static final int VERSION_CHACHA_PUBKEY = 0x0010;
    /** ChaCha20-Poly1305 with ECDH-derived KEK. */
    public static final int VERSION_CHACHA_ECDH   = 0x0011;
    /** ASCON-128a with RSA public-key-wrapped DEK. */
    public static final int VERSION_ASCON_PUBKEY  = 0x0018;
    /** ASCON-128a with ECDH-derived KEK. */
    public static final int VERSION_ASCON_ECDH    = 0x0019;

    private static final int DEK_LEN = 32;

    private final int    version;
    private final byte[] encDek;  // wrapped DEK for wire (EncryptHeader)
    private final byte[] dek;     // raw DEK, zeroed on close()

    private CryptoContext(int version, byte[] encDek, byte[] dek) {
        this.version = version;
        this.encDek  = encDek.clone();
        this.dek     = dek; // owned — zeroed on close
    }

    // ── Factory methods ───────────────────────────────────────────────────────

    /**
     * Create a context for writing an encrypted stream.
     *
     * <p>Generates a random 256-bit DEK, wraps it via
     * {@link CryptoConfig#wrapDek(int, byte[])}, and stores both for later use.
     *
     * @param algo    the field cipher algorithm
     * @param kekMode the DEK wrapping mode
     * @param config  the key-material provider (reads env vars at call time)
     * @return a ready-to-use context; caller must {@link #close()} it when done
     * @throws CryptoError if DEK wrapping fails
     */
    public static CryptoContext forEncrypt(AlgoVersion algo, KekMode kekMode, CryptoConfig config)
            throws CryptoError {
        Objects.requireNonNull(algo,    "algo must not be null");
        Objects.requireNonNull(kekMode, "kekMode must not be null");
        Objects.requireNonNull(config,  "config must not be null");

        int version = buildVersion(algo, kekMode);
        byte[] dek = new byte[DEK_LEN];
        new SecureRandom().nextBytes(dek);
        try {
            byte[] encDek = config.wrapDek(version, dek);
            return new CryptoContext(version, encDek, dek);
        } catch (CryptoError e) {
            Arrays.fill(dek, (byte) 0);
            throw e;
        }
    }

    /**
     * Create a context for reading an encrypted stream.
     *
     * <p>Unwraps {@code encDek} immediately via {@link CryptoConfig#unwrapDek(int, byte[])}
     * and validates the DEK length. Fails fast: if the key is wrong or the data is corrupt,
     * this throws before any rows are touched.
     *
     * @param version the version field read from the EncryptHeader row
     * @param encDek  the wrapped DEK bytes read from the EncryptHeader row
     * @param config  the key-material provider
     * @return a ready-to-use context; caller must {@link #close()} it when done
     * @throws CryptoError if DEK unwrapping or length validation fails
     */
    public static CryptoContext forDecrypt(int version, byte[] encDek, CryptoConfig config)
            throws CryptoError {
        if (version < 0 || version > 0xFFFF) {
            throw new IllegalArgumentException("version out of u16 range: " + version);
        }
        Objects.requireNonNull(encDek,  "encDek must not be null");
        Objects.requireNonNull(config,  "config must not be null");

        byte[] dek = config.unwrapDek(version, encDek);
        if (dek.length != DEK_LEN) {
            Arrays.fill(dek, (byte) 0);
            throw new CryptoError.DekUnwrapFailed(
                    "unexpected DEK length " + dek.length + ", expected " + DEK_LEN);
        }
        return new CryptoContext(version, encDek, dek);
    }

    // ── Field cipher dispatch ─────────────────────────────────────────────────

    /**
     * Encrypt one field value using the stream DEK and the algo selected by {@code algo_ver}.
     *
     * <p>Generates a fresh IV per call via {@link FieldCipherRegistry}. Never reuses an IV.
     *
     * @param plaintext the field value bytes
     * @return {@link EncryptedField} holding the per-field IV and ciphertext+tag
     * @throws CryptoError if encryption fails or {@code algo_ver} is unknown
     */
    public EncryptedField encryptField(byte[] plaintext) throws CryptoError {
        FieldCipherHandler handler = FieldCipherRegistry.find(algoVer());
        byte[] iv = new byte[handler.ivLen()];
        new SecureRandom().nextBytes(iv);
        byte[] ciphertext = handler.encrypt(dek, iv, plaintext);
        return new EncryptedField(iv, ciphertext);
    }

    /**
     * Decrypt one field value using the stream DEK and the algo selected by {@code algo_ver}.
     *
     * @param iv         the per-field IV stored in the payload
     * @param encContent the ciphertext+tag bytes
     * @return the plaintext bytes
     * @throws CryptoError if decryption or authentication fails
     */
    public byte[] decryptField(byte[] iv, byte[] encContent) throws CryptoError {
        return FieldCipherRegistry.find(algoVer()).decrypt(dek, iv, encContent);
    }

    /** Convenience overload — decrypts the iv/encContent carried by an {@link EncryptedField}. */
    public byte[] decryptField(EncryptedField ef) throws CryptoError {
        return decryptField(ef.iv(), ef.encContent());
    }

    // ── Wire accessors (for EncryptHeader serialisation) ─────────────────────

    /** The packed version field (fits in a u16). */
    public int version() { return version; }

    /** A defensive copy of the wrapped DEK bytes for writing the EncryptHeader row. */
    public byte[] encDek() { return encDek.clone(); }

    // ── Version bit-field accessors ───────────────────────────────────────────

    /** Algorithm nibble: bits 6..3. */
    public int algoVer()  { return (version >> 3) & 0x0F; }

    /** Certificate version nibble: bits 10..7. */
    public int certVer()  { return (version >> 7) & 0x0F; }

    /** KEK mode bit: bit 0 (0 = public-key, 1 = ECDH). */
    public int kekMode()  { return version & 0x01; }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    /** Zeros the plaintext DEK in memory. Call when the stream is done. */
    @Override
    public void close() {
        Arrays.fill(dek, (byte) 0);
    }

    // ── Version helpers ───────────────────────────────────────────────────────

    private static int buildVersion(AlgoVersion algo, KekMode kekMode) {
        return (algo.bits << 3) | kekMode.bit;
    }

    // ── Standard overrides ────────────────────────────────────────────────────

    @Override
    public String toString() {
        return "CryptoContext{version=0x" + Integer.toHexString(version)
                + ", encDekLen=" + encDek.length + "}";
    }
}
