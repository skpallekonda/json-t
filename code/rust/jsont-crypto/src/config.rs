// =============================================================================
// config.rs — CryptoConfig trait (stream-level DEK model)
// =============================================================================

use crate::CryptoError;

/// Pluggable crypto contract for encrypted JsonT streams.
///
/// The stream-level DEK model:
/// - One DEK is generated per stream and wrapped once → written as `EncryptHeader`.
/// - Every sensitive field is encrypted with that shared DEK (`encrypt_field`).
/// - On read, the DEK is unwrapped once (`unwrap_dek`) and reused for all fields.
///
/// Implementations read key material from the environment at call time — no key
/// bytes should be stored as struct fields. `CryptoConfig` objects are safe to
/// keep alive across calls.
///
/// All implementations must be `Send + Sync` so they can be shared across threads
/// in the streaming validation pipeline.
pub trait CryptoConfig: Send + Sync {
    /// Wrap a raw DEK (plaintext) for writing to the `EncryptHeader` row.
    ///
    /// `version` carries the `algo_ver` and `kek_mode` bits so the implementation
    /// can choose the correct key and algorithm.
    fn wrap_dek(&self, version: u16, dek: &[u8]) -> Result<Vec<u8>, CryptoError>;

    /// Unwrap `enc_dek` from the `EncryptHeader` → raw plaintext DEK.
    ///
    /// The returned DEK must be zeroed by the caller after use.
    fn unwrap_dek(&self, version: u16, enc_dek: &[u8]) -> Result<Vec<u8>, CryptoError>;

    /// Encrypt one field value with the shared DEK.
    ///
    /// Returns `(iv, enc_content)`. The IV must be freshly generated per call —
    /// reusing an IV with the same DEK under AES-GCM is catastrophic. The
    /// `enc_content` includes the authentication tag for AEAD ciphers.
    fn encrypt_field(&self, dek: &[u8], plaintext: &[u8]) -> Result<(Vec<u8>, Vec<u8>), CryptoError>;

    /// Decrypt one field value using the shared DEK, the per-field IV, and the
    /// raw ciphertext+tag bytes.
    fn decrypt_field(&self, dek: &[u8], iv: &[u8], enc_content: &[u8]) -> Result<Vec<u8>, CryptoError>;
}
