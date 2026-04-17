package io.github.datakore.jsont.crypto;

import java.util.Arrays;
import java.util.Objects;

/**
 * Holds the stream-level version tag and the RSA-wrapped (encrypted) DEK.
 *
 * <p>A {@code CryptoContext} is constructed by parsing the
 * {@code EncryptHeader} row at the top of an encrypted JsonT stream.
 * It is safe to store: the DEK is kept in its wrapped (encrypted) form —
 * no plaintext key material is ever held in memory beyond the lifetime of a
 * single encrypt/decrypt call.
 *
 * <h2>Version bit layout</h2>
 * <pre>
 *   bit 15       : reserved
 *   bits 14..11  : format_ver  (0 = current)
 *   bits 10..7   : cert_ver    (0 = no cert)
 *   bits  6..3   : algo_ver    (1=AES-GCM, 2=ChaCha20, 3=ASCON)
 *   bits  2..1   : unused
 *   bit  0       : kek_mode    (0=public-key, 1=ECDH)
 * </pre>
 *
 * <h2>Well-known version constants</h2>
 * <pre>
 *   VERSION_AES_PUBKEY   = 0x0008   (AES-GCM, RSA public-key KEK)
 *   VERSION_AES_ECDH     = 0x0009   (AES-GCM, ECDH KEK)
 *   VERSION_CHACHA_PUBKEY= 0x0010   (ChaCha20, RSA public-key KEK)
 *   VERSION_ASCON_PUBKEY = 0x0018   (ASCON, RSA public-key KEK)
 * </pre>
 */
public final class CryptoContext {

    /** AES-256-GCM with RSA public-key-wrapped DEK. */
    public static final int VERSION_AES_PUBKEY    = 0x0008;
    /** AES-256-GCM with ECDH-derived DEK. */
    public static final int VERSION_AES_ECDH      = 0x0009;
    /** ChaCha20-Poly1305 with RSA public-key-wrapped DEK. */
    public static final int VERSION_CHACHA_PUBKEY = 0x0010;
    /** ASCON-128 with RSA public-key-wrapped DEK. */
    public static final int VERSION_ASCON_PUBKEY  = 0x0018;

    /** Version field (u16 range: 0..65535). */
    private final int version;

    /** RSA-OAEP-wrapped DEK bytes (never the plaintext DEK). */
    private final byte[] encDek;

    /**
     * Constructs a {@code CryptoContext}.
     *
     * @param version the version tag (u16 range)
     * @param encDek  the RSA-OAEP-wrapped DEK; copied defensively
     */
    public CryptoContext(int version, byte[] encDek) {
        if (version < 0 || version > 0xFFFF) {
            throw new IllegalArgumentException("version must be in u16 range [0, 65535], got " + version);
        }
        Objects.requireNonNull(encDek, "encDek must not be null");
        this.version = version;
        this.encDek  = encDek.clone();
    }

    /** The version tag (fits in a u16). */
    public int version() { return version; }

    /**
     * Returns a defensive copy of the wrapped DEK bytes.
     *
     * <p>These are the RSA-OAEP-encrypted bytes — never the plaintext key.
     */
    public byte[] encDek() { return encDek.clone(); }

    // ── Version bit-field accessors ──────────────────────────────────────────

    /** Algorithm version nibble (bits 6..3). */
    public int algoVer() { return (version >> 3) & 0x0F; }

    /** Certificate version nibble (bits 10..7). */
    public int certVer() { return (version >> 7) & 0x0F; }

    /** KEK mode bit (bit 0): 0 = public-key, 1 = ECDH. */
    public int kekMode() { return version & 0x01; }

    // ── Standard overrides ───────────────────────────────────────────────────

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof CryptoContext other)) return false;
        return version == other.version && Arrays.equals(encDek, other.encDek);
    }

    @Override
    public int hashCode() {
        return 31 * Integer.hashCode(version) + Arrays.hashCode(encDek);
    }

    @Override
    public String toString() {
        return "CryptoContext{version=0x" + Integer.toHexString(version)
                + ", encDekLen=" + encDek.length + "}";
    }
}
