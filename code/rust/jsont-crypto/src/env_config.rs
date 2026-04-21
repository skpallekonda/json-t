// =============================================================================
// env_config.rs — EnvCryptoConfig (RSA public-key DEK wrapping)
// =============================================================================
// CryptoConfig implementation that reads RSA key material from environment
// variables at call time.  No key bytes are stored as struct fields.
//
// Supported key formats (auto-detected):
//   Full PEM  : value starts with "-----BEGIN"
//   Stripped  : Base64-encoded DER (headers and newlines removed)
// =============================================================================

use base64::Engine as _;
use pkcs8::{DecodePrivateKey, DecodePublicKey};
use rsa::{Oaep, RsaPrivateKey, RsaPublicKey};
use sha2::Sha256;

use crate::{CryptoConfig, CryptoError};

const DEK_LEN: usize = 32;

/// [`CryptoConfig`] that wraps and unwraps the DEK using RSA-OAEP-SHA256.
///
/// Keys are read from named environment variables at each call — no key bytes
/// stored in the struct.
///
/// # Key format
/// Each env var must contain either a full PKCS#8 PEM block or the same
/// content as Base64-encoded DER with headers and newlines stripped.
pub struct EnvCryptoConfig {
    /// Name of the env var holding the receiver's PKCS#8 public key.
    pub public_key_var: String,
    /// Name of the env var holding the receiver's PKCS#8 private key.
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
    // open_session is inherited from the CryptoConfig default implementation.
}

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
