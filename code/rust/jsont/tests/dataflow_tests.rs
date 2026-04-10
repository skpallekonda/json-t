// =============================================================================
// tests/dataflow_tests.rs — Step 10.4: Build-time Dataflow Analysis
// =============================================================================
//
// Covers the two layers of dataflow enforcement:
//
//   Step 5  — build-time operation ordering (no parent needed)
//   Step 5a — runtime constraint pass-through for Encrypted values
//   Step 5  — parent-aware: validate_with_parent (decrypt on nonexistent / non-sensitive field)
//
// Build-time ordering checks (Step 5):
//   • transform before decrypt on sensitive field → build error
//   • filter  before decrypt on sensitive field → build error
//   • decrypt then transform → OK (correct ordering)
//   • decrypt then filter   → OK (correct ordering)
//   • rename/exclude/project before decrypt → OK (identity ops, no value access)
//   • two sensitive fields, decrypt first one, transform second before its decrypt → error
//   • no decrypt op at all → no check fires (non-sensitive pipeline)
//
// validate_with_parent (Step 5 cross-schema):
//   • decrypt field that does not exist in parent → error
//   • decrypt field that exists but is not sensitive → error
//   • decrypt field that is sensitive → OK
//
// Runtime constraint pass-through (Step 5a):
//   • Encrypted value on field with minValue → no constraint violation (skipped)
//   • Encrypted value on field with maxLength → no constraint violation (skipped)
//   • Encrypted value on field with constant  → no false-positive fatal (skipped)
//   • Encrypted value on required field       → passes required check (not absent)
//   • Null on required sensitive field        → RequiredFieldMissing (absent check still fires)
// =============================================================================

use jsont::{
    DiagnosticEvent, DiagnosticSink, EventKind, JsonTFieldBuilder, JsonTRow, JsonTSchemaBuilder,
    JsonTValue, ScalarType, SchemaOperation, SinkError, ValidationPipeline,
};
use jsont::model::schema::FieldPath;

// ── capture sink ──────────────────────────────────────────────────────────────

use std::sync::{Arc, Mutex};

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

// ── helpers ───────────────────────────────────────────────────────────────────

fn b64(plaintext: &[u8]) -> JsonTValue {
    use base64::Engine as _;
    let encoded = base64::engine::general_purpose::STANDARD.encode(plaintext);
    JsonTValue::str(encoded)
}

fn person_schema() -> jsont::JsonTSchema {
    JsonTSchemaBuilder::straight("Person")
        .field(JsonTFieldBuilder::scalar("ssn",  ScalarType::Str).sensitive().build().unwrap()).unwrap()
        .field(JsonTFieldBuilder::scalar("name", ScalarType::Str).build().unwrap()).unwrap()
        .build().unwrap()
}

// =============================================================================
// Build-time ordering checks
// =============================================================================

#[test]
fn transform_before_decrypt_on_sensitive_field_is_build_error() {
    // salary appears in Decrypt later in the list → transform before decrypt is an error
    let result = JsonTSchemaBuilder::derived("Analytics", "Employee")
        .operation(SchemaOperation::new_transform(
            FieldPath::single("salary"),
            jsont::JsonTExpression::field_name("salary"),
        )).unwrap()
        .operation(SchemaOperation::Decrypt { fields: vec!["salary".into()] }).unwrap()
        .build();

    assert!(result.is_err(), "should fail: transform on encrypted field before decrypt");
    let msg = result.unwrap_err().to_string();
    assert!(msg.contains("salary"), "error should name the field: {}", msg);
    assert!(msg.contains("encrypted") || msg.contains("decrypt"), "error should mention encryption: {}", msg);
}

#[test]
fn filter_before_decrypt_on_sensitive_field_is_build_error() {
    use jsont::JsonTExpression;
    let result = JsonTSchemaBuilder::derived("Analytics", "Employee")
        .operation(SchemaOperation::new_filter(
            JsonTExpression::field_name("ssn"),
        )).unwrap()
        .operation(SchemaOperation::Decrypt { fields: vec!["ssn".into()] }).unwrap()
        .build();

    assert!(result.is_err(), "should fail: filter on encrypted field before decrypt");
    let msg = result.unwrap_err().to_string();
    assert!(msg.contains("ssn"), "error should name the field: {}", msg);
}

#[test]
fn decrypt_then_transform_is_ok() {
    let result = JsonTSchemaBuilder::derived("Analytics", "Employee")
        .operation(SchemaOperation::Decrypt { fields: vec!["salary".into()] }).unwrap()
        .operation(SchemaOperation::new_transform(
            FieldPath::single("salary"),
            jsont::JsonTExpression::field_name("salary"),
        )).unwrap()
        .build();

    assert!(result.is_ok(), "decrypt before transform should succeed: {:?}", result.err());
}

#[test]
fn decrypt_then_filter_is_ok() {
    use jsont::JsonTExpression;
    let result = JsonTSchemaBuilder::derived("Analytics", "Employee")
        .operation(SchemaOperation::Decrypt { fields: vec!["ssn".into()] }).unwrap()
        .operation(SchemaOperation::new_filter(
            JsonTExpression::field_name("ssn"),
        )).unwrap()
        .build();

    assert!(result.is_ok(), "decrypt before filter should succeed: {:?}", result.err());
}

#[test]
fn rename_before_decrypt_is_ok() {
    use jsont::model::schema::RenamePair;
    let result = JsonTSchemaBuilder::derived("Analytics", "Employee")
        .operation(SchemaOperation::Rename(vec![RenamePair {
            from: FieldPath::single("ssn"),
            to: "social_security".into(),
        }])).unwrap()
        .operation(SchemaOperation::Decrypt { fields: vec!["ssn".into()] }).unwrap()
        .build();

    assert!(result.is_ok(), "rename before decrypt is identity op — should succeed: {:?}", result.err());
}

#[test]
fn exclude_before_decrypt_is_ok() {
    let result = JsonTSchemaBuilder::derived("Analytics", "Employee")
        .operation(SchemaOperation::Exclude(vec![FieldPath::single("dept")])).unwrap()
        .operation(SchemaOperation::Decrypt { fields: vec!["ssn".into()] }).unwrap()
        .build();

    assert!(result.is_ok(), "exclude before decrypt is OK: {:?}", result.err());
}

#[test]
fn no_decrypt_op_no_check_fires() {
    // Pipeline with transform but no Decrypt → nothing is known-sensitive → no error
    let result = JsonTSchemaBuilder::derived("Summary", "Employee")
        .operation(SchemaOperation::new_transform(
            FieldPath::single("dept"),
            jsont::JsonTExpression::field_name("dept"),
        )).unwrap()
        .build();

    assert!(result.is_ok(), "no Decrypt op means no sensitive field detected: {:?}", result.err());
}

#[test]
fn second_sensitive_field_used_before_its_decrypt_is_error() {
    use jsont::JsonTExpression;
    // salary is decrypted first; ssn is decrypted second.
    // Filter references ssn BEFORE ssn's Decrypt → error.
    let result = JsonTSchemaBuilder::derived("Analytics", "Employee")
        .operation(SchemaOperation::Decrypt { fields: vec!["salary".into()] }).unwrap()
        .operation(SchemaOperation::new_filter(
            JsonTExpression::field_name("ssn"),
        )).unwrap()
        .operation(SchemaOperation::Decrypt { fields: vec!["ssn".into()] }).unwrap()
        .build();

    assert!(result.is_err(), "ssn used in filter before its decrypt → error");
    let msg = result.unwrap_err().to_string();
    assert!(msg.contains("ssn"), "error should name ssn: {}", msg);
}

// =============================================================================
// validate_with_parent — cross-schema checks
// =============================================================================

#[test]
fn validate_with_parent_decrypt_nonexistent_field_is_error() {
    let parent = person_schema();

    let derived = JsonTSchemaBuilder::derived("Analytics", "Person")
        .operation(SchemaOperation::Decrypt { fields: vec!["nonexistent".into()] }).unwrap()
        .build().unwrap();

    let result = derived.validate_with_parent(&parent);
    assert!(result.is_err(), "decrypt on nonexistent field should be an error");
    let msg = result.unwrap_err().to_string();
    assert!(msg.contains("nonexistent"), "error should name the missing field: {}", msg);
}

#[test]
fn validate_with_parent_decrypt_non_sensitive_field_is_error() {
    let parent = person_schema(); // "name" is not sensitive

    let derived = JsonTSchemaBuilder::derived("Analytics", "Person")
        .operation(SchemaOperation::Decrypt { fields: vec!["name".into()] }).unwrap()
        .build().unwrap();

    let result = derived.validate_with_parent(&parent);
    assert!(result.is_err(), "decrypt on non-sensitive field should be an error");
    let msg = result.unwrap_err().to_string();
    assert!(msg.contains("name"), "error should name the field: {}", msg);
    assert!(msg.contains("sensitive") || msg.contains("~"), "error should mention sensitivity: {}", msg);
}

#[test]
fn validate_with_parent_decrypt_sensitive_field_is_ok() {
    let parent = person_schema(); // "ssn" is sensitive

    let derived = JsonTSchemaBuilder::derived("Analytics", "Person")
        .operation(SchemaOperation::Decrypt { fields: vec!["ssn".into()] }).unwrap()
        .build().unwrap();

    assert!(derived.validate_with_parent(&parent).is_ok(),
        "decrypt on a sensitive field should pass validate_with_parent");
}

#[test]
fn validate_with_parent_on_straight_schema_is_noop() {
    let parent = person_schema();
    let straight = person_schema(); // same, but called as "parent"
    assert!(straight.validate_with_parent(&parent).is_ok(),
        "validate_with_parent on a straight schema is a no-op");
}

// ── Full field-scope simulation ───────────────────────────────────────────────

/// Parent: str~:ssn, str:name, str:dept (3 fields)
fn employee_schema() -> jsont::JsonTSchema {
    JsonTSchemaBuilder::straight("Employee")
        .field(JsonTFieldBuilder::scalar("ssn",  ScalarType::Str).sensitive().build().unwrap()).unwrap()
        .field(JsonTFieldBuilder::scalar("name", ScalarType::Str).build().unwrap()).unwrap()
        .field(JsonTFieldBuilder::scalar("dept", ScalarType::Str).build().unwrap()).unwrap()
        .build().unwrap()
}

#[test]
fn project_then_transform_on_excluded_field_is_error() {
    // Project keeps only (name, dept); ssn is dropped.
    // Transform then references ssn → should fail.
    let parent = employee_schema();
    let derived = JsonTSchemaBuilder::derived("Analytics", "Employee")
        .operation(SchemaOperation::Project(vec![
            FieldPath::single("name"),
            FieldPath::single("dept"),
        ])).unwrap()
        .operation(SchemaOperation::new_transform(
            FieldPath::single("name"),
            jsont::JsonTExpression::field_name("ssn"), // ssn not in scope
        )).unwrap()
        .build().unwrap();

    let result = derived.validate_with_parent(&parent);
    assert!(result.is_err(), "transform on projected-away field should fail");
    let msg = result.unwrap_err().to_string();
    assert!(msg.contains("ssn"), "error should name ssn: {}", msg);
}

#[test]
fn exclude_then_transform_on_excluded_field_is_error() {
    let parent = employee_schema();
    let derived = JsonTSchemaBuilder::derived("Analytics", "Employee")
        .operation(SchemaOperation::Exclude(vec![FieldPath::single("ssn")])).unwrap()
        .operation(SchemaOperation::new_transform(
            FieldPath::single("name"),
            jsont::JsonTExpression::field_name("ssn"), // ssn was excluded
        )).unwrap()
        .build().unwrap();

    let result = derived.validate_with_parent(&parent);
    assert!(result.is_err(), "transform referencing excluded field should fail");
    let msg = result.unwrap_err().to_string();
    assert!(msg.contains("ssn"), "error should name ssn: {}", msg);
}

#[test]
fn rename_old_name_no_longer_in_scope() {
    // Rename ssn → social; then Transform references ssn (old name) → error
    let parent = employee_schema();
    let derived = JsonTSchemaBuilder::derived("Analytics", "Employee")
        .operation(SchemaOperation::Rename(vec![jsont::model::schema::RenamePair {
            from: FieldPath::single("ssn"),
            to: "social".into(),
        }])).unwrap()
        .operation(SchemaOperation::new_transform(
            FieldPath::single("name"),
            jsont::JsonTExpression::field_name("ssn"), // old name, gone after rename
        )).unwrap()
        .build().unwrap();

    let result = derived.validate_with_parent(&parent);
    assert!(result.is_err(), "reference to renamed-away field should fail");
    let msg = result.unwrap_err().to_string();
    assert!(msg.contains("ssn"), "error should name ssn: {}", msg);
}

#[test]
fn rename_to_conflicting_name_is_error() {
    // Rename ssn → name, but name already exists → collision
    let parent = employee_schema();
    let derived = JsonTSchemaBuilder::derived("Analytics", "Employee")
        .operation(SchemaOperation::Rename(vec![jsont::model::schema::RenamePair {
            from: FieldPath::single("ssn"),
            to: "name".into(), // "name" already in scope
        }])).unwrap()
        .build().unwrap();

    let result = derived.validate_with_parent(&parent);
    assert!(result.is_err(), "rename collision should fail");
    let msg = result.unwrap_err().to_string();
    assert!(msg.contains("name"), "error should mention the conflicting field: {}", msg);
}

#[test]
fn filter_referencing_projected_away_field_is_error() {
    let parent = employee_schema();
    let derived = JsonTSchemaBuilder::derived("Analytics", "Employee")
        .operation(SchemaOperation::Project(vec![FieldPath::single("name")])).unwrap()
        .operation(SchemaOperation::new_filter(
            jsont::JsonTExpression::field_name("dept"), // dept was projected away
        )).unwrap()
        .build().unwrap();

    let result = derived.validate_with_parent(&parent);
    assert!(result.is_err(), "filter on projected-away field should fail");
    let msg = result.unwrap_err().to_string();
    assert!(msg.contains("dept"), "error should name dept: {}", msg);
}

#[test]
fn full_valid_pipeline_passes() {
    // decrypt(ssn) → transform(name) using name → filter on dept → exclude ssn
    // All references in scope and in correct order.
    let parent = employee_schema();
    let derived = JsonTSchemaBuilder::derived("Analytics", "Employee")
        .operation(SchemaOperation::Decrypt { fields: vec!["ssn".into()] }).unwrap()
        .operation(SchemaOperation::new_transform(
            FieldPath::single("name"),
            jsont::JsonTExpression::field_name("name"),
        )).unwrap()
        .operation(SchemaOperation::new_filter(
            jsont::JsonTExpression::field_name("dept"),
        )).unwrap()
        .operation(SchemaOperation::Exclude(vec![FieldPath::single("ssn")])).unwrap()
        .build().unwrap();

    assert!(derived.validate_with_parent(&parent).is_ok(),
        "valid pipeline should pass full dataflow check");
}

// =============================================================================
// Runtime constraint pass-through — Step 5a
// =============================================================================

fn run_silent(schema: jsont::JsonTSchema, rows: Vec<JsonTRow>) -> Vec<JsonTRow> {
    let pipeline = ValidationPipeline::builder(schema)
        .without_console()
        .build();
    let clean = pipeline.validate_rows(rows);
    pipeline.finish().unwrap();
    clean
}

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

#[test]
fn encrypted_value_skips_min_value_constraint() {
    // salary has minValue=50000 — but an Encrypted value should skip this check
    let schema = JsonTSchemaBuilder::straight("Employee")
        .field(JsonTFieldBuilder::scalar("salary", ScalarType::D64)
            .sensitive()
            .min_value(50000.0)
            .build().unwrap()).unwrap()
        .build().unwrap();

    let row = JsonTRow::new(vec![b64(b"30000.00")]); // below minValue, but encrypted
    let (clean, events) = run_with_events(schema, vec![row]);

    // Row should be accepted (no constraint violation for encrypted value)
    assert_eq!(clean.len(), 1, "encrypted row should pass through despite apparent minValue violation");
    let has_constraint_warning = events.iter().any(|e| {
        matches!(&e.kind, EventKind::ConstraintViolation { field, .. } if field == "salary")
    });
    assert!(!has_constraint_warning, "no constraint warning expected for encrypted value, got: {:?}", events);
}

#[test]
fn encrypted_value_skips_max_length_constraint() {
    let schema = JsonTSchemaBuilder::straight("Person")
        .field(JsonTFieldBuilder::scalar("ssn", ScalarType::Str)
            .sensitive()
            .max_length(3) // artificially small
            .build().unwrap()).unwrap()
        .build().unwrap();

    let row = JsonTRow::new(vec![b64(b"123-45-6789")]); // longer than 3 chars, but encrypted
    let (clean, events) = run_with_events(schema, vec![row]);

    assert_eq!(clean.len(), 1, "encrypted value should bypass maxLength constraint");
    let has_length_warning = events.iter().any(|e| {
        matches!(&e.kind, EventKind::ConstraintViolation { constraint, .. } if constraint.contains("maxLength"))
    });
    assert!(!has_length_warning, "no maxLength warning expected for encrypted value, got: {:?}", events);
}

#[test]
fn encrypted_value_skips_constant_check() {
    // Constant check would normally fire a Fatal for any non-matching value.
    // For an Encrypted value it must be skipped — the ciphertext cannot be compared.
    let schema = JsonTSchemaBuilder::straight("Config")
        .field(JsonTFieldBuilder::scalar("secret", ScalarType::Str)
            .sensitive()
            .constant_value(JsonTValue::str("fixed"))
            .build().unwrap()).unwrap()
        .build().unwrap();

    let row = JsonTRow::new(vec![b64(b"some-secret")]); // encrypted — constant cannot apply
    let (clean, events) = run_with_events(schema, vec![row]);

    assert_eq!(clean.len(), 1, "encrypted value should not trigger constant-check fatal");
    let has_fatal_constant = events.iter().any(|e| {
        e.is_fatal() && matches!(&e.kind, EventKind::ConstraintViolation { field, .. } if field == "secret")
    });
    assert!(!has_fatal_constant, "constant check must not fire for encrypted value, got: {:?}", events);
}

#[test]
fn encrypted_value_satisfies_required_check() {
    let schema = person_schema(); // ssn is required and sensitive

    let row = JsonTRow::new(vec![b64(b"123-45-6789"), JsonTValue::str("Alice")]);
    let clean = run_silent(schema, vec![row]);

    assert_eq!(clean.len(), 1, "encrypted value satisfies required check");
    assert!(clean[0].fields[0].is_encrypted(), "ssn should be Encrypted");
}

#[test]
fn null_on_required_sensitive_field_still_fails() {
    let schema = person_schema(); // ssn is required and sensitive

    let row = JsonTRow::new(vec![JsonTValue::Null, JsonTValue::str("Alice")]);
    let (clean, events) = run_with_events(schema, vec![row]);

    assert!(clean.is_empty(), "null on required sensitive field → row rejected");
    let has_required_missing = events.iter().any(|e| {
        matches!(&e.kind, EventKind::RequiredFieldMissing { field } if field == "ssn")
    });
    assert!(has_required_missing, "expected RequiredFieldMissing for ssn, got: {:?}", events);
}
