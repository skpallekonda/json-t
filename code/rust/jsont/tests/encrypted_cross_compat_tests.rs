// =============================================================================
// tests/encrypted_cross_compat_tests.rs — Full-stream encrypted cross-compat
// =============================================================================
// Verifies that a complete encrypted JsonT stream written by Rust can be
// decrypted by Java, and vice versa.  Tests the full wire format end-to-end:
//
//   EncryptHeader row  →  10 data rows with 2 sensitive (encrypted) fields
//
// Schema: CricketMatchEncrypted (11 fields, fields 4 and 7 are sensitive~)
//   0  matchId          str
//   1  matchCode        str
//   2  teamAId          str
//   3  teamAName        str
//   4  teamACoachName~  str  (always "Rahul Dravid", encrypted)
//   5  teamBId          str
//   6  teamBName        str
//   7  teamBCoachName~  str  (always "Matthew Mott", encrypted)
//   8  matchNumber      u16
//   9  prizeMoneyUsd    d128
//  10  dataSource       str
//
// Keys: RSA 2048 from code/cross-compat/keys/ (same pair as jsont-crypto tests)
// Algorithm: ChaCha20-Poly1305 + RSA-OAEP-SHA256 (VERSION_CHACHA_PUBKEY)
//
// Run order:
//   cargo test --test encrypted_cross_compat_tests   → writes rust-encrypted.jsont
//   mvn test -Dtest=EncryptedCrossCompatTest          → reads Rust file, writes java-encrypted.jsont
//   cargo test --test encrypted_cross_compat_tests   → reads Java file (verify pass)
// =============================================================================

use std::{fs, io::{BufWriter, Write}, path::Path};

use rust_decimal::Decimal;

use jsont::{
    CryptoConfig, CryptoContext, EnvCryptoConfig, JsonTFieldBuilder, JsonTRowBuilder,
    JsonTSchemaBuilder, JsonTValue, Parseable, ScalarType, SchemaKind,
    ValidationPipeline, parse_field_payload, try_parse_encrypt_header,
};

// ── Paths (relative to workspace root = code/rust/, which is cwd during tests) ─

const RUST_ENC_OUT: &str = "../../cross-compat/rust-encrypted.jsont";
const JAVA_ENC_IN:  &str = "../../cross-compat/java-encrypted.jsont";
const KEYS_DIR:     &str = "../../cross-compat/keys";

// ── Schema ────────────────────────────────────────────────────────────────────

fn encrypted_schema() -> jsont::JsonTSchema {
    JsonTSchemaBuilder::straight("CricketMatchEncrypted")
        .field(JsonTFieldBuilder::scalar("matchId",         ScalarType::Str ).build().unwrap()).unwrap()
        .field(JsonTFieldBuilder::scalar("matchCode",       ScalarType::Str ).build().unwrap()).unwrap()
        .field(JsonTFieldBuilder::scalar("teamAId",         ScalarType::Str ).build().unwrap()).unwrap()
        .field(JsonTFieldBuilder::scalar("teamAName",       ScalarType::Str ).build().unwrap()).unwrap()
        .field(JsonTFieldBuilder::scalar("teamACoachName",  ScalarType::Str ).sensitive().build().unwrap()).unwrap()
        .field(JsonTFieldBuilder::scalar("teamBId",         ScalarType::Str ).build().unwrap()).unwrap()
        .field(JsonTFieldBuilder::scalar("teamBName",       ScalarType::Str ).build().unwrap()).unwrap()
        .field(JsonTFieldBuilder::scalar("teamBCoachName",  ScalarType::Str ).sensitive().build().unwrap()).unwrap()
        .field(JsonTFieldBuilder::scalar("matchNumber",     ScalarType::U16 ).build().unwrap()).unwrap()
        .field(JsonTFieldBuilder::scalar("prizeMoneyUsd",   ScalarType::D128).build().unwrap()).unwrap()
        .field(JsonTFieldBuilder::scalar("dataSource",      ScalarType::Str ).build().unwrap()).unwrap()
        .build().unwrap()
}

// ── Row factory ───────────────────────────────────────────────────────────────

fn make_enc_row(i: u64) -> jsont::JsonTRow {
    JsonTRowBuilder::new()
        .push(JsonTValue::str(&format!("cc000000-0000-0000-0000-enc{:09}", i)))
        .push(JsonTValue::str(&format!("XCOMPAT-ENCR-{:02}", i)))
        .push(JsonTValue::str("IND"))
        .push(JsonTValue::str("India"))
        .push(JsonTValue::str("Rahul Dravid"))              // sensitive: teamACoachName
        .push(JsonTValue::str("ENG"))
        .push(JsonTValue::str("England"))
        .push(JsonTValue::str("Matthew Mott"))              // sensitive: teamBCoachName
        .push(JsonTValue::u16((1000 + i) as u16))
        .push(JsonTValue::d128(Decimal::new(100_000 + i as i64 * 10_000, 2)))
        .push(JsonTValue::str("ICC API"))
        .build()
}

// ── Helpers ───────────────────────────────────────────────────────────────────

fn load_rsa_config(pub_var: &str, priv_var: &str) -> EnvCryptoConfig {
    let keys = Path::new(KEYS_DIR);
    std::env::set_var(pub_var,  fs::read_to_string(keys.join("rsa_2048_public.pem")).unwrap());
    std::env::set_var(priv_var, fs::read_to_string(keys.join("rsa_2048_private.pem")).unwrap());
    EnvCryptoConfig::new(pub_var, priv_var)
}

/// Extract and decrypt a sensitive field from a validated row.
fn decrypt_sensitive(
    row: &jsont::JsonTRow,
    field_idx: usize,
    field_name: &str,
    session: &jsont::CipherSession,
) -> String {
    match row.get(field_idx).unwrap_or_else(|| panic!("field[{field_idx}] missing")) {
        JsonTValue::Encrypted(envelope) => {
            let (iv, _digest, enc_content) = parse_field_payload(envelope, field_name).unwrap();
            let plaintext = session.decrypt_field(iv, enc_content).unwrap();
            String::from_utf8(plaintext).unwrap()
        }
        other => panic!("field[{field_idx}] ({field_name}): expected Encrypted, got {other:?}"),
    }
}

// =============================================================================
// Gen: write rust-encrypted.jsont (only if not already committed)
// =============================================================================

#[test]
fn gen_rust_encrypted_jsont() {
    let path = Path::new(RUST_ENC_OUT);
    if path.exists() {
        println!("rust-encrypted.jsont already committed — skipping generation");
        return;
    }
    fs::create_dir_all(path.parent().unwrap()).unwrap();

    let cfg    = load_rsa_config("CC_ENC_PUB_GEN_R", "CC_ENC_PRIV_GEN_R");
    let schema = encrypted_schema();
    let rows: Vec<jsont::JsonTRow> = (0..10).map(make_enc_row).collect();

    let fields = match &schema.kind {
        SchemaKind::Straight { fields } => fields.clone(),
        _ => panic!("expected straight schema"),
    };

    let file = fs::File::create(path).unwrap();
    let mut out = BufWriter::new(file);
    jsont::write_encrypted_stream(&rows, &fields, &cfg, CryptoContext::VERSION_CHACHA_PUBKEY, &mut out).unwrap();
    out.flush().unwrap();

    println!("Generated rust-encrypted.jsont ({} rows) — commit this file", rows.len());
}

// =============================================================================
// Verify: decrypt java-encrypted.jsont written by Java test
// =============================================================================

#[test]
fn verify_java_encrypted_jsont() {
    let path = Path::new(JAVA_ENC_IN);
    assert!(
        path.exists(),
        "java-encrypted.jsont not found — run 'mvn test -Dtest=EncryptedCrossCompatTest' first and commit the file"
    );

    let content = fs::read_to_string(path).unwrap();
    let all_rows = Vec::<jsont::JsonTRow>::parse(&content).unwrap();
    assert!(!all_rows.is_empty(), "java-encrypted.jsont has no rows");

    // Row 0 must be the EncryptHeader.
    let ctx = try_parse_encrypt_header(&all_rows[0])
        .expect("java-encrypted.jsont: first row is not a valid EncryptHeader");

    let cfg = load_rsa_config("CC_ENC_PUB_VFY_R", "CC_ENC_PRIV_VFY_R");

    // Session for the validation pipeline (consumed by it).
    let pipeline_session = cfg.open_session(&ctx).expect("failed to unwrap Java DEK");

    let schema    = encrypted_schema();
    let data_rows = all_rows[1..].to_vec();
    assert_eq!(data_rows.len(), 10, "expected 10 data rows in java-encrypted.jsont");

    let pipeline = ValidationPipeline::builder(schema)
        .without_console()
        .with_cipher_session(pipeline_session)
        .build()
        .unwrap();

    let clean_rows = pipeline.validate_rows(data_rows);
    pipeline.finish().unwrap();

    // Fresh session for field-level decryption verification.
    let verify_session = cfg.open_session(&ctx).unwrap();

    for (i, row) in clean_rows.iter().enumerate() {
        let coach_a = decrypt_sensitive(row, 4, "teamACoachName", &verify_session);
        assert_eq!(coach_a, "Rahul Dravid", "row {i}: teamACoachName mismatch");

        let coach_b = decrypt_sensitive(row, 7, "teamBCoachName", &verify_session);
        assert_eq!(coach_b, "Matthew Mott", "row {i}: teamBCoachName mismatch");
    }

    println!(
        "verify_java_encrypted_jsont: all {} rows decrypted and verified ✓",
        clean_rows.len()
    );
}
