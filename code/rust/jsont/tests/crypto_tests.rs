// =============================================================================
// tests/crypto_tests.rs — Steps 10.5-10.7: Stringify+Crypto, Transform Decrypt,
//                          On-demand Decrypt (Rust)
// =============================================================================
// Coverage:
//   10.5 — write_row_with_schema: sensitive plaintext → encrypted on wire
//   10.5 — write_row_with_schema: Encrypted value re-encoded to base64 wire
//   10.5 — write_row_with_schema: non-sensitive field written normally
//   10.5 — PassthroughCryptoConfig: bytes identity, wire format round-trip
//   10.6 — transform_with_crypto: Decrypt op decrypts Encrypted → Plain
//   10.6 — transform_with_crypto: Decrypt on plaintext value is idempotent
//   10.6 — transform with no crypto (transform()): Decrypt op → DecryptFailed
//   10.6 — transform_with_crypto on straight schema → row unchanged
//   10.7 — JsonTValue::decrypt_str: Encrypted → Some(plaintext)
//   10.7 — JsonTValue::decrypt_str: non-encrypted → None
//   10.7 — JsonTValue::decrypt_bytes: Encrypted → Some(bytes)
//   10.7 — JsonTRow::decrypt_field_str: correct index → Some(plaintext)
//   10.7 — JsonTRow::decrypt_field_str: out-of-scope index returns None
//   10.7 — JsonTRow::decrypt_field_str: non-encrypted field → None
// =============================================================================

use std::io::Cursor;

use jsont::{
    CryptoConfig, JsonTFieldBuilder, JsonTRow, JsonTSchemaBuilder,
    JsonTValue, PassthroughCryptoConfig, RowTransformer, SchemaRegistry, ScalarType,
    write_row_with_schema,
};
use jsont::model::field::JsonTField;
use jsont::model::schema::{SchemaKind, SchemaOperation};

// =============================================================================
// Helpers
// =============================================================================

/// Build a schema with one plain "name" field and one sensitive "ssn" field.
fn sensitive_schema() -> jsont::JsonTSchema {
    JsonTSchemaBuilder::straight("Person")
        .field(JsonTFieldBuilder::scalar("name", ScalarType::Str).build().unwrap()).unwrap()
        .field(JsonTFieldBuilder::scalar("ssn",  ScalarType::Str).sensitive().build().unwrap()).unwrap()
        .build()
        .unwrap()
}

/// Extract the field list from a straight schema.
fn schema_fields(schema: &jsont::JsonTSchema) -> &[JsonTField] {
    match &schema.kind {
        SchemaKind::Straight { fields } => fields.as_slice(),
        _ => panic!("expected straight schema"),
    }
}

/// Encode bytes to the "base64:<b64>" wire prefix.
fn base64_wire(bytes: &[u8]) -> String {
    use base64::Engine as _;
    format!(
        "base64:{}",
        base64::engine::general_purpose::STANDARD.encode(bytes)
    )
}

// =============================================================================
// 10.5 — write_row_with_schema
// =============================================================================

#[test]
fn write_row_with_schema_plaintext_sensitive_encrypted_on_wire() {
    let schema = sensitive_schema();
    let crypto = PassthroughCryptoConfig;

    // SSN is plaintext — schema-aware writer should encrypt it.
    let row = JsonTRow::new(vec![
        JsonTValue::str("Alice"),
        JsonTValue::str("123-45-6789"),
    ]);

    let mut buf = Cursor::new(Vec::new());
    write_row_with_schema(&row, schema_fields(&schema), &crypto, &mut buf).unwrap();
    let out = String::from_utf8(buf.into_inner()).unwrap();

    // name should be written as a quoted plain string.
    assert!(out.contains("\"Alice\""), "name should be quoted: {out}");
    // ssn should be written as base64:... envelope.
    assert!(out.contains("\"base64:"), "ssn should be base64-encoded: {out}");
    // The passthrough crypto leaves bytes identical — base64 of UTF-8 bytes of
    // "123-45-6789" must appear in the output.
    let expected = base64_wire(b"123-45-6789");
    assert!(
        out.contains(&expected[7..]), // strip "base64:" prefix, check b64 portion
        "base64 payload mismatch: {out}"
    );
}

#[test]
fn write_row_with_schema_encrypted_value_re_encoded() {
    let schema = sensitive_schema();
    let crypto = PassthroughCryptoConfig;

    // SSN already holds Encrypted bytes — re-encode without another crypto call.
    let ciphertext = b"already-ciphertext".to_vec();
    let row = JsonTRow::new(vec![
        JsonTValue::str("Bob"),
        JsonTValue::encrypted(ciphertext.clone()),
    ]);

    let mut buf = Cursor::new(Vec::new());
    write_row_with_schema(&row, schema_fields(&schema), &crypto, &mut buf).unwrap();
    let out = String::from_utf8(buf.into_inner()).unwrap();

    let expected = base64_wire(&ciphertext);
    assert!(
        out.contains(&expected),
        "re-encoded ciphertext mismatch: {out}"
    );
}

#[test]
fn write_row_with_schema_non_sensitive_written_plain() {
    let schema = sensitive_schema();
    let crypto = PassthroughCryptoConfig;

    let row = JsonTRow::new(vec![
        JsonTValue::str("Carol"),
        JsonTValue::str("999-00-1234"),
    ]);

    let mut buf = Cursor::new(Vec::new());
    write_row_with_schema(&row, schema_fields(&schema), &crypto, &mut buf).unwrap();
    let out = String::from_utf8(buf.into_inner()).unwrap();

    // name (non-sensitive) must NOT be base64-encoded.
    assert!(out.contains("\"Carol\""), "name should be plain: {out}");
    // The output must NOT start with {"base64: for the name field.
    assert!(
        !out.starts_with("{\"base64:"),
        "name field should not be encrypted: {out}"
    );
}

#[test]
fn passthrough_crypto_round_trips_identity() {
    let crypto = PassthroughCryptoConfig;
    let plaintext = b"hello world";
    let ct = crypto.encrypt("f", plaintext).unwrap();
    let pt = crypto.decrypt("f", &ct).unwrap();
    assert_eq!(pt, plaintext);
}

// =============================================================================
// 10.6 — transform_with_crypto
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
    let crypto = PassthroughCryptoConfig;

    // SSN stored as Encrypted bytes (passthrough: bytes == plaintext UTF-8).
    let row = JsonTRow::new(vec![
        JsonTValue::str("Alice"),
        JsonTValue::encrypted(b"123-45-6789".to_vec()),
    ]);

    let result = derived.transform_with_crypto(row, &registry, &crypto).unwrap();
    assert_eq!(result.fields[0], JsonTValue::str("Alice"));
    assert_eq!(result.fields[1], JsonTValue::str("123-45-6789"));
}

#[test]
fn transform_with_crypto_idempotent_on_plaintext_field() {
    let (_, derived, registry) = make_person_with_decrypt();
    let crypto = PassthroughCryptoConfig;

    // SSN already plaintext — Decrypt should leave it unchanged.
    let row = JsonTRow::new(vec![
        JsonTValue::str("Bob"),
        JsonTValue::str("000-11-2222"),
    ]);

    let result = derived.transform_with_crypto(row, &registry, &crypto).unwrap();
    assert_eq!(result.fields[1], JsonTValue::str("000-11-2222"));
}

#[test]
fn transform_without_crypto_returns_err_for_decrypt_op() {
    let (_, derived, registry) = make_person_with_decrypt();

    let row = JsonTRow::new(vec![
        JsonTValue::str("Carol"),
        JsonTValue::encrypted(b"secret".to_vec()),
    ]);

    // RowTransformer::transform passes None for crypto — Decrypt must fail.
    let result = derived.transform(row, &registry);
    assert!(
        result.is_err(),
        "expected error when Decrypt has no CryptoConfig"
    );
}

#[test]
fn transform_with_crypto_straight_schema_unchanged() {
    let schema = JsonTSchemaBuilder::straight("Simple")
        .field(JsonTFieldBuilder::scalar("x", ScalarType::I32).build().unwrap()).unwrap()
        .build()
        .unwrap();
    let mut registry = SchemaRegistry::new();
    registry.register(schema.clone());
    let crypto = PassthroughCryptoConfig;

    let row = JsonTRow::new(vec![JsonTValue::i32(42)]);
    let result = schema.transform_with_crypto(row.clone(), &registry, &crypto).unwrap();
    assert_eq!(result, row);
}

// =============================================================================
// 10.7 — on-demand decrypt API
// =============================================================================

#[test]
fn decrypt_str_on_encrypted_returns_plaintext_string() {
    let crypto = PassthroughCryptoConfig;
    let val = JsonTValue::encrypted(b"hello".to_vec());
    let result = val.decrypt_str("f", &crypto).unwrap();
    assert_eq!(result, Some("hello".to_string()));
}

#[test]
fn decrypt_str_on_plain_string_returns_none() {
    let crypto = PassthroughCryptoConfig;
    let val = JsonTValue::str("plaintext");
    let result = val.decrypt_str("f", &crypto).unwrap();
    assert_eq!(result, None);
}

#[test]
fn decrypt_bytes_on_encrypted_returns_raw_bytes() {
    let crypto = PassthroughCryptoConfig;
    let bytes = b"raw bytes".to_vec();
    let val = JsonTValue::encrypted(bytes.clone());
    let result = val.decrypt_bytes("f", &crypto).unwrap();
    assert_eq!(result, Some(bytes));
}

#[test]
fn decrypt_bytes_on_null_returns_none() {
    let crypto = PassthroughCryptoConfig;
    let val = JsonTValue::Null;
    let result = val.decrypt_bytes("f", &crypto).unwrap();
    assert_eq!(result, None);
}

#[test]
fn row_decrypt_field_str_at_valid_encrypted_index() {
    let crypto = PassthroughCryptoConfig;
    let row = JsonTRow::new(vec![
        JsonTValue::str("Alice"),
        JsonTValue::encrypted(b"123-45-6789".to_vec()),
    ]);
    let result = row.decrypt_field_str(1, "ssn", &crypto).unwrap();
    assert_eq!(result, Some("123-45-6789".to_string()));
}

#[test]
fn row_decrypt_field_str_out_of_range_returns_none() {
    let crypto = PassthroughCryptoConfig;
    let row = JsonTRow::new(vec![JsonTValue::str("Alice")]);
    let result = row.decrypt_field_str(99, "ssn", &crypto).unwrap();
    assert_eq!(result, None);
}

#[test]
fn row_decrypt_field_str_on_non_encrypted_returns_none() {
    let crypto = PassthroughCryptoConfig;
    let row = JsonTRow::new(vec![
        JsonTValue::str("Alice"),
        JsonTValue::str("plain ssn"),
    ]);
    let result = row.decrypt_field_str(1, "ssn", &crypto).unwrap();
    assert_eq!(result, None);
}
