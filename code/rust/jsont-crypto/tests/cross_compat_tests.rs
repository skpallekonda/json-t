// =============================================================================
// tests/cross_compat_tests.rs — Rust ↔ Java cross-compatibility
// =============================================================================
// Each language generates fixture files and verifies the other language's fixtures.
//
// Convention (ECDH roles):
//   Rust = party A: host_priv = ec_a_private.pem, peer_pub = ec_b_public.der
//   Java = party B: host_priv = ec_b_private.pem, peer_pub = ec_a_public.der
//   ECDH symmetry: ECDH(a_priv, b_pub) == ECDH(b_priv, a_pub) ✓
//
// Run order (CI):
//   cargo test --test cross_compat_tests   → writes fixtures/rust_*.txt
//   mvn test -Dtest=CrossCompatTest        → reads Rust fixtures, writes fixtures/java_*.txt
//   cargo test --test cross_compat_tests   → reads Java fixtures (verify step)
//
// Algorithm: ChaCha20-Poly1305 (VERSION_CHACHA_PUBKEY=0x0010, VERSION_CHACHA_ECDH=0x0011)
// =============================================================================

use std::collections::HashMap;
use std::path::{Path, PathBuf};

use jsont_crypto::{CryptoConfig, CryptoContext, EcdhCryptoConfig, EnvCryptoConfig};

// ── Path helpers ──────────────────────────────────────────────────────────────

fn repo_root() -> PathBuf {
    Path::new(env!("CARGO_MANIFEST_DIR"))
        .join("../../..")
        .canonicalize()
        .unwrap()
}

fn keys_dir() -> PathBuf { repo_root().join("code/cross-compat/keys") }

fn fixtures_dir() -> PathBuf {
    let d = repo_root().join("code/cross-compat/fixtures");
    std::fs::create_dir_all(&d).ok();
    d
}

// ── Fixture I/O ───────────────────────────────────────────────────────────────

fn write_fixture(path: &Path, fields: &[(&str, &str)]) {
    let content = fields.iter()
        .map(|(k, v)| format!("{}={}", k, v))
        .collect::<Vec<_>>()
        .join("\n");
    std::fs::write(path, content).unwrap();
}

fn read_fixture(path: &Path) -> HashMap<String, String> {
    std::fs::read_to_string(path).unwrap()
        .lines()
        .filter_map(|line| {
            let (k, v) = line.split_once('=')?;
            Some((k.to_string(), v.to_string()))
        })
        .collect()
}

fn to_hex(bytes: &[u8]) -> String {
    bytes.iter().map(|b| format!("{:02x}", b)).collect()
}

fn from_hex(s: &str) -> Vec<u8> {
    (0..s.len())
        .step_by(2)
        .map(|i| u8::from_str_radix(&s[i..i + 2], 16).unwrap())
        .collect()
}

// ── RSA + ChaCha20 ────────────────────────────────────────────────────────────

/// Rust encrypts with RSA public key + ChaCha20; writes fixture for Java to decrypt.
#[test]
fn gen_rust_rsa_chacha20_fixture() {
    let keys = keys_dir();
    let pub_pem  = std::fs::read_to_string(keys.join("rsa_2048_public.pem")).unwrap();
    let priv_pem = std::fs::read_to_string(keys.join("rsa_2048_private.pem")).unwrap();

    std::env::set_var("CC_RSA_PUB_GEN",  &pub_pem);
    std::env::set_var("CC_RSA_PRIV_GEN", &priv_pem);
    let cfg = EnvCryptoConfig::new("CC_RSA_PUB_GEN", "CC_RSA_PRIV_GEN");

    let plaintext: &[u8] = b"cross-compat: rust->java RSA+ChaCha20";
    let dek: Vec<u8> = (1u8..=32).collect();

    let enc_dek = cfg.wrap_dek(CryptoContext::VERSION_CHACHA_PUBKEY, &dek).unwrap();
    let ctx     = CryptoContext::new(CryptoContext::VERSION_CHACHA_PUBKEY, enc_dek.clone());
    let session = cfg.open_session(&ctx).unwrap();
    let ef      = session.encrypt_field(plaintext).unwrap();

    // Verify own round-trip before writing.
    let dec = cfg.open_session(&ctx).unwrap().decrypt_field(&ef.iv, &ef.enc_content).unwrap();
    assert_eq!(dec.as_slice(), plaintext);

    let fixture_path = fixtures_dir().join("rust_rsa_chacha20.txt");
    if !fixture_path.exists() {
        write_fixture(&fixture_path, &[
            ("generator",    "rust"),
            ("scenario",     "rsa_chacha20"),
            ("version",      &CryptoContext::VERSION_CHACHA_PUBKEY.to_string()),
            ("plaintext_hex",&to_hex(plaintext)),
            ("enc_dek_hex",  &to_hex(&enc_dek)),
            ("iv_hex",       &to_hex(&ef.iv)),
            ("ciphertext_hex",&to_hex(&ef.enc_content)),
        ]);
        eprintln!("Generated rust_rsa_chacha20.txt — commit this file");
    }
}

/// Rust decrypts the fixture written by Java (RSA + ChaCha20).
/// Skipped gracefully when the Java fixture does not exist yet.
#[test]
fn verify_java_rsa_chacha20_fixture() {
    let fixture_path = fixtures_dir().join("java_rsa_chacha20.txt");
    assert!(
        fixture_path.exists(),
        "java_rsa_chacha20.txt not found — run 'mvn test -Dtest=CrossCompatTest' once and commit the file"
    );

    let f = read_fixture(&fixture_path);
    let keys = keys_dir();
    let pub_pem  = std::fs::read_to_string(keys.join("rsa_2048_public.pem")).unwrap();
    let priv_pem = std::fs::read_to_string(keys.join("rsa_2048_private.pem")).unwrap();

    std::env::set_var("CC_RSA_PUB_VFY",  &pub_pem);
    std::env::set_var("CC_RSA_PRIV_VFY", &priv_pem);
    let cfg = EnvCryptoConfig::new("CC_RSA_PUB_VFY", "CC_RSA_PRIV_VFY");

    let version  = f["version"].parse::<u16>().unwrap();
    let enc_dek  = from_hex(&f["enc_dek_hex"]);
    let iv       = from_hex(&f["iv_hex"]);
    let cipher   = from_hex(&f["ciphertext_hex"]);
    let expected = from_hex(&f["plaintext_hex"]);

    let ctx      = CryptoContext::new(version, enc_dek);
    let session  = cfg.open_session(&ctx).unwrap();
    let recovered = session.decrypt_field(&iv, &cipher).unwrap();

    assert_eq!(recovered, expected, "Rust failed to decrypt Java RSA+ChaCha20 fixture");
    eprintln!("PASS: decrypted Java RSA+ChaCha20 → \"{}\"", String::from_utf8_lossy(&recovered));
}

// ── ECDH + ChaCha20 ───────────────────────────────────────────────────────────

/// Rust (party A) encrypts with ECDH + ChaCha20; writes fixture for Java (party B) to decrypt.
/// Rust uses: peer_pub = ec_b_public.der, host_priv = ec_a_private.pem
/// Java uses: peer_pub = ec_a_public.der, host_priv = ec_b_private.pem
#[test]
fn gen_rust_ecdh_chacha20_fixture() {
    let keys = keys_dir();
    let ec_b_pub_der  = std::fs::read(keys.join("ec_b_public.der")).unwrap();
    let ec_a_priv_pem = std::fs::read_to_string(keys.join("ec_a_private.pem")).unwrap();

    let cfg = EcdhCryptoConfig::of_keys(ec_b_pub_der, &ec_a_priv_pem);

    let plaintext: &[u8] = b"cross-compat: rust->java ECDH+ChaCha20";
    let dek: Vec<u8> = (33u8..=64).collect();

    let enc_dek = cfg.wrap_dek(CryptoContext::VERSION_CHACHA_ECDH, &dek).unwrap();
    let ctx     = CryptoContext::new(CryptoContext::VERSION_CHACHA_ECDH, enc_dek.clone());
    let session = cfg.open_session(&ctx).unwrap();
    let ef      = session.encrypt_field(plaintext).unwrap();

    // Verify own round-trip before writing.
    let dec = cfg.open_session(&ctx).unwrap().decrypt_field(&ef.iv, &ef.enc_content).unwrap();
    assert_eq!(dec.as_slice(), plaintext);

    let fixture_path = fixtures_dir().join("rust_ecdh_chacha20.txt");
    if !fixture_path.exists() {
        write_fixture(&fixture_path, &[
            ("generator",    "rust"),
            ("scenario",     "ecdh_chacha20"),
            ("version",      &CryptoContext::VERSION_CHACHA_ECDH.to_string()),
            ("plaintext_hex",&to_hex(plaintext)),
            ("enc_dek_hex",  &to_hex(&enc_dek)),
            ("iv_hex",       &to_hex(&ef.iv)),
            ("ciphertext_hex",&to_hex(&ef.enc_content)),
        ]);
        eprintln!("Generated rust_ecdh_chacha20.txt — commit this file");
    }
}

/// Rust (party A) decrypts the ECDH fixture written by Java (party B).
/// Java used: peer_pub = ec_a_public.der, host_priv = ec_b_private.pem
/// Rust uses: peer_pub = ec_b_public.der, host_priv = ec_a_private.pem (same ECDH secret)
#[test]
fn verify_java_ecdh_chacha20_fixture() {
    let fixture_path = fixtures_dir().join("java_ecdh_chacha20.txt");
    assert!(
        fixture_path.exists(),
        "java_ecdh_chacha20.txt not found — run 'mvn test -Dtest=CrossCompatTest' once and commit the file"
    );

    let f = read_fixture(&fixture_path);
    let keys = keys_dir();
    let ec_b_pub_der  = std::fs::read(keys.join("ec_b_public.der")).unwrap();
    let ec_a_priv_pem = std::fs::read_to_string(keys.join("ec_a_private.pem")).unwrap();

    let cfg = EcdhCryptoConfig::of_keys(ec_b_pub_der, &ec_a_priv_pem);

    let version  = f["version"].parse::<u16>().unwrap();
    let enc_dek  = from_hex(&f["enc_dek_hex"]);
    let iv       = from_hex(&f["iv_hex"]);
    let cipher   = from_hex(&f["ciphertext_hex"]);
    let expected = from_hex(&f["plaintext_hex"]);

    let ctx       = CryptoContext::new(version, enc_dek);
    let session   = cfg.open_session(&ctx).unwrap();
    let recovered = session.decrypt_field(&iv, &cipher).unwrap();

    assert_eq!(recovered, expected, "Rust failed to decrypt Java ECDH+ChaCha20 fixture");
    eprintln!("PASS: decrypted Java ECDH+ChaCha20 → \"{}\"", String::from_utf8_lossy(&recovered));
}
