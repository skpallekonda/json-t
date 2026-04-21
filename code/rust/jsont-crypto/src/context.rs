// =============================================================================
// context.rs — CryptoContext
// =============================================================================

/// Derived from the `EncryptHeader` row at the top of an encrypted stream.
///
/// Safe to persist anywhere — contains only `version` and the *encrypted* DEK
/// (exactly as read from the wire). No plaintext key material ever lives here.
///
/// # CryptoContext lifetime
/// The caller obtains a `CryptoContext` from `try_parse_encrypt_header` and
/// owns its lifetime. It may be:
/// - Kept in memory for the duration of a session.
/// - Persisted to a store for later use (safe — no plaintext secrets).
/// - Shared across services.
///
/// At decrypt time, the caller supplies both `CryptoContext` and a
/// `CryptoConfig`. The raw DEK is derived ephemerally and dropped immediately
/// after the decryption call.
#[derive(Debug, Clone)]
pub struct CryptoContext {
    /// 16-bit version field from the EncryptHeader row.
    /// Encodes: format version (bits 14–11), cert version (bits 10–7),
    /// algorithm version (bits 6–3), unused (bits 2–1), KEK mode (bit 0).
    pub version: u16,

    /// Encrypted DEK as decoded from the header row (raw bytes, not base64).
    pub enc_dek: Vec<u8>,
}

impl CryptoContext {
    /// Construct from parsed header fields.
    pub fn new(version: u16, enc_dek: Vec<u8>) -> Self {
        Self { version, enc_dek }
    }

    /// 4-bit algorithm version extracted from bits 6–3.
    /// 1 = AES-256-GCM, 2 = ChaCha20-Poly1305, 3 = ASCON.
    pub fn algo_ver(&self) -> u8 { ((self.version >> 3) & 0x0F) as u8 }

    /// 4-bit receiver certificate version extracted from bits 10–7.
    /// 0 = unversioned.
    pub fn cert_ver(&self) -> u8 { ((self.version >> 7) & 0x0F) as u8 }

    /// KEK mode bit (bit 0).
    /// 0 = receiver public key, 1 = ECDH pre-established shared key.
    pub fn kek_mode(&self) -> u8 { (self.version & 0x01) as u8 }

    // ── Pre-built version constants ──────────────────────────────────────────

    /// AES-256-GCM + receiver public key (format=0, cert=0, algo=1, kek=0).
    /// Bit layout: bits 6-3 = 0001 → value = 0x0008.
    pub const VERSION_AES_PUBKEY: u16 = 0x0008;

    /// AES-256-GCM + ECDH pre-established key (format=0, cert=0, algo=1, kek=1).
    pub const VERSION_AES_ECDH: u16 = 0x0009;

    /// ChaCha20-Poly1305 + receiver public key (algo=2 → bits 6-3 = 0010 → 0x0010).
    pub const VERSION_CHACHA_PUBKEY: u16 = 0x0010;

    /// ASCON + receiver public key (algo=3 → bits 6-3 = 0011 → 0x0018).
    pub const VERSION_ASCON_PUBKEY: u16 = 0x0018;

    /// ChaCha20-Poly1305 + ECDH pre-established key (algo=2, kek=1 → 0x0011).
    pub const VERSION_CHACHA_ECDH: u16 = 0x0011;

    /// ASCON + ECDH pre-established key (algo=3, kek=1 → 0x0019).
    pub const VERSION_ASCON_ECDH: u16 = 0x0019;
}
