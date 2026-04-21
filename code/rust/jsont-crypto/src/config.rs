// =============================================================================
// config.rs — CryptoConfig trait (stream-level DEK model)
// =============================================================================

use crate::session::CipherSession;
use crate::{CryptoContext, CryptoError};

/// Pluggable crypto contract for encrypted JsonT streams.
///
/// The stream-level DEK model:
/// - One DEK is generated per stream and wrapped once → written as `EncryptHeader`.
/// - On write: call `open_session` with a freshly created `CryptoContext` to get a
///   `CipherSession` that holds the plaintext DEK for field encryption.
/// - On read: call `open_session` with the `CryptoContext` parsed from the
///   `EncryptHeader` row to unwrap the DEK and get a `CipherSession` for decryption.
///
/// Implementations must be `Send + Sync` so they can be shared across threads.
pub trait CryptoConfig: Send + Sync {
    /// Wrap a raw DEK (plaintext) for writing to the `EncryptHeader` row.
    ///
    /// `version` carries the `algo_ver` and `kek_mode` bits so the implementation
    /// can select the correct key and wrapping algorithm.
    fn wrap_dek(&self, version: u16, dek: &[u8]) -> Result<Vec<u8>, CryptoError>;

    /// Unwrap `enc_dek` from the `EncryptHeader` → raw plaintext DEK bytes.
    fn unwrap_dek(&self, version: u16, enc_dek: &[u8]) -> Result<Vec<u8>, CryptoError>;

    /// Open a cipher session for `ctx`, unwrapping the DEK exactly once.
    ///
    /// The returned [`CipherSession`] holds the plaintext DEK and zeroes it on drop.
    /// Use it for all `encrypt_field` / `decrypt_field` calls within one stream.
    ///
    /// The default implementation calls `unwrap_dek` and constructs a `CipherSession`;
    /// override only when the unwrap step differs from the standard contract.
    fn open_session(&self, ctx: &CryptoContext) -> Result<CipherSession, CryptoError> {
        let dek = self.unwrap_dek(ctx.version, &ctx.enc_dek)?;
        Ok(CipherSession::new(ctx.version, dek))
    }
}
