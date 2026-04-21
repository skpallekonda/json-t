// =============================================================================
// tests/ecdh_config_tests.rs — EcdhCryptoConfig (ECDH P-256 + HKDF-SHA256)
// =============================================================================
// Mirrors Java EcdhCryptoConfigTest.
//
// ECDH symmetry: ECDH(A_priv, B_pub) == ECDH(B_priv, A_pub), so:
//   - wrapping with (B_pub, A_priv) and unwrapping with (A_pub, B_priv) derives the same KEK.
//
// Coverage:
//   1. AES-GCM  DEK wrap/unwrap round-trip via ECDH
//   2. ChaCha20 DEK wrap/unwrap round-trip via ECDH
//   3. ASCON-128a DEK wrap/unwrap round-trip via ECDH
//   4. Wrong shared secret → DekUnwrapFailed
//   5. Full field encrypt/decrypt round-trip (ECDH mode)
//   6. Missing env var → KeyNotFound
// =============================================================================

use jsont_crypto::{CryptoConfig, CryptoContext, CryptoError, EcdhCryptoConfig};
use p256::SecretKey;
use p256::pkcs8::EncodePrivateKey;
use p256::pkcs8::spki::EncodePublicKey;
use pkcs8::LineEnding;

// ── Helpers ───────────────────────────────────────────────────────────────────

struct EcKeyPair {
    pub_der:  Vec<u8>,
    priv_pem: String,
}

fn generate_ec_pair() -> EcKeyPair {
    let priv_key = SecretKey::random(&mut rand::thread_rng());
    let pub_key  = priv_key.public_key();
    let pub_der  = pub_key.to_public_key_der().unwrap().to_vec();
    let priv_pem = priv_key.to_pkcs8_pem(LineEnding::LF).unwrap().to_string();
    EcKeyPair { pub_der, priv_pem }
}

/// A wraps for B: uses B's public key + A's private key → ECDH(a_priv, b_pub).
/// B unwraps:     uses A's public key + B's private key → ECDH(b_priv, a_pub).
/// ECDH symmetry guarantees both sides derive the same shared secret.
fn make_pair(a: &EcKeyPair, b: &EcKeyPair) -> (EcdhCryptoConfig, EcdhCryptoConfig) {
    let wrapper   = EcdhCryptoConfig::of_keys(b.pub_der.clone(), &a.priv_pem);
    let unwrapper = EcdhCryptoConfig::of_keys(a.pub_der.clone(), &b.priv_pem);
    (wrapper, unwrapper)
}

// =============================================================================
// 1 — AES-GCM DEK wrap/unwrap via ECDH
// =============================================================================

#[test]
fn ecdh_aes_gcm_wrap_unwrap_roundtrip() {
    let a = generate_ec_pair();
    let b = generate_ec_pair();
    let (wrapper, unwrapper) = make_pair(&a, &b);

    let dek: Vec<u8> = (1u8..=32).collect();
    let wrapped   = wrapper.wrap_dek(CryptoContext::VERSION_AES_ECDH, &dek).unwrap();
    let unwrapped = unwrapper.unwrap_dek(CryptoContext::VERSION_AES_ECDH, &wrapped).unwrap();
    assert_eq!(dek, unwrapped);
}

// =============================================================================
// 2 — ChaCha20-Poly1305 DEK wrap/unwrap via ECDH
// =============================================================================

#[test]
fn ecdh_chacha20_wrap_unwrap_roundtrip() {
    let a = generate_ec_pair();
    let b = generate_ec_pair();
    let (wrapper, unwrapper) = make_pair(&a, &b);

    let dek: Vec<u8> = (0u8..32).map(|i| i * 2 + 3).collect();
    let wrapped   = wrapper.wrap_dek(CryptoContext::VERSION_CHACHA_ECDH, &dek).unwrap();
    let unwrapped = unwrapper.unwrap_dek(CryptoContext::VERSION_CHACHA_ECDH, &wrapped).unwrap();
    assert_eq!(dek, unwrapped);
}

// =============================================================================
// 3 — ASCON-128a DEK wrap/unwrap via ECDH
// =============================================================================

#[test]
fn ecdh_ascon_wrap_unwrap_roundtrip() {
    let a = generate_ec_pair();
    let b = generate_ec_pair();
    let (wrapper, unwrapper) = make_pair(&a, &b);

    let dek: Vec<u8> = (0u8..32).map(|i| i + 7).collect();
    let wrapped   = wrapper.wrap_dek(CryptoContext::VERSION_ASCON_ECDH, &dek).unwrap();
    let unwrapped = unwrapper.unwrap_dek(CryptoContext::VERSION_ASCON_ECDH, &wrapped).unwrap();
    assert_eq!(dek, unwrapped);
}

// =============================================================================
// 4 — Wrong shared secret → DekUnwrapFailed
// =============================================================================

#[test]
fn unwrap_with_wrong_shared_secret_fails() {
    let a       = generate_ec_pair();
    let b       = generate_ec_pair();
    let wrapper = EcdhCryptoConfig::of_keys(b.pub_der.clone(), &a.priv_pem);

    let dek     = vec![0u8; 32];
    let wrapped = wrapper.wrap_dek(CryptoContext::VERSION_AES_ECDH, &dek).unwrap();

    // Unwrap with B's private key but B's own public key — derives wrong shared secret.
    let wrong_unwrapper = EcdhCryptoConfig::of_keys(b.pub_der.clone(), &b.priv_pem);
    let result = wrong_unwrapper.unwrap_dek(CryptoContext::VERSION_AES_ECDH, &wrapped);
    assert!(
        matches!(result, Err(CryptoError::DekUnwrapFailed { .. })),
        "expected DekUnwrapFailed with wrong shared secret, got {result:?}"
    );
}

// =============================================================================
// 5 — Full field encrypt/decrypt round-trip in ECDH mode
// =============================================================================

#[test]
fn ecdh_full_encrypt_decrypt_roundtrip() {
    let a = generate_ec_pair();
    let b = generate_ec_pair();
    let enc_cfg = EcdhCryptoConfig::of_keys(b.pub_der.clone(), &a.priv_pem);
    let dec_cfg = EcdhCryptoConfig::of_keys(a.pub_der.clone(), &b.priv_pem);

    let plaintext = b"ECDH protected secret";
    let dek       = vec![55u8; 32];

    let enc_dek     = enc_cfg.wrap_dek(CryptoContext::VERSION_AES_ECDH, &dek).unwrap();
    let ctx         = CryptoContext::new(CryptoContext::VERSION_AES_ECDH, enc_dek);
    let enc_session = enc_cfg.open_session(&ctx).unwrap();
    let ef          = enc_session.encrypt_field(plaintext).unwrap();

    let dec_session = dec_cfg.open_session(&ctx).unwrap();
    let recovered   = dec_session.decrypt_field(&ef.iv, &ef.enc_content).unwrap();
    assert_eq!(recovered, plaintext);
}

// =============================================================================
// 6 — Missing env var → KeyNotFound
// =============================================================================

#[test]
fn missing_env_var_returns_key_not_found() {
    let a   = generate_ec_pair();
    let cfg = EcdhCryptoConfig::new(a.pub_der, "JSONT_MISSING_ECDH_PRIV_XYZ");
    let dek = vec![0u8; 32];
    let result = cfg.wrap_dek(CryptoContext::VERSION_AES_ECDH, &dek);
    assert!(
        matches!(result, Err(CryptoError::KeyNotFound { .. })),
        "expected KeyNotFound, got {result:?}"
    );
}
