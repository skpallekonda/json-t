// =============================================================================
// env_config.rs — EnvCryptoConfig (stream-level DEK model)
// =============================================================================
// CryptoConfig implementation that reads RSA key material from environment
// variables at call time. No key bytes are stored as struct fields.
//
// Per-stream layout (steps 5+):
//   EncryptHeader: one RSA-OAEP-SHA256 wrapped DEK  ← wrap_dek / unwrap_dek
//   Per-field:     [IV (12 B)] [ciphertext+tag]      ← encrypt_field / decrypt_field
//
// Supported key formats (auto-detected):
//   Full PEM  : value starts with "-----BEGIN"
//   Stripped  : Base64-encoded DER (headers and newlines removed)
// =============================================================================

use crate::{CryptoConfig, CryptoError};
use aes_gcm::aead::Aead;
use aes_gcm::{Aes256Gcm, Key, KeyInit, Nonce};
use base64::Engine as _;
use pkcs8::{DecodePrivateKey, DecodePublicKey};
use rand::RngCore;
use rsa::{Oaep, RsaPrivateKey, RsaPublicKey};
use sha2::Sha256;

const DEK_LEN: usize = 32; // AES-256 key size in bytes
const IV_LEN: usize  = 12; // AES-GCM 96-bit nonce

// =============================================================================
// EnvCryptoConfig
// =============================================================================

/// [`CryptoConfig`] that reads RSA key material from environment variables.
///
/// The struct holds only the *names* of the environment variables, not the key
/// bytes. Keys are read, used, and dropped within each call.
///
/// # Key format
/// Each env var must contain either a full PKCS#8 PEM block
/// (`-----BEGIN PUBLIC KEY-----` / `-----BEGIN PRIVATE KEY-----`) or the same
/// content as Base64-encoded DER with headers and newlines stripped.
///
/// # Example
/// ```rust,ignore
/// std::env::set_var("MY_PUB",  "-----BEGIN PUBLIC KEY-----\n...\n-----END PUBLIC KEY-----");
/// std::env::set_var("MY_PRIV", "-----BEGIN PRIVATE KEY-----\n...\n-----END PRIVATE KEY-----");
/// let cfg = EnvCryptoConfig::new("MY_PUB", "MY_PRIV");
/// ```
pub struct EnvCryptoConfig {
    /// Name of the env var holding the receiver's PKCS#8 public key (for DEK wrap).
    pub public_key_var: String,
    /// Name of the env var holding the receiver's PKCS#8 private key (for DEK unwrap).
    pub private_key_var: String,
}

impl EnvCryptoConfig {
    pub fn new(
        public_key_var: impl Into<String>,
        private_key_var: impl Into<String>,
    ) -> Self {
        Self {
            public_key_var:  public_key_var.into(),
            private_key_var: private_key_var.into(),
        }
    }

    fn load_public_key(&self) -> Result<RsaPublicKey, CryptoError> {
        let val = std::env::var(&self.public_key_var).map_err(|_| CryptoError::KeyNotFound {
            var:    self.public_key_var.clone(),
            reason: "environment variable not set".to_string(),
        })?;
        parse_public_key(val.trim())
    }

    fn load_private_key(&self) -> Result<RsaPrivateKey, CryptoError> {
        let val = std::env::var(&self.private_key_var).map_err(|_| CryptoError::KeyNotFound {
            var:    self.private_key_var.clone(),
            reason: "environment variable not set".to_string(),
        })?;
        parse_private_key(val.trim())
    }
}

// =============================================================================
// CryptoConfig impl
// =============================================================================

impl CryptoConfig for EnvCryptoConfig {
    /// Wrap the raw DEK with the receiver's public key (RSA-OAEP-SHA256).
    fn wrap_dek(&self, _version: u16, dek: &[u8]) -> Result<Vec<u8>, CryptoError> {
        let pub_key = self.load_public_key()?;
        let mut rng = rand::thread_rng();
        pub_key
            .encrypt(&mut rng, Oaep::new::<Sha256>(), dek)
            .map_err(|e| CryptoError::DekWrapFailed { reason: e.to_string() })
    }

    /// Unwrap `enc_dek` with the receiver's private key (RSA-OAEP-SHA256).
    fn unwrap_dek(&self, _version: u16, enc_dek: &[u8]) -> Result<Vec<u8>, CryptoError> {
        let priv_key = self.load_private_key()?;
        let dek = priv_key
            .decrypt(Oaep::new::<Sha256>(), enc_dek)
            .map_err(|e| CryptoError::DekUnwrapFailed { reason: e.to_string() })?;
        if dek.len() != DEK_LEN {
            return Err(CryptoError::DekUnwrapFailed {
                reason: format!("unexpected DEK length {}, expected {DEK_LEN}", dek.len()),
            });
        }
        Ok(dek)
    }

    /// Generate a fresh IV and encrypt `plaintext` with AES-256-GCM using `dek`.
    ///
    /// Returns `(iv, enc_content)` where `enc_content` includes the 16-byte
    /// authentication tag appended by AES-GCM.
    fn encrypt_field(&self, dek: &[u8], plaintext: &[u8]) -> Result<(Vec<u8>, Vec<u8>), CryptoError> {
        let mut iv_bytes = [0u8; IV_LEN];
        rand::thread_rng().fill_bytes(&mut iv_bytes);

        let key    = Key::<Aes256Gcm>::from_slice(dek);
        let cipher = Aes256Gcm::new(key);
        let nonce  = Nonce::from_slice(&iv_bytes);
        let enc_content = cipher
            .encrypt(nonce, plaintext)
            .map_err(|_| CryptoError::EncryptFailed {
                field:  String::new(),
                reason: "AES-256-GCM encryption failed".to_string(),
            })?;

        Ok((iv_bytes.to_vec(), enc_content))
    }

    /// Decrypt one field using AES-256-GCM with the supplied `dek` and `iv`.
    fn decrypt_field(&self, dek: &[u8], iv: &[u8], enc_content: &[u8]) -> Result<Vec<u8>, CryptoError> {
        let key    = Key::<Aes256Gcm>::from_slice(dek);
        let cipher = Aes256Gcm::new(key);
        let nonce  = Nonce::from_slice(iv);
        cipher
            .decrypt(nonce, enc_content)
            .map_err(|_| CryptoError::DecryptFailed {
                field:  String::new(),
                reason: "AES-256-GCM decryption failed (auth tag mismatch or corrupt data)".to_string(),
            })
    }
}

// =============================================================================
// Key parsing helpers
// =============================================================================

fn parse_public_key(s: &str) -> Result<RsaPublicKey, CryptoError> {
    if s.starts_with("-----") {
        RsaPublicKey::from_public_key_pem(s)
            .map_err(|e| CryptoError::InvalidKey { reason: e.to_string() })
    } else {
        let der = base64::engine::general_purpose::STANDARD
            .decode(s)
            .map_err(|e| CryptoError::InvalidKey { reason: format!("base64 decode: {e}") })?;
        RsaPublicKey::from_public_key_der(&der)
            .map_err(|e| CryptoError::InvalidKey { reason: e.to_string() })
    }
}

fn parse_private_key(s: &str) -> Result<RsaPrivateKey, CryptoError> {
    if s.starts_with("-----") {
        RsaPrivateKey::from_pkcs8_pem(s)
            .map_err(|e| CryptoError::InvalidKey { reason: e.to_string() })
    } else {
        let der = base64::engine::general_purpose::STANDARD
            .decode(s)
            .map_err(|e| CryptoError::InvalidKey { reason: format!("base64 decode: {e}") })?;
        RsaPrivateKey::from_pkcs8_der(&der)
            .map_err(|e| CryptoError::InvalidKey { reason: e.to_string() })
    }
}
