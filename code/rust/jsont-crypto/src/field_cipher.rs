// =============================================================================
// field_cipher.rs — Strategy interface for symmetric AEAD cipher algorithms
// =============================================================================

use crate::CryptoError;

/// Strategy interface for one symmetric AEAD cipher algorithm.
///
/// Implementations throw only `EncryptFailed` / `DecryptFailed`; callers
/// are responsible for re-wrapping into context-specific error variants.
pub(crate) trait FieldCipherHandler: Send + Sync {
    /// The `algo_ver` nibble this handler owns (matches bits 6..3 of the version word).
    fn algo_ver(&self) -> u8;

    /// The nonce length in bytes required by this cipher.
    fn iv_len(&self) -> usize;

    /// Encrypt `plaintext` with `key` and `iv`; returns ciphertext + auth tag.
    fn encrypt(&self, key: &[u8], iv: &[u8], plaintext: &[u8]) -> Result<Vec<u8>, CryptoError>;

    /// Decrypt `ciphertext` (including auth tag) with `key` and `iv`; returns plaintext.
    fn decrypt(&self, key: &[u8], iv: &[u8], ciphertext: &[u8]) -> Result<Vec<u8>, CryptoError>;
}
