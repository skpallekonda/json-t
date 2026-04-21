// =============================================================================
// tests/env_config_tests.rs — EnvCryptoConfig (RSA-OAEP-SHA256)
// =============================================================================
// Mirrors Java PublicKeyCryptoConfigTest.
//
// Coverage:
//   1. wrapDek / unwrapDek round-trip
//   2. wrapDek is non-deterministic (RSA-OAEP is probabilistic)
//   3. unwrapDek with wrong private key → DekUnwrapFailed
//   4. Full field encrypt/decrypt via open_session round-trip
//   5. Missing env var → KeyNotFound
// =============================================================================

use std::sync::OnceLock;

use jsont_crypto::{CryptoConfig, CryptoContext, CryptoError, EnvCryptoConfig};

// RSA key generation is slow in debug mode — generate once per test binary run.
use pkcs8::{EncodePrivateKey, LineEnding};
use rsa::pkcs8::EncodePublicKey;
use rsa::{RsaPrivateKey, RsaPublicKey};

struct TestRsaKeys {
    pub_pem:  String,
    priv_pem: String,
}

static RSA_KEYS: OnceLock<TestRsaKeys> = OnceLock::new();

fn rsa_keys() -> &'static TestRsaKeys {
    RSA_KEYS.get_or_init(|| {
        let mut rng     = rand::thread_rng();
        let priv_key    = RsaPrivateKey::new(&mut rng, 2048).unwrap();
        let pub_key     = RsaPublicKey::from(&priv_key);
        let pub_pem     = pub_key.to_public_key_pem(LineEnding::LF).unwrap();
        let priv_pem    = priv_key.to_pkcs8_pem(LineEnding::LF).unwrap().to_string();
        TestRsaKeys { pub_pem, priv_pem }
    })
}

/// Build a config whose keys are supplied directly as PEM strings via env vars.
fn cfg_from_env(pub_var: &str, priv_var: &str, keys: &TestRsaKeys) -> EnvCryptoConfig {
    std::env::set_var(pub_var,  &keys.pub_pem);
    std::env::set_var(priv_var, &keys.priv_pem);
    EnvCryptoConfig::new(pub_var, priv_var)
}

// =============================================================================
// 1 — wrapDek / unwrapDek round-trip
// =============================================================================

#[test]
fn wrap_unwrap_dek_roundtrip() {
    let keys = rsa_keys();
    let cfg  = cfg_from_env("JSONT_TEST_PUB_1", "JSONT_TEST_PRIV_1", keys);

    let dek: Vec<u8> = (1u8..=32).collect();
    let wrapped   = cfg.wrap_dek(CryptoContext::VERSION_AES_PUBKEY, &dek).unwrap();
    let unwrapped = cfg.unwrap_dek(CryptoContext::VERSION_AES_PUBKEY, &wrapped).unwrap();
    assert_eq!(dek, unwrapped);
}

// =============================================================================
// 2 — RSA-OAEP is probabilistic: same plaintext → different ciphertext each time
// =============================================================================

#[test]
fn wrap_dek_produces_non_deterministic_output() {
    let keys = rsa_keys();
    let cfg  = cfg_from_env("JSONT_TEST_PUB_2", "JSONT_TEST_PRIV_2", keys);

    let dek = vec![0u8; 32];
    let enc1 = cfg.wrap_dek(CryptoContext::VERSION_AES_PUBKEY, &dek).unwrap();
    let enc2 = cfg.wrap_dek(CryptoContext::VERSION_AES_PUBKEY, &dek).unwrap();
    assert_ne!(enc1, enc2, "RSA-OAEP must produce different ciphertext each call");
}

// =============================================================================
// 3 — unwrapDek with wrong private key → DekUnwrapFailed
// =============================================================================

#[test]
fn unwrap_dek_with_wrong_key_fails() {
    let keys = rsa_keys();
    let cfg  = cfg_from_env("JSONT_TEST_PUB_3", "JSONT_TEST_PRIV_3", keys);

    // Generate a second, different key pair and use its private key to unwrap.
    let mut rng       = rand::thread_rng();
    let other_priv    = RsaPrivateKey::new(&mut rng, 2048).unwrap();
    let other_priv_pem = other_priv.to_pkcs8_pem(LineEnding::LF).unwrap().to_string();
    std::env::set_var("JSONT_TEST_PRIV_OTHER_3", &other_priv_pem);
    let wrong_cfg = EnvCryptoConfig::new("JSONT_TEST_PUB_3", "JSONT_TEST_PRIV_OTHER_3");

    let dek     = vec![0u8; 32];
    let wrapped = cfg.wrap_dek(CryptoContext::VERSION_AES_PUBKEY, &dek).unwrap();

    let result = wrong_cfg.unwrap_dek(CryptoContext::VERSION_AES_PUBKEY, &wrapped);
    assert!(
        matches!(result, Err(CryptoError::DekUnwrapFailed { .. })),
        "expected DekUnwrapFailed with wrong key, got {result:?}"
    );
}

// =============================================================================
// 4 — Full field encrypt/decrypt via open_session
// =============================================================================

#[test]
fn open_session_encrypt_decrypt_roundtrip() {
    let keys      = rsa_keys();
    let enc_cfg   = cfg_from_env("JSONT_TEST_PUB_4", "JSONT_TEST_PRIV_4", keys);
    let plaintext = b"sensitive data";

    // Encrypt: wrap a fresh DEK, open encrypt session.
    let dek: Vec<u8> = vec![42u8; 32];
    let enc_dek = enc_cfg.wrap_dek(CryptoContext::VERSION_AES_PUBKEY, &dek).unwrap();
    let ctx     = CryptoContext::new(CryptoContext::VERSION_AES_PUBKEY, enc_dek);
    let enc_session = enc_cfg.open_session(&ctx).unwrap();
    let ef      = enc_session.encrypt_field(plaintext).unwrap();

    // Decrypt: open decrypt session from the same CryptoContext.
    let dec_cfg     = cfg_from_env("JSONT_TEST_PUB_4", "JSONT_TEST_PRIV_4", keys);
    let dec_session = dec_cfg.open_session(&ctx).unwrap();
    let recovered   = dec_session.decrypt_field(&ef.iv, &ef.enc_content).unwrap();
    assert_eq!(recovered, plaintext);
}

// =============================================================================
// 5 — Missing env var → KeyNotFound
// =============================================================================

#[test]
fn missing_env_var_returns_key_not_found() {
    let cfg = EnvCryptoConfig::new("JSONT_MISSING_PUB_XYZ", "JSONT_MISSING_PRIV_XYZ");
    let dek = vec![0u8; 32];
    let result = cfg.wrap_dek(CryptoContext::VERSION_AES_PUBKEY, &dek);
    assert!(
        matches!(result, Err(CryptoError::KeyNotFound { .. })),
        "expected KeyNotFound, got {result:?}"
    );
}
