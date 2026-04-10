// =============================================================================
// tests/sensitive_row_tests.rs — Step 10.3: Row Parse via promote_row (Rust)
// =============================================================================
// The row scanner has no schema context, so it always emits quoted strings as
// JsonTValue::Str(Plain("…")).  promote_row, which runs inside the validation
// pipeline and has both field and value, decodes the wire value as base64
// ciphertext for sensitive fields (the ~ schema marker is the authority).
//
// These tests construct rows that mimic scanner output (plain Str values) and
// feed them through ValidationPipeline::validate_rows, then inspect the
// promoted row values.
//
// Coverage:
//   • base64 string on sensitive field → Encrypted after promote_row
//   • Encrypted value carries correct decoded bytes
//   • Non-sensitive field with base64-looking string stays Str (no promotion)
//   • Sensitive field that is already Encrypted passes through unchanged
//   • Null on optional sensitive field stays Null
//   • Multiple sensitive fields in one row — all decoded
//   • Two rows processed independently — both decoded correctly
//   • Invalid base64 payload → FormatViolation (row rejected)
// =============================================================================

use std::sync::{Arc, Mutex};

use jsont::{
    DiagnosticEvent, DiagnosticSink, JsonTFieldBuilder, JsonTRow, JsonTSchemaBuilder,
    JsonTValue, ScalarType, SinkError, ValidationPipeline,
};

// ── capture sink ──────────────────────────────────────────────────────────────

struct CaptureSink(Arc<Mutex<Vec<DiagnosticEvent>>>);

impl CaptureSink {
    fn new() -> (Self, Arc<Mutex<Vec<DiagnosticEvent>>>) {
        let buf = Arc::new(Mutex::new(Vec::new()));
        (Self(Arc::clone(&buf)), buf)
    }
}

impl DiagnosticSink for CaptureSink {
    fn emit(&mut self, e: DiagnosticEvent) { self.0.lock().unwrap().push(e); }
    fn flush(&mut self) -> Result<(), SinkError> { Ok(()) }
}

// ── schema helpers ────────────────────────────────────────────────────────────

/// Person schema: str~:ssn, str:name
fn person_schema() -> jsont::JsonTSchema {
    JsonTSchemaBuilder::straight("Person")
        .field(JsonTFieldBuilder::scalar("ssn",  ScalarType::Str).sensitive().build().unwrap()).unwrap()
        .field(JsonTFieldBuilder::scalar("name", ScalarType::Str).build().unwrap()).unwrap()
        .build().unwrap()
}

/// Employee schema: str~:ssn, d64~:salary, str:dept
fn employee_schema() -> jsont::JsonTSchema {
    JsonTSchemaBuilder::straight("Employee")
        .field(JsonTFieldBuilder::scalar("ssn",    ScalarType::Str).sensitive().build().unwrap()).unwrap()
        .field(JsonTFieldBuilder::scalar("salary", ScalarType::D64).sensitive().build().unwrap()).unwrap()
        .field(JsonTFieldBuilder::scalar("dept",   ScalarType::Str).build().unwrap()).unwrap()
        .build().unwrap()
}

/// Run rows through the pipeline (no console output), return clean rows.
fn run_silent(schema: jsont::JsonTSchema, rows: Vec<JsonTRow>) -> Vec<JsonTRow> {
    let pipeline = ValidationPipeline::builder(schema)
        .without_console()
        .build();
    let clean = pipeline.validate_rows(rows);
    pipeline.finish().unwrap();
    clean
}

/// Run rows, also capturing diagnostic events.
fn run_with_events(
    schema: jsont::JsonTSchema,
    rows: Vec<JsonTRow>,
) -> (Vec<JsonTRow>, Vec<DiagnosticEvent>) {
    let (sink, captured) = CaptureSink::new();
    let pipeline = ValidationPipeline::builder(schema)
        .without_console()
        .with_sink(Box::new(sink))
        .build();
    let clean = pipeline.validate_rows(rows);
    pipeline.finish().unwrap();
    let events = captured.lock().unwrap().clone();
    (clean, events)
}

// ── base64 wire-format helper (mirrors what stringify will produce) ────────────

fn b64(plaintext: &[u8]) -> JsonTValue {
    use base64::Engine as _;
    let encoded = base64::engine::general_purpose::STANDARD.encode(plaintext);
    JsonTValue::str(encoded)
}

// =============================================================================
// Tests
// =============================================================================

#[test]
fn sensitive_field_base64_becomes_encrypted() {
    let row = JsonTRow::new(vec![b64(b"123-45-6789"), JsonTValue::str("Alice")]);
    let clean = run_silent(person_schema(), vec![row]);

    assert_eq!(clean.len(), 1);
    assert!(clean[0].fields[0].is_encrypted(),
        "ssn should be Encrypted, got type_name={}", clean[0].fields[0].type_name());
    assert!(!clean[0].fields[1].is_encrypted(), "name must stay plain");
}

#[test]
fn encrypted_value_carries_correct_bytes() {
    let plaintext = b"123-45-6789";
    let row = JsonTRow::new(vec![b64(plaintext), JsonTValue::str("Alice")]);
    let clean = run_silent(person_schema(), vec![row]);

    let bytes = clean[0].fields[0].as_encrypted().expect("should be Encrypted");
    assert_eq!(bytes, plaintext);
}

#[test]
fn non_sensitive_field_base64_prefix_stays_str() {
    // name is not sensitive — a base64-looking string stays as Str
    let row = JsonTRow::new(vec![
        b64(b"123-45-6789"), // sensitive ssn field — will be decoded
        JsonTValue::str("aGVsbG8="), // name is not sensitive — stays as Str
    ]);
    let clean = run_silent(person_schema(), vec![row]);

    assert!(!clean[0].fields[1].is_encrypted(), "non-sensitive field must not become Encrypted");
    assert!(matches!(&clean[0].fields[1], JsonTValue::Str(_)));
}

#[test]
fn sensitive_field_already_encrypted_passes_through() {
    // If the value is already Encrypted (e.g. after a prior promotion),
    // promote_row must leave it unchanged — no double-decoding.
    let ciphertext = b"already-decoded-bytes".to_vec();
    let row = JsonTRow::new(vec![
        JsonTValue::encrypted(ciphertext.clone()),
        JsonTValue::str("Dave"),
    ]);
    let clean = run_silent(person_schema(), vec![row]);

    assert!(clean[0].fields[0].is_encrypted(), "pre-Encrypted value must stay Encrypted");
    assert_eq!(clean[0].fields[0].as_encrypted().unwrap(), ciphertext.as_slice());
}

#[test]
fn null_on_optional_sensitive_field_stays_null() {
    let schema = JsonTSchemaBuilder::straight("Person")
        .field(JsonTFieldBuilder::scalar("ssn",  ScalarType::Str).sensitive().optional().build().unwrap()).unwrap()
        .field(JsonTFieldBuilder::scalar("name", ScalarType::Str).build().unwrap()).unwrap()
        .build().unwrap();

    let row = JsonTRow::new(vec![JsonTValue::Null, JsonTValue::str("Carol")]);
    let clean = run_silent(schema, vec![row]);

    assert!(matches!(&clean[0].fields[0], JsonTValue::Null), "null stays null");
}

#[test]
fn multiple_sensitive_fields_all_encrypted() {
    let row = JsonTRow::new(vec![
        b64(b"111-22-3333"),
        b64(b"95000.00"),
        JsonTValue::str("Engineering"),
    ]);
    let clean = run_silent(employee_schema(), vec![row]);

    assert_eq!(clean.len(), 1);
    assert!(clean[0].fields[0].is_encrypted(), "ssn encrypted");
    assert!(clean[0].fields[1].is_encrypted(), "salary encrypted");
    assert!(!clean[0].fields[2].is_encrypted(), "dept plain");
}

#[test]
fn two_rows_both_decoded_independently() {
    let rows = vec![
        JsonTRow::new(vec![b64(b"111-22-3333"), JsonTValue::str("Eve")]),
        JsonTRow::new(vec![b64(b"444-55-6666"), JsonTValue::str("Frank")]),
    ];
    let clean = run_silent(person_schema(), rows);

    assert_eq!(clean.len(), 2);
    assert_eq!(clean[0].fields[0].as_encrypted().unwrap(), b"111-22-3333");
    assert_eq!(clean[1].fields[0].as_encrypted().unwrap(), b"444-55-6666");
}

#[test]
fn invalid_base64_payload_emits_format_violation() {
    // "!!!" is not valid base64 — should emit a FormatViolation and reject the row
    let row = JsonTRow::new(vec![
        JsonTValue::str("!!!not-valid-base64!!!"),
        JsonTValue::str("Grace"),
    ]);
    let (clean, events) = run_with_events(person_schema(), vec![row]);

    assert!(clean.is_empty(), "invalid base64 row should be rejected");
    let has_format_violation = events.iter().any(|e| {
        matches!(&e.kind, jsont::EventKind::FormatViolation { field, .. } if field == "ssn")
    });
    assert!(has_format_violation, "expected FormatViolation for ssn, got: {:?}", events);
}

#[test]
fn mixed_row_sensitive_encrypted_plain_unchanged() {
    let row = JsonTRow::new(vec![b64(b"secret-ssn"), JsonTValue::str("Helen")]);
    let clean = run_silent(person_schema(), vec![row]);

    assert!(clean[0].fields[0].is_encrypted());
    // name field is plain Str after normal promotion
    assert!(matches!(&clean[0].fields[1], JsonTValue::Str(_)));
}
