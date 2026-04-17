// =============================================================================
// tests/crypto_tests.rs — Steps 5+6: Stream-level DEK, EncryptHeader writer/reader
// =============================================================================
// Coverage:
//   5.1  — write_encrypted_stream: EncryptHeader row is emitted first
//   5.2  — write_encrypted_stream: sensitive plaintext field uses per-field payload format
//   5.3  — write_encrypted_stream: non-sensitive field written as plain value
//   5.4  — write_encrypted_stream: sensitive Encrypted value re-encoded (no second crypto call)
//   6.1  — try_parse_encrypt_header: round-trips CryptoContext via write_encrypted_stream
//   6.2  — JsonTValue::decrypt_bytes: Encrypted payload → Some(plaintext) via CryptoContext
//   6.3  — JsonTValue::decrypt_str: Encrypted payload → Some(string) via CryptoContext
//   6.4  — JsonTValue::decrypt_bytes: non-encrypted → None
//   6.5  — JsonTRow::decrypt_field_str: Encrypted field → Some(string) via CryptoContext
//   6.6  — JsonTRow::decrypt_field_str: out-of-range index → None
//   6.7  — JsonTRow::decrypt_field_str: non-encrypted field → None
//   6.8  — transform_with_crypto: Decrypt op decrypts Encrypted → plain using CryptoContext
//   6.9  — transform_with_crypto: idempotent on already-plaintext field
//   6.10 — transform (no ctx/crypto): Decrypt op → DecryptFailed error
//   6.11 — transform_with_crypto on straight schema → row unchanged
//   misc — PassthroughCryptoConfig: wrap/unwrap/encrypt/decrypt round-trip
// =============================================================================

use std::io::Cursor;

use jsont::{
    CryptoConfig, CryptoContext, JsonTFieldBuilder, JsonTRow, JsonTSchemaBuilder,
    JsonTValue, PassthroughCryptoConfig, RowTransformer, SchemaRegistry, ScalarType,
    assemble_field_payload, parse_field_payload, try_parse_encrypt_header,
    write_encrypted_stream, write_row,
};
use jsont::model::field::JsonTField;
use jsont::model::schema::{SchemaKind, SchemaOperation};

// =============================================================================
// Helpers
// =============================================================================

/// Build a schema with "name" (plain) and "ssn" (sensitive).
fn sensitive_schema() -> jsont::JsonTSchema {
    JsonTSchemaBuilder::straight("Person")
        .field(JsonTFieldBuilder::scalar("name", ScalarType::Str).build().unwrap()).unwrap()
        .field(JsonTFieldBuilder::scalar("ssn",  ScalarType::Str).sensitive().build().unwrap()).unwrap()
        .build()
        .unwrap()
}

fn schema_fields(schema: &jsont::JsonTSchema) -> &[JsonTField] {
    match &schema.kind {
        SchemaKind::Straight { fields } => fields.as_slice(),
        _ => panic!("expected straight schema"),
    }
}

/// Build a CryptoContext with a known all-zero DEK for tests.
/// With PassthroughCryptoConfig, unwrap_dek returns the enc_dek unchanged, so
/// enc_dek here == the raw DEK that will be used for field decryption.
fn test_ctx() -> CryptoContext {
    let dek = vec![0u8; 32];
    CryptoContext::new(CryptoContext::VERSION_AES_PUBKEY, dek)
}

/// Encode bytes to the plain base64 wire format (no prefix).
fn base64_encode(bytes: &[u8]) -> String {
    use base64::Engine as _;
    base64::engine::general_purpose::STANDARD.encode(bytes)
}

/// Build a passthrough-encrypted field payload for `plaintext`.
///
/// Uses PassthroughCryptoConfig: encrypt_field returns (iv=[0;12], enc=plaintext).
/// The assembled payload is: [len_iv=12][len_digest=256][iv][sha256(plaintext)][plaintext].
fn make_encrypted_payload(plaintext: &[u8]) -> Vec<u8> {
    use sha2::{Digest, Sha256};
    use jsont::assemble_field_payload;
    let crypto = PassthroughCryptoConfig;
    let (iv, enc_content) = crypto.encrypt_field(&[0u8; 32], plaintext).unwrap();
    let digest = Sha256::digest(plaintext).to_vec();
    assemble_field_payload(&iv, &digest, &enc_content)
}

// =============================================================================
// Misc — PassthroughCryptoConfig
// =============================================================================

#[test]
fn passthrough_wrap_unwrap_is_identity() {
    let crypto = PassthroughCryptoConfig;
    let dek = b"this-is-a-32-byte-dek-for-tests!".to_vec();
    let enc = crypto.wrap_dek(CryptoContext::VERSION_AES_PUBKEY, &dek).unwrap();
    let dec = crypto.unwrap_dek(CryptoContext::VERSION_AES_PUBKEY, &enc).unwrap();
    assert_eq!(dek, dec);
}

#[test]
fn passthrough_encrypt_decrypt_field_is_identity() {
    let crypto = PassthroughCryptoConfig;
    let dek = [0u8; 32];
    let plaintext = b"hello-world";
    let (iv, enc) = crypto.encrypt_field(&dek, plaintext).unwrap();
    let dec = crypto.decrypt_field(&dek, &iv, &enc).unwrap();
    assert_eq!(plaintext.as_slice(), dec.as_slice());
}

// =============================================================================
// 5.1-5.4 — write_encrypted_stream
// =============================================================================

#[test]
fn write_stream_emits_encrypt_header_first() {
    let schema = sensitive_schema();
    let fields = schema_fields(&schema);
    let crypto = PassthroughCryptoConfig;
    let rows = vec![JsonTRow::new(vec![
        JsonTValue::str("Alice"),
        JsonTValue::str("123-45-6789"),
    ])];

    let mut buf = Cursor::new(Vec::new());
    write_encrypted_stream(&rows, fields, &crypto, CryptoContext::VERSION_AES_PUBKEY, &mut buf).unwrap();
    let out = String::from_utf8(buf.into_inner()).unwrap();

    // First row must be the EncryptHeader.
    assert!(out.starts_with("{\"ENCRYPTED_HEADER\""), "first row must be EncryptHeader: {out}");
}

#[test]
fn write_stream_sensitive_field_is_base64_payload() {
    let schema = sensitive_schema();
    let fields = schema_fields(&schema);
    let crypto = PassthroughCryptoConfig;
    let rows = vec![JsonTRow::new(vec![
        JsonTValue::str("Alice"),
        JsonTValue::str("123-45-6789"),
    ])];

    let mut buf = Cursor::new(Vec::new());
    write_encrypted_stream(&rows, fields, &crypto, CryptoContext::VERSION_AES_PUBKEY, &mut buf).unwrap();
    let out = String::from_utf8(buf.into_inner()).unwrap();

    // The ssn plaintext must NOT appear verbatim.
    assert!(!out.contains("\"123-45-6789\""), "ssn must not be plain: {out}");
    // The second row (after the header) must have a quoted base64 for the ssn field.
    let data_row = out.split(",\n").nth(1).unwrap_or("");
    assert!(data_row.starts_with("{\"Alice\""), "name should be plain: {data_row}");
}

#[test]
fn write_stream_non_sensitive_field_plain() {
    let schema = sensitive_schema();
    let fields = schema_fields(&schema);
    let crypto = PassthroughCryptoConfig;
    let rows = vec![JsonTRow::new(vec![
        JsonTValue::str("Carol"),
        JsonTValue::str("999-00-1234"),
    ])];

    let mut buf = Cursor::new(Vec::new());
    write_encrypted_stream(&rows, fields, &crypto, CryptoContext::VERSION_AES_PUBKEY, &mut buf).unwrap();
    let out = String::from_utf8(buf.into_inner()).unwrap();

    let data_row = out.split(",\n").nth(1).unwrap_or("");
    assert!(data_row.starts_with("{\"Carol\""), "name should be plain: {data_row}");
}

#[test]
fn write_stream_already_encrypted_value_reencoded() {
    let schema = sensitive_schema();
    let fields = schema_fields(&schema);
    let crypto = PassthroughCryptoConfig;

    // ssn already holds a raw payload blob — should be base64-encoded as-is.
    let payload_bytes = make_encrypted_payload(b"already-encrypted");
    let rows = vec![JsonTRow::new(vec![
        JsonTValue::str("Dave"),
        JsonTValue::encrypted(payload_bytes.clone()),
    ])];

    let mut buf = Cursor::new(Vec::new());
    write_encrypted_stream(&rows, fields, &crypto, CryptoContext::VERSION_AES_PUBKEY, &mut buf).unwrap();
    let out = String::from_utf8(buf.into_inner()).unwrap();

    let expected_b64 = base64_encode(&payload_bytes);
    assert!(out.contains(&expected_b64), "re-encoded payload mismatch: {out}");
}

// =============================================================================
// 6.1 — round-trip: write then parse EncryptHeader → CryptoContext
// =============================================================================

#[test]
fn encrypt_header_round_trip_via_write_stream() {
    let schema = sensitive_schema();
    let fields = schema_fields(&schema);
    let crypto = PassthroughCryptoConfig;
    let rows: Vec<JsonTRow> = vec![];

    let mut buf = Cursor::new(Vec::new());
    write_encrypted_stream(&rows, fields, &crypto, CryptoContext::VERSION_AES_PUBKEY, &mut buf).unwrap();
    let wire = String::from_utf8(buf.into_inner()).unwrap();

    let mut header_row = None;
    jsont::parse_rows(&wire, |r| {
        if header_row.is_none() { header_row = Some(r); }
    }).unwrap();
    let ctx = try_parse_encrypt_header(header_row.as_ref().unwrap())
        .expect("should parse EncryptHeader");
    assert_eq!(ctx.version, CryptoContext::VERSION_AES_PUBKEY);
    // PassthroughCryptoConfig wrap_dek returns DEK unchanged, and DEK is 32 random bytes.
    assert_eq!(ctx.enc_dek.len(), 32);
}

// =============================================================================
// 6.2-6.4 — JsonTValue::decrypt_bytes / decrypt_str
// =============================================================================

#[test]
fn decrypt_bytes_on_encrypted_returns_plaintext() {
    let ctx = test_ctx();
    let crypto = PassthroughCryptoConfig;
    let payload = make_encrypted_payload(b"hello");
    let val = JsonTValue::encrypted(payload);
    let result = val.decrypt_bytes("f", &ctx, &crypto).unwrap();
    assert_eq!(result, Some(b"hello".to_vec()));
}

#[test]
fn decrypt_str_on_encrypted_returns_string() {
    let ctx = test_ctx();
    let crypto = PassthroughCryptoConfig;
    let payload = make_encrypted_payload(b"hello");
    let val = JsonTValue::encrypted(payload);
    let result = val.decrypt_str("f", &ctx, &crypto).unwrap();
    assert_eq!(result, Some("hello".to_string()));
}

#[test]
fn decrypt_bytes_on_non_encrypted_returns_none() {
    let ctx = test_ctx();
    let crypto = PassthroughCryptoConfig;
    let val = JsonTValue::str("plaintext");
    let result = val.decrypt_bytes("f", &ctx, &crypto).unwrap();
    assert_eq!(result, None);
}

#[test]
fn decrypt_bytes_on_null_returns_none() {
    let ctx = test_ctx();
    let crypto = PassthroughCryptoConfig;
    let result = JsonTValue::Null.decrypt_bytes("f", &ctx, &crypto).unwrap();
    assert_eq!(result, None);
}

// =============================================================================
// 6.5-6.7 — JsonTRow::decrypt_field_str
// =============================================================================

#[test]
fn row_decrypt_field_str_at_valid_encrypted_index() {
    let ctx = test_ctx();
    let crypto = PassthroughCryptoConfig;
    let payload = make_encrypted_payload(b"123-45-6789");
    let row = JsonTRow::new(vec![
        JsonTValue::str("Alice"),
        JsonTValue::encrypted(payload),
    ]);
    let result = row.decrypt_field_str(1, "ssn", &ctx, &crypto).unwrap();
    assert_eq!(result, Some("123-45-6789".to_string()));
}

#[test]
fn row_decrypt_field_str_out_of_range_returns_none() {
    let ctx = test_ctx();
    let crypto = PassthroughCryptoConfig;
    let row = JsonTRow::new(vec![JsonTValue::str("Alice")]);
    let result = row.decrypt_field_str(99, "ssn", &ctx, &crypto).unwrap();
    assert_eq!(result, None);
}

#[test]
fn row_decrypt_field_str_non_encrypted_returns_none() {
    let ctx = test_ctx();
    let crypto = PassthroughCryptoConfig;
    let row = JsonTRow::new(vec![
        JsonTValue::str("Alice"),
        JsonTValue::str("plain ssn"),
    ]);
    let result = row.decrypt_field_str(1, "ssn", &ctx, &crypto).unwrap();
    assert_eq!(result, None);
}

// =============================================================================
// 6.8-6.11 — transform_with_crypto
// =============================================================================

fn make_person_with_decrypt() -> (jsont::JsonTSchema, jsont::JsonTSchema, SchemaRegistry) {
    let parent = JsonTSchemaBuilder::straight("Person")
        .field(JsonTFieldBuilder::scalar("name", ScalarType::Str).build().unwrap()).unwrap()
        .field(JsonTFieldBuilder::scalar("ssn",  ScalarType::Str).sensitive().build().unwrap()).unwrap()
        .build()
        .unwrap();

    let derived = JsonTSchemaBuilder::derived("PersonDecrypted", "Person")
        .operation(SchemaOperation::Decrypt { fields: vec!["ssn".to_string()] }).unwrap()
        .build()
        .unwrap();

    let mut registry = SchemaRegistry::new();
    registry.register(parent.clone());
    registry.register(derived.clone());

    (parent, derived, registry)
}

#[test]
fn transform_with_crypto_decrypts_encrypted_field() {
    let (_, derived, registry) = make_person_with_decrypt();
    let ctx = test_ctx();
    let crypto = PassthroughCryptoConfig;

    let payload = make_encrypted_payload(b"123-45-6789");
    let row = JsonTRow::new(vec![
        JsonTValue::str("Alice"),
        JsonTValue::encrypted(payload),
    ]);

    let result = derived.transform_with_crypto(row, &registry, &ctx, &crypto).unwrap();
    assert_eq!(result.fields[0], JsonTValue::str("Alice"));
    assert_eq!(result.fields[1], JsonTValue::str("123-45-6789"));
}

#[test]
fn transform_with_crypto_idempotent_on_plaintext_field() {
    let (_, derived, registry) = make_person_with_decrypt();
    let ctx = test_ctx();
    let crypto = PassthroughCryptoConfig;

    // ssn already plaintext — Decrypt should leave it unchanged.
    let row = JsonTRow::new(vec![
        JsonTValue::str("Bob"),
        JsonTValue::str("000-11-2222"),
    ]);

    let result = derived.transform_with_crypto(row, &registry, &ctx, &crypto).unwrap();
    assert_eq!(result.fields[1], JsonTValue::str("000-11-2222"));
}

#[test]
fn transform_without_crypto_returns_err_for_decrypt_op() {
    let (_, derived, registry) = make_person_with_decrypt();

    let payload = make_encrypted_payload(b"secret");
    let row = JsonTRow::new(vec![
        JsonTValue::str("Carol"),
        JsonTValue::encrypted(payload),
    ]);

    // RowTransformer::transform passes None for crypto — Decrypt must fail.
    let result = derived.transform(row, &registry);
    assert!(result.is_err(), "expected error when Decrypt has no CryptoConfig");
}

#[test]
fn transform_with_crypto_straight_schema_unchanged() {
    let schema = JsonTSchemaBuilder::straight("Simple")
        .field(JsonTFieldBuilder::scalar("x", ScalarType::I32).build().unwrap()).unwrap()
        .build()
        .unwrap();
    let mut registry = SchemaRegistry::new();
    registry.register(schema.clone());
    let ctx = test_ctx();
    let crypto = PassthroughCryptoConfig;

    let row = JsonTRow::new(vec![JsonTValue::i32(42)]);
    let result = schema.transform_with_crypto(row.clone(), &registry, &ctx, &crypto).unwrap();
    assert_eq!(result, row);
}

// =============================================================================
// End-to-end round-trip: write stream → parse header → decrypt fields
// =============================================================================

#[test]
fn end_to_end_write_read_roundtrip() {
    use base64::Engine as _;
    use sha2::{Digest, Sha256};

    let schema = sensitive_schema();
    let fields = schema_fields(&schema);
    let crypto = PassthroughCryptoConfig;

    let rows = vec![JsonTRow::new(vec![
        JsonTValue::str("Alice"),
        JsonTValue::str("123-45-6789"),
    ])];

    // Write the encrypted stream.
    let mut buf = Cursor::new(Vec::new());
    write_encrypted_stream(&rows, fields, &crypto, CryptoContext::VERSION_AES_PUBKEY, &mut buf).unwrap();
    let wire = String::from_utf8(buf.into_inner()).unwrap();

    // Parse all rows (raw scanner — no validation pipeline).
    let mut parsed_rows = Vec::new();
    jsont::parse_rows(&wire, |r| parsed_rows.push(r)).unwrap();

    assert_eq!(parsed_rows.len(), 2, "expected header + 1 data row");

    // First row: EncryptHeader → CryptoContext.
    let ctx = try_parse_encrypt_header(&parsed_rows[0])
        .expect("first row must be EncryptHeader");

    // Data row: name is plain, ssn is a quoted base64 string (raw scanner output).
    let data_row = &parsed_rows[1];
    assert_eq!(data_row.fields[0], JsonTValue::str("Alice"), "name should be plain");
    let ssn_b64 = data_row.fields[1].as_str()
        .expect("ssn must be a string on the wire");

    // Base64-decode the ssn wire value → payload bytes.
    let payload_bytes = base64::engine::general_purpose::STANDARD
        .decode(ssn_b64)
        .expect("ssn must be valid base64");

    // Parse the payload → decrypt manually.
    let (iv, stored_digest, enc_content) =
        parse_field_payload(&payload_bytes, "ssn").unwrap();
    let dek = crypto.unwrap_dek(ctx.version, &ctx.enc_dek).unwrap();
    let plaintext = crypto.decrypt_field(&dek, iv, enc_content).unwrap();
    // Verify digest.
    let computed = Sha256::digest(&plaintext);
    assert_eq!(computed.as_slice(), stored_digest);
    assert_eq!(plaintext, b"123-45-6789");

    // Alternatively: wrap in Encrypted and use the high-level API.
    let enc_val = JsonTValue::encrypted(payload_bytes);
    let decrypted = enc_val.decrypt_str("ssn", &ctx, &crypto).unwrap().unwrap();
    assert_eq!(decrypted, "123-45-6789");
}
