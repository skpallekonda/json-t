// =============================================================================
// ecdh_config.rs — EcdhCryptoConfig (ECDH P-256 + HKDF-SHA256 key agreement)
// =============================================================================
//
// Protocol (mirrors Java EcdhCryptoConfig):
//   1. ECDH agreement : sharedSecret = ECDH(host_priv, peer_pub)
//   2. KEK derivation : kek = HKDF-SHA256(sharedSecret, salt=version_bytes, info="jsont-dek")
//   3. DEK wrap       : encrypt DEK with FieldCipherRegistry cipher selected by algo_ver
//                       using the KEK; result is  iv || ciphertext+tag
//   4. DEK unwrap     : derive the same KEK, split enc_dek into iv + ciphertext,
//                       decrypt — auth failure → DekUnwrapFailed
//
// Key material:
//   Peer public key  — DER-encoded SubjectPublicKeyInfo (SEC1 EC P-256 public key)
//   Host private key — read from named env var at call time (PKCS#8 PEM or
//                      stripped Base64-DER), or supplied directly via `of_keys`.
// =============================================================================

use base64::Engine as _;
use hkdf::Hkdf;
use p256::pkcs8::{spki::DecodePublicKey, DecodePrivateKey};
use p256::{PublicKey, SecretKey};
use sha2::Sha256;
use zeroize::Zeroizing;

use crate::cipher::registry;
use crate::{CryptoConfig, CryptoError};

const KEK_LEN: usize  = 32;
const HKDF_INFO: &[u8] = b"jsont-dek";

/// [`CryptoConfig`] that wraps and unwraps the DEK using ECDH P-256 key agreement
/// followed by HKDF-SHA256 key derivation.
///
/// # Construction
/// - [`EcdhCryptoConfig::new`] — host private key read from a named env var at each call.
/// - [`EcdhCryptoConfig::of_keys`] — host private key supplied directly (tests / programmatic use).
pub struct EcdhCryptoConfig {
    /// DER-encoded SubjectPublicKeyInfo of the peer's EC P-256 public key.
    peer_public_key_der: Vec<u8>,
    /// Name of the env var holding the host's PKCS#8 EC private key.
    host_priv_key_var: String,
    /// Non-None only when the key is supplied directly via [`of_keys`].
    host_priv_key_pem: Option<String>,
}

impl EcdhCryptoConfig {
    /// Env-var-based constructor.  The host private key is read from the named
    /// environment variable at each [`CryptoConfig::wrap_dek`] / [`CryptoConfig::unwrap_dek`] call.
    pub fn new(peer_public_key_der: Vec<u8>, host_priv_key_var: impl Into<String>) -> Self {
        Self {
            peer_public_key_der,
            host_priv_key_var: host_priv_key_var.into(),
            host_priv_key_pem: None,
        }
    }

    /// Direct-key constructor — useful for tests and programmatic contexts where
    /// environment variables are unavailable.
    pub fn of_keys(peer_public_key_der: Vec<u8>, host_priv_key_pem: impl Into<String>) -> Self {
        Self {
            peer_public_key_der,
            host_priv_key_var: "<direct>".to_string(),
            host_priv_key_pem: Some(host_priv_key_pem.into()),
        }
    }

    fn derive_kek(&self, version: u16) -> Result<Zeroizing<Vec<u8>>, CryptoError> {
        let peer_pub  = self.parse_peer_public_key()?;
        let host_priv = self.load_host_private_key()?;

        let shared = p256::ecdh::diffie_hellman(
            host_priv.to_nonzero_scalar(),
            peer_pub.as_affine(),
        );
        let shared_bytes = Zeroizing::new(shared.raw_secret_bytes().to_vec());

        let salt = [(version >> 8) as u8, (version & 0xFF) as u8];
        let hk = Hkdf::<Sha256>::new(Some(&salt), &shared_bytes);
        let mut kek = vec![0u8; KEK_LEN];
        hk.expand(HKDF_INFO, &mut kek)
            .map_err(|_| CryptoError::DekWrapFailed { reason: "HKDF expand failed".to_string() })?;

        Ok(Zeroizing::new(kek))
    }

    fn parse_peer_public_key(&self) -> Result<PublicKey, CryptoError> {
        PublicKey::from_public_key_der(&self.peer_public_key_der)
            .map_err(|e| CryptoError::InvalidKey {
                reason: format!("failed to parse peer EC public key: {e}"),
            })
    }

    fn load_host_private_key(&self) -> Result<SecretKey, CryptoError> {
        let pem = match &self.host_priv_key_pem {
            Some(p) => p.trim().to_string(),
            None => std::env::var(&self.host_priv_key_var).map_err(|_| CryptoError::KeyNotFound {
                var:    self.host_priv_key_var.clone(),
                reason: "environment variable not set".to_string(),
            })?,
        };
        parse_private_key(pem.trim())
    }
}

impl CryptoConfig for EcdhCryptoConfig {
    /// Derive KEK via ECDH + HKDF-SHA256, then encrypt DEK with the algo
    /// selected by the `algo_ver` nibble in `version`.
    ///
    /// Returns `iv || ciphertext+tag`.
    fn wrap_dek(&self, version: u16, dek: &[u8]) -> Result<Vec<u8>, CryptoError> {
        let kek     = self.derive_kek(version)?;
        let algo_ver = ((version >> 3) & 0x0F) as u8;
        let handler = registry::find(algo_ver)?;

        let mut iv = vec![0u8; handler.iv_len()];
        rand::thread_rng().fill_bytes(&mut iv);

        let ciphertext = handler.encrypt(&kek, &iv, dek)
            .map_err(|e| CryptoError::DekWrapFailed { reason: e.to_string() })?;

        let mut result = Vec::with_capacity(iv.len() + ciphertext.len());
        result.extend_from_slice(&iv);
        result.extend_from_slice(&ciphertext);
        Ok(result)
    }

    /// Derive KEK via ECDH + HKDF-SHA256, split `enc_dek` into `iv` + ciphertext,
    /// then decrypt.
    ///
    /// AEAD authentication failure is mapped to `DekUnwrapFailed`.
    fn unwrap_dek(&self, version: u16, enc_dek: &[u8]) -> Result<Vec<u8>, CryptoError> {
        let kek      = self.derive_kek(version)?;
        let algo_ver = ((version >> 3) & 0x0F) as u8;
        let handler  = registry::find(algo_ver)?;
        let iv_len   = handler.iv_len();

        if enc_dek.len() <= iv_len {
            return Err(CryptoError::DekUnwrapFailed {
                reason: "enc_dek too short for ECDH mode".to_string(),
            });
        }
        let (iv, ciphertext) = enc_dek.split_at(iv_len);
        handler.decrypt(&kek, iv, ciphertext)
            .map_err(|e| CryptoError::DekUnwrapFailed {
                reason: format!("DEK mismatch: {e}"),
            })
    }
    // open_session is inherited from the CryptoConfig default implementation.
}

// ── Key parsing helpers ───────────────────────────────────────────────────────

fn parse_private_key(s: &str) -> Result<SecretKey, CryptoError> {
    if s.starts_with("-----") {
        SecretKey::from_pkcs8_pem(s)
            .map_err(|e| CryptoError::InvalidKey { reason: e.to_string() })
    } else {
        let der = base64::engine::general_purpose::STANDARD
            .decode(s)
            .map_err(|e| CryptoError::InvalidKey { reason: format!("base64 decode: {e}") })?;
        SecretKey::from_pkcs8_der(&der)
            .map_err(|e| CryptoError::InvalidKey { reason: e.to_string() })
    }
}

// needed for rand::RngCore::fill_bytes in wrap_dek
use rand::RngCore;
