// =============================================================================
// passthrough.rs — identity CryptoConfig (tests only)
// =============================================================================

use crate::{CryptoConfig, CryptoError};

/// Identity [`CryptoConfig`] — all crypto operations are identity functions.
///
/// - `wrap_dek` / `unwrap_dek` return the input bytes unchanged.
/// - `encrypt_field` returns a fixed all-zero IV and the plaintext as the
///   "ciphertext". `decrypt_field` returns `enc_content` unchanged.
///
/// This makes the per-field payload deterministic in tests (fixed IV), and
/// digest verification still passes because SHA-256(plaintext) is computed
/// over the original bytes before being stored in the payload.
///
/// **Not for production use.**
pub struct PassthroughCryptoConfig;

impl CryptoConfig for PassthroughCryptoConfig {
    fn wrap_dek(&self, _version: u16, dek: &[u8]) -> Result<Vec<u8>, CryptoError> {
        Ok(dek.to_vec())
    }

    fn unwrap_dek(&self, _version: u16, enc_dek: &[u8]) -> Result<Vec<u8>, CryptoError> {
        Ok(enc_dek.to_vec())
    }

    /// Returns `(iv=[0u8;12], enc_content=plaintext)` — deterministic for tests.
    fn encrypt_field(&self, _dek: &[u8], plaintext: &[u8]) -> Result<(Vec<u8>, Vec<u8>), CryptoError> {
        Ok((vec![0u8; 12], plaintext.to_vec()))
    }

    fn decrypt_field(&self, _dek: &[u8], _iv: &[u8], enc_content: &[u8]) -> Result<Vec<u8>, CryptoError> {
        Ok(enc_content.to_vec())
    }
}
