// =============================================================================
// error.rs — CryptoError variants
// =============================================================================

use thiserror::Error;

/// All errors produced by jsont-crypto operations.
#[derive(Debug, Error)]
pub enum CryptoError {
    /// Environment variable not set or key-store lookup returned nothing.
    #[error("key not found — env var '{var}': {reason}")]
    KeyNotFound { var: String, reason: String },

    /// Key material found but PEM/DER parsing failed, wrong key type, or wrong size.
    #[error("invalid key: {reason}")]
    InvalidKey { reason: String },

    /// The `algo_ver` bits in the version field name an unknown cipher.
    #[error("unsupported algorithm version {version:#06x}")]
    UnsupportedAlgorithm { version: u16 },

    /// The `kek_mode` bit in the version field names an unknown mode.
    #[error("unsupported KEK mode in version {version:#06x}")]
    UnsupportedKekMode { version: u16 },

    /// Encrypting the DEK with the KEK failed (wrapping step).
    #[error("DEK wrap failed: {reason}")]
    DekWrapFailed { reason: String },

    /// Decrypting `enc_dek` with the KEK failed (wrong key or corrupt bytes).
    #[error("DEK unwrap failed: {reason}")]
    DekUnwrapFailed { reason: String },

    /// AE encryption of a field value failed.
    #[error("encrypt failed for field '{field}': {reason}")]
    EncryptFailed { field: String, reason: String },

    /// AE decryption failed — auth tag mismatch or corrupt IV.
    #[error("decrypt failed for field '{field}': {reason}")]
    DecryptFailed { field: String, reason: String },

    /// Auth tag passed but SHA-256(plaintext) ≠ stored digest — tampered content.
    #[error("digest mismatch for field '{field}': content may have been tampered")]
    DigestMismatch { field: String },

    /// Per-field binary payload cannot be parsed (truncated or bad length fields).
    #[error("malformed payload for field '{field}': {reason}")]
    MalformedPayload { field: String, reason: String },
}
