// =============================================================================
// tests/cipher_session_tests.rs — CipherSession algo dispatch
// =============================================================================
// Mirrors Java CryptoContextTest.
//
// Coverage:
//   1. AES-GCM  encrypt/decrypt round-trip, iv_len = 12
//   2. ChaCha20 encrypt/decrypt round-trip, iv_len = 12
//   3. ASCON-128a encrypt/decrypt round-trip, iv_len = 16
//   4. IV is unique per call (AES-GCM and ASCON)
//   5. Version bit accessors: algo_ver, kek_mode on CryptoContext
//   6. Unknown algo_ver → UnsupportedAlgorithm error
// =============================================================================

use jsont_crypto::{CipherSession, CryptoConfig, CryptoContext, CryptoError, PassthroughCryptoConfig};

// ── Helpers ───────────────────────────────────────────────────────────────────

fn session_for(version: u16) -> CipherSession {
    PassthroughCryptoConfig
        .open_session(&CryptoContext::new(version, vec![0u8; 32]))
        .unwrap()
}

// =============================================================================
// 1 — AES-GCM round-trip
// =============================================================================

#[test]
fn aes_gcm_encrypt_decrypt_roundtrip() {
    let session   = session_for(CryptoContext::VERSION_AES_PUBKEY);
    let plaintext = b"hello world";
    let enc       = session.encrypt_field(plaintext).unwrap();
    assert_eq!(enc.iv.len(), 12, "AES-GCM iv must be 12 bytes");
    assert_ne!(enc.enc_content, plaintext, "output must differ from plaintext");
    let recovered = session.decrypt_field(&enc.iv, &enc.enc_content).unwrap();
    assert_eq!(recovered, plaintext);
}

// =============================================================================
// 2 — ChaCha20-Poly1305 round-trip
// =============================================================================

#[test]
fn chacha20_encrypt_decrypt_roundtrip() {
    let session   = session_for(CryptoContext::VERSION_CHACHA_PUBKEY);
    let plaintext = b"chacha test data";
    let enc       = session.encrypt_field(plaintext).unwrap();
    assert_eq!(enc.iv.len(), 12, "ChaCha20 iv must be 12 bytes");
    let recovered = session.decrypt_field(&enc.iv, &enc.enc_content).unwrap();
    assert_eq!(recovered, plaintext);
}

// =============================================================================
// 3 — ASCON-128a round-trip + iv_len = 16
// =============================================================================

#[test]
fn ascon128a_encrypt_decrypt_roundtrip() {
    let session   = session_for(CryptoContext::VERSION_ASCON_PUBKEY);
    let plaintext = b"ascon test data";
    let enc       = session.encrypt_field(plaintext).unwrap();
    assert_eq!(enc.iv.len(), 16, "ASCON-128a nonce must be 16 bytes");
    let recovered = session.decrypt_field(&enc.iv, &enc.enc_content).unwrap();
    assert_eq!(recovered, plaintext);
}

// =============================================================================
// 4 — IV uniqueness: two calls must produce different IVs
// =============================================================================

#[test]
fn aes_gcm_iv_is_unique_per_call() {
    let session = session_for(CryptoContext::VERSION_AES_PUBKEY);
    let enc1    = session.encrypt_field(b"data").unwrap();
    let enc2    = session.encrypt_field(b"data").unwrap();
    assert_ne!(enc1.iv, enc2.iv, "AES-GCM IVs must differ across calls");
}

#[test]
fn chacha20_iv_is_unique_per_call() {
    let session = session_for(CryptoContext::VERSION_CHACHA_PUBKEY);
    let enc1    = session.encrypt_field(b"data").unwrap();
    let enc2    = session.encrypt_field(b"data").unwrap();
    assert_ne!(enc1.iv, enc2.iv, "ChaCha20 IVs must differ across calls");
}

#[test]
fn ascon128a_iv_is_unique_per_call() {
    let session = session_for(CryptoContext::VERSION_ASCON_PUBKEY);
    let enc1    = session.encrypt_field(b"data").unwrap();
    let enc2    = session.encrypt_field(b"data").unwrap();
    assert_ne!(enc1.iv, enc2.iv, "ASCON IVs must differ across calls");
}

// =============================================================================
// 5 — CryptoContext version bit accessors
// =============================================================================

#[test]
fn version_bits_aes_pubkey() {
    let ctx = CryptoContext::new(CryptoContext::VERSION_AES_PUBKEY, vec![]);
    assert_eq!(ctx.algo_ver(),  1, "algo_ver must be 1 (AES-GCM)");
    assert_eq!(ctx.kek_mode(),  0, "kek_mode must be 0 (public key)");
}

#[test]
fn version_bits_aes_ecdh() {
    let ctx = CryptoContext::new(CryptoContext::VERSION_AES_ECDH, vec![]);
    assert_eq!(ctx.algo_ver(),  1, "algo_ver must be 1 (AES-GCM)");
    assert_eq!(ctx.kek_mode(),  1, "kek_mode must be 1 (ECDH)");
}

#[test]
fn version_bits_chacha_pubkey() {
    let ctx = CryptoContext::new(CryptoContext::VERSION_CHACHA_PUBKEY, vec![]);
    assert_eq!(ctx.algo_ver(),  2, "algo_ver must be 2 (ChaCha20)");
    assert_eq!(ctx.kek_mode(),  0);
}

#[test]
fn version_bits_ascon_pubkey() {
    let ctx = CryptoContext::new(CryptoContext::VERSION_ASCON_PUBKEY, vec![]);
    assert_eq!(ctx.algo_ver(),  3, "algo_ver must be 3 (ASCON)");
    assert_eq!(ctx.kek_mode(),  0);
}

#[test]
fn version_bits_chacha_ecdh() {
    let ctx = CryptoContext::new(CryptoContext::VERSION_CHACHA_ECDH, vec![]);
    assert_eq!(ctx.algo_ver(), 2);
    assert_eq!(ctx.kek_mode(), 1, "kek_mode must be 1 (ECDH)");
}

#[test]
fn version_bits_ascon_ecdh() {
    let ctx = CryptoContext::new(CryptoContext::VERSION_ASCON_ECDH, vec![]);
    assert_eq!(ctx.algo_ver(), 3);
    assert_eq!(ctx.kek_mode(), 1);
}

// =============================================================================
// 6 — Unknown algo_ver → UnsupportedAlgorithm
// =============================================================================

#[test]
fn unknown_algo_ver_encrypt_returns_error() {
    // algo_ver = 15 (reserved/unknown): version = (15 << 3) | 0 = 0x0078
    let unknown_version: u16 = 15 << 3;
    let session = PassthroughCryptoConfig
        .open_session(&CryptoContext::new(unknown_version, vec![0u8; 32]))
        .unwrap();
    let result = session.encrypt_field(b"x");
    assert!(
        matches!(result, Err(CryptoError::UnsupportedAlgorithm { .. })),
        "expected UnsupportedAlgorithm, got {result:?}"
    );
}
