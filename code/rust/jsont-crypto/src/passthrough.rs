// =============================================================================
// passthrough.rs — identity CryptoConfig (tests only)
// =============================================================================

use crate::{CryptoConfig, CryptoError};

/// Identity [`CryptoConfig`] — DEK wrapping is a no-op.
///
/// - `wrap_dek` / `unwrap_dek` return the input bytes unchanged.
/// - Field encryption / decryption go through the real [`CipherSession`] /
///   [`FieldCipherRegistry`] path selected by the version's `algo_ver` nibble.
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
    // open_session is inherited from the CryptoConfig default implementation.
}
