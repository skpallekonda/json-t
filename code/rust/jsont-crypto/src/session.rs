// =============================================================================
// session.rs — CipherSession: per-stream DEK holder
// =============================================================================
// Design (D6-B): CryptoContext stays clean (no secret material, Clone + Send +
// Sync, safe to persist).  CipherSession is the short-lived wrapper that holds
// the plaintext DEK for the duration of one encrypted stream and zeros it on
// drop via `Zeroizing`.
// =============================================================================

use rand::RngCore;
use zeroize::Zeroizing;

use crate::cipher::registry;
use crate::CryptoError;

/// Short-lived handle for field-level encryption / decryption within one stream.
///
/// Constructed via [`CryptoConfig::open_session`] which unwraps the DEK once.
/// The DEK is zeroed automatically when this value is dropped.
///
/// # Lifetime
/// Create one `CipherSession` per stream; drop it when the stream is done.
/// Never clone or persist — the DEK lives only in this struct.
pub struct CipherSession {
    version: u16,
    dek: Zeroizing<Vec<u8>>,
}

/// Per-field encryption output: a random nonce and authenticated ciphertext.
#[derive(Debug)]
pub struct EncryptedField {
    /// The random nonce generated for this field invocation.
    pub iv: Vec<u8>,
    /// Ciphertext + authentication tag produced by the AEAD cipher.
    pub enc_content: Vec<u8>,
}

impl CipherSession {
    /// Construct a session from a plaintext DEK.
    ///
    /// `pub(crate)` — callers use [`CryptoConfig::open_session`] instead.
    pub(crate) fn new(version: u16, dek: Vec<u8>) -> Self {
        Self { version, dek: Zeroizing::new(dek) }
    }

    /// Encrypt one field value with a fresh random IV.
    ///
    /// The cipher is selected from [`registry`] by `algo_ver()`.
    pub fn encrypt_field(&self, plaintext: &[u8]) -> Result<EncryptedField, CryptoError> {
        let handler = registry::find(self.algo_ver())?;
        let mut iv  = vec![0u8; handler.iv_len()];
        rand::thread_rng().fill_bytes(&mut iv);
        let enc_content = handler.encrypt(&self.dek, &iv, plaintext)?;
        Ok(EncryptedField { iv, enc_content })
    }

    /// Decrypt one field value.
    pub fn decrypt_field(&self, iv: &[u8], enc_content: &[u8]) -> Result<Vec<u8>, CryptoError> {
        registry::find(self.algo_ver())?.decrypt(&self.dek, iv, enc_content)
    }

    /// The packed version word (fits in a u16).
    pub fn version(&self) -> u16 { self.version }

    /// Algorithm nibble: bits 6..3 of `version`.
    pub fn algo_ver(&self) -> u8 { ((self.version >> 3) & 0x0F) as u8 }
}
