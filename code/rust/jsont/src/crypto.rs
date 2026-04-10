// =============================================================================
// crypto.rs — CryptoConfig trait, CryptoError, and PassthroughCryptoConfig
// =============================================================================
//
// The CryptoConfig abstraction is the single extension point for all
// encryption/decryption in the JsonT privacy pipeline.  Implementations
// plug in at three sites:
//
//   • stringify (write_row_with_schema) — encrypt plaintext values on wire write
//   • transform  (Decrypt operation)    — decrypt Encrypted values in a pipeline
//   • on-demand  (JsonTValue::decrypt)  — caller-driven field decryption
//
// PassthroughCryptoConfig is the identity implementation (bytes in = bytes out)
// and is used in tests and as a reference implementation.
// =============================================================================

use thiserror::Error;

// =============================================================================
// CryptoError
// =============================================================================

/// Error returned by any [`CryptoConfig`] operation.
#[derive(Debug, Error)]
pub enum CryptoError {
    /// The encryption operation failed.
    #[error("encrypt failed for field '{field}': {reason}")]
    EncryptFailed { field: String, reason: String },

    /// The decryption operation failed (bad key, corrupted ciphertext, etc.).
    #[error("decrypt failed for field '{field}': {reason}")]
    DecryptFailed { field: String, reason: String },

    /// The decrypted bytes are not valid UTF-8 and cannot be converted to a
    /// JsonT string value.
    #[error("decrypt produced invalid UTF-8 for field '{field}': {reason}")]
    InvalidUtf8 { field: String, reason: String },
}

// =============================================================================
// CryptoConfig
// =============================================================================

/// Pluggable encryption/decryption for sensitive (`~`) fields.
///
/// Implementations must be `Send + Sync` so they can be shared across threads
/// in the streaming validation pipeline.
///
/// # Contract
///
/// - `encrypt(field, plaintext)` → `ciphertext` bytes.  The output is stored
///   as `base64:<b64>` on the wire.
/// - `decrypt(field, ciphertext)` → `plaintext` bytes.  The input is the raw
///   bytes decoded from the `base64:` envelope (i.e. the output of the
///   corresponding `encrypt` call).
/// - Both operations **must be deterministic** for a given key; round-tripping
///   `encrypt` then `decrypt` must return the original `plaintext`.
///
/// # Field name
///
/// The `field` parameter lets implementations apply per-field key derivation
/// or access-control policies.  Implementations that do not need it may ignore
/// it.
pub trait CryptoConfig: Send + Sync {
    /// Encrypt `plaintext` bytes for the named field.
    fn encrypt(&self, field: &str, plaintext: &[u8]) -> Result<Vec<u8>, CryptoError>;

    /// Decrypt `ciphertext` bytes for the named field.
    fn decrypt(&self, field: &str, ciphertext: &[u8]) -> Result<Vec<u8>, CryptoError>;
}

// =============================================================================
// PassthroughCryptoConfig
// =============================================================================

/// Identity [`CryptoConfig`] — bytes are returned unchanged.
///
/// Useful for tests and for pipelines that only need the `base64:` wire format
/// without actual encryption (the bytes are stored as-is).
///
/// # Example
/// ```rust,ignore
/// use jsont::{PassthroughCryptoConfig, CryptoConfig};
///
/// let cfg = PassthroughCryptoConfig;
/// let ct = cfg.encrypt("ssn", b"123-45-6789").unwrap();
/// let pt = cfg.decrypt("ssn", &ct).unwrap();
/// assert_eq!(pt, b"123-45-6789");
/// ```
pub struct PassthroughCryptoConfig;

impl CryptoConfig for PassthroughCryptoConfig {
    fn encrypt(&self, _field: &str, plaintext: &[u8]) -> Result<Vec<u8>, CryptoError> {
        Ok(plaintext.to_vec())
    }

    fn decrypt(&self, _field: &str, ciphertext: &[u8]) -> Result<Vec<u8>, CryptoError> {
        Ok(ciphertext.to_vec())
    }
}
