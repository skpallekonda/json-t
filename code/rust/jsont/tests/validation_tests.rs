// =============================================================================
// tests/validation_tests.rs — Integration tests for the validation pipeline
// =============================================================================
//
// Run:
//   cargo test --test validation_tests -- --nocapture
// =============================================================================

use std::sync::{Arc, Mutex};

use jsont::{
    DiagnosticSink, DiagnosticEvent, EventKind, Severity, SinkError,
    JsonTFieldBuilder, JsonTSchemaBuilder,
    JsonTRowBuilder, JsonTValue,
    ScalarType,
    ValidationPipeline,
    model::validation::{JsonTExpression, JsonTRule, JsonTValidationBlock},
    model::schema::FieldPath,
};

// =============================================================================
// CaptureSink — captures all events in a shared buffer for assertions
// =============================================================================

struct CaptureSink {
    events: Arc<Mutex<Vec<DiagnosticEvent>>>,
}

impl CaptureSink {
    fn new() -> (Self, Arc<Mutex<Vec<DiagnosticEvent>>>) {
        let buf = Arc::new(Mutex::new(Vec::new()));
        (Self { events: Arc::clone(&buf) }, buf)
    }
}

impl DiagnosticSink for CaptureSink {
    fn emit(&mut self, event: DiagnosticEvent) {
        self.events.lock().unwrap().push(event);
    }
    fn flush(&mut self) -> Result<(), SinkError> {
        Ok(())
    }
}

// =============================================================================
// Schema / row helpers
// =============================================================================

/// Order schema: { i64:id, str:name (required), i32:quantity, d64:price }
fn order_schema() -> jsont::JsonTSchema {
    JsonTSchemaBuilder::straight("Order")
        .field(JsonTFieldBuilder::scalar("id",       ScalarType::I64).build().unwrap()).unwrap()
        .field(JsonTFieldBuilder::scalar("name",     ScalarType::Str).build().unwrap()).unwrap()
        .field(JsonTFieldBuilder::scalar("quantity", ScalarType::I32).build().unwrap()).unwrap()
        .field(JsonTFieldBuilder::scalar("price",    ScalarType::D64).build().unwrap()).unwrap()
        .build()
        .unwrap()
}

fn make_order(id: i64, name: &str, qty: i32, price: f64) -> jsont::JsonTRow {
    JsonTRowBuilder::new()
        .push(JsonTValue::i64(id))
        .push(JsonTValue::str(name))
        .push(JsonTValue::i32(qty))
        .push(JsonTValue::d64(price))
        .build()
}

// =============================================================================
// Tests
// =============================================================================

// ── 1. All rows valid ──────────────────────────────────────────────────────

#[test]
fn test_all_valid_rows_pass_through() {
    let schema = order_schema();
    let (sink, buf) = CaptureSink::new();
    let pipeline = ValidationPipeline::builder(schema)
        .without_console()
        .with_sink(Box::new(sink))
        .build().unwrap();

    let rows = vec![
        make_order(1, "Widget", 10, 9.99),
        make_order(2, "Gadget", 5,  19.99),
    ];
    let clean = pipeline.validate_rows(rows);
    pipeline.finish().unwrap();

    assert_eq!(clean.len(), 2);

    let events = buf.lock().unwrap();
    let fatals: Vec<_> = events.iter().filter(|e| e.is_fatal()).collect();
    assert!(fatals.is_empty(), "expected no fatals, got: {:?}", fatals);
}

// ── 2. Shape mismatch → row excluded ──────────────────────────────────────

#[test]
fn test_shape_mismatch_excluded() {
    let schema = order_schema(); // expects 4 fields
    let (sink, buf) = CaptureSink::new();
    let pipeline = ValidationPipeline::builder(schema)
        .without_console()
        .with_sink(Box::new(sink))
        .build().unwrap();

    let rows = vec![
        make_order(1, "Widget", 10, 9.99),            // good (4 fields)
        JsonTRowBuilder::new()                         // bad  (2 fields)
            .push(JsonTValue::i64(2))
            .push(JsonTValue::str("Gadget"))
            .build(),
    ];
    let clean = pipeline.validate_rows(rows);
    pipeline.finish().unwrap();

    assert_eq!(clean.len(), 1, "only the valid row should pass");
    assert_eq!(clean[0].get(0), Some(&JsonTValue::i64(1)));

    let events = buf.lock().unwrap();
    let shape_events: Vec<_> = events
        .iter()
        .filter(|e| matches!(&e.kind, EventKind::ShapeMismatch { .. }))
        .collect();
    assert_eq!(shape_events.len(), 1);
    assert!(shape_events[0].is_fatal());
}

// ── 3. Required field missing → row excluded ──────────────────────────────

#[test]
fn test_required_field_missing_excluded() {
    let schema = JsonTSchemaBuilder::straight("Order")
        .field(JsonTFieldBuilder::scalar("id",   ScalarType::I64).build().unwrap()).unwrap()
        .field(JsonTFieldBuilder::scalar("name", ScalarType::Str).build().unwrap()).unwrap()
        .build()
        .unwrap();

    let (sink, buf) = CaptureSink::new();
    let pipeline = ValidationPipeline::builder(schema)
        .without_console()
        .with_sink(Box::new(sink))
        .build().unwrap();

    let rows = vec![
        JsonTRowBuilder::new()
            .push(JsonTValue::i64(1))
            .push(JsonTValue::str("Alice"))
            .build(),
        JsonTRowBuilder::new()
            .push(JsonTValue::i64(2))
            .push(JsonTValue::Null)         // missing required name
            .build(),
        JsonTRowBuilder::new()
            .push(JsonTValue::i64(3))
            .push(JsonTValue::Unspecified)  // missing required name
            .build(),
    ];
    let clean = pipeline.validate_rows(rows);
    pipeline.finish().unwrap();

    assert_eq!(clean.len(), 1);
    assert_eq!(clean[0].get(0), Some(&JsonTValue::i64(1)));

    let events = buf.lock().unwrap();
    let missing: Vec<_> = events
        .iter()
        .filter(|e| matches!(&e.kind, EventKind::RequiredFieldMissing { .. }))
        .collect();
    assert_eq!(missing.len(), 2, "two rows had missing required fields");
    assert!(missing.iter().all(|e| e.is_fatal()));
}

// ── 4. Optional field null → row accepted ─────────────────────────────────

#[test]
fn test_optional_field_null_accepted() {
    let schema = JsonTSchemaBuilder::straight("Order")
        .field(JsonTFieldBuilder::scalar("id",   ScalarType::I64).build().unwrap()).unwrap()
        .field(JsonTFieldBuilder::scalar("name", ScalarType::Str).optional().build().unwrap()).unwrap()
        .build()
        .unwrap();

    let pipeline = ValidationPipeline::builder(schema)
        .without_console()
        .build().unwrap();

    let rows = vec![
        JsonTRowBuilder::new()
            .push(JsonTValue::i64(1))
            .push(JsonTValue::Null)
            .build(),
    ];
    let clean = pipeline.validate_rows(rows);
    pipeline.finish().unwrap();

    assert_eq!(clean.len(), 1, "null optional field must not reject the row");
}

// ── 5. Value constraint violation → Warning, row accepted ─────────────────

#[test]
fn test_value_constraint_warning_row_accepted() {
    let schema = JsonTSchemaBuilder::straight("Product")
        .field(JsonTFieldBuilder::scalar("id",    ScalarType::I64).build().unwrap()).unwrap()
        .field(
            JsonTFieldBuilder::scalar("price", ScalarType::D64)
                .min_value(0.0)
                .max_value(1000.0)
                .build()
                .unwrap(),
        ).unwrap()
        .build()
        .unwrap();

    let (sink, buf) = CaptureSink::new();
    let pipeline = ValidationPipeline::builder(schema)
        .without_console()
        .with_sink(Box::new(sink))
        .build().unwrap();

    let rows = vec![
        JsonTRowBuilder::new().push(JsonTValue::i64(1)).push(JsonTValue::d64(50.0)).build(),    // OK
        JsonTRowBuilder::new().push(JsonTValue::i64(2)).push(JsonTValue::d64(-5.0)).build(),    // violates minValue
        JsonTRowBuilder::new().push(JsonTValue::i64(3)).push(JsonTValue::d64(9999.0)).build(),  // violates maxValue
    ];
    let clean = pipeline.validate_rows(rows);
    pipeline.finish().unwrap();

    // All three rows are returned — violations are Warnings, not Fatal.
    assert_eq!(clean.len(), 3, "constraint warnings must not exclude rows");

    let events = buf.lock().unwrap();
    let violations: Vec<_> = events
        .iter()
        .filter(|e| matches!(&e.kind, EventKind::ConstraintViolation { .. }))
        .collect();
    assert_eq!(violations.len(), 2, "expected two constraint violations");
    assert!(
        violations.iter().all(|e| e.severity == Severity::Warning),
        "value constraint violations must be Warnings"
    );
}

// ── 6. Length constraint violation → Warning, row accepted ────────────────

#[test]
fn test_length_constraint_warning_row_accepted() {
    let schema = JsonTSchemaBuilder::straight("Tag")
        .field(
            JsonTFieldBuilder::scalar("label", ScalarType::Str)
                .min_length(3)
                .max_length(10)
                .build()
                .unwrap(),
        ).unwrap()
        .build()
        .unwrap();

    let (sink, buf) = CaptureSink::new();
    let pipeline = ValidationPipeline::builder(schema)
        .without_console()
        .with_sink(Box::new(sink))
        .build().unwrap();

    let rows = vec![
        JsonTRowBuilder::new().push(JsonTValue::str("ok_label")).build(),       // OK (8 chars)
        JsonTRowBuilder::new().push(JsonTValue::str("ab")).build(),              // too short (2)
        JsonTRowBuilder::new().push(JsonTValue::str("way_too_long_str")).build(), // too long (16)
    ];
    let clean = pipeline.validate_rows(rows);
    pipeline.finish().unwrap();

    assert_eq!(clean.len(), 3, "length warnings must not exclude rows");

    let events = buf.lock().unwrap();
    let violations: Vec<_> = events
        .iter()
        .filter(|e| matches!(&e.kind, EventKind::ConstraintViolation { .. }))
        .collect();
    assert_eq!(violations.len(), 2);
    assert!(violations.iter().all(|e| e.severity == Severity::Warning));
}

// ── 7. Regex constraint violation → Warning, row accepted ─────────────────

#[test]
fn test_regex_constraint_warning_row_accepted() {
    let schema = JsonTSchemaBuilder::straight("User")
        .field(
            JsonTFieldBuilder::scalar("email", ScalarType::Str)
                .regex(r"^[^@]+@[^@]+\.[^@]+$")
                .build()
                .unwrap(),
        ).unwrap()
        .build()
        .unwrap();

    let (sink, buf) = CaptureSink::new();
    let pipeline = ValidationPipeline::builder(schema)
        .without_console()
        .with_sink(Box::new(sink))
        .build().unwrap();

    let rows = vec![
        JsonTRowBuilder::new().push(JsonTValue::str("user@example.com")).build(), // OK
        JsonTRowBuilder::new().push(JsonTValue::str("not-an-email")).build(),      // fails regex
    ];
    let clean = pipeline.validate_rows(rows);
    pipeline.finish().unwrap();

    assert_eq!(clean.len(), 2, "regex warnings must not exclude rows");

    let events = buf.lock().unwrap();
    let violations: Vec<_> = events
        .iter()
        .filter(|e| matches!(&e.kind, EventKind::ConstraintViolation { .. }))
        .collect();
    assert_eq!(violations.len(), 1);
    assert_eq!(violations[0].severity, Severity::Warning);
}

// ── 8. Row rule violation → Warning, row accepted ─────────────────────────

#[test]
fn test_rule_violation_warning_row_accepted() {
    // Rule: price > 0.0
    let rule = JsonTRule::Expression(
        JsonTExpression::gt(
            JsonTExpression::field_name("price"),
            JsonTExpression::literal(JsonTValue::d64(0.0)),
        ),
    );
    let validation = JsonTValidationBlock {
        rules:   vec![rule],
        unique:  vec![],
        dataset: vec![],
    };

    let schema = JsonTSchemaBuilder::straight("Item")
        .field(JsonTFieldBuilder::scalar("price", ScalarType::D64).build().unwrap()).unwrap()
        .validation(validation)
        .build()
        .unwrap();

    let (sink, buf) = CaptureSink::new();
    let pipeline = ValidationPipeline::builder(schema)
        .without_console()
        .with_sink(Box::new(sink))
        .build().unwrap();

    let rows = vec![
        JsonTRowBuilder::new().push(JsonTValue::d64(9.99)).build(),  // passes rule
        JsonTRowBuilder::new().push(JsonTValue::d64(-1.0)).build(),  // violates rule
    ];
    let clean = pipeline.validate_rows(rows);
    pipeline.finish().unwrap();

    assert_eq!(clean.len(), 2, "rule violations are Warnings — both rows kept");

    let events = buf.lock().unwrap();
    let rule_violations: Vec<_> = events
        .iter()
        .filter(|e| matches!(&e.kind, EventKind::RuleViolation { .. }))
        .collect();
    assert_eq!(rule_violations.len(), 1);
    assert_eq!(rule_violations[0].severity, Severity::Warning);
}

// ── 9. Uniqueness violation → row excluded ────────────────────────────────

#[test]
fn test_uniqueness_violation_excluded() {
    let validation = JsonTValidationBlock {
        rules:   vec![],
        unique:  vec![vec![FieldPath::single("id")]],
        dataset: vec![],
    };

    let schema = JsonTSchemaBuilder::straight("Item")
        .field(JsonTFieldBuilder::scalar("id",   ScalarType::I64).build().unwrap()).unwrap()
        .field(JsonTFieldBuilder::scalar("name", ScalarType::Str).build().unwrap()).unwrap()
        .validation(validation)
        .build()
        .unwrap();

    let (sink, buf) = CaptureSink::new();
    let pipeline = ValidationPipeline::builder(schema)
        .without_console()
        .with_sink(Box::new(sink))
        .build().unwrap();

    let rows = vec![
        JsonTRowBuilder::new().push(JsonTValue::i64(1)).push(JsonTValue::str("A")).build(),
        JsonTRowBuilder::new().push(JsonTValue::i64(2)).push(JsonTValue::str("B")).build(),
        JsonTRowBuilder::new().push(JsonTValue::i64(1)).push(JsonTValue::str("C")).build(), // dup id=1
    ];
    let clean = pipeline.validate_rows(rows);
    pipeline.finish().unwrap();

    assert_eq!(clean.len(), 2, "duplicate row must be excluded");

    let events = buf.lock().unwrap();
    let unique_violations: Vec<_> = events
        .iter()
        .filter(|e| matches!(&e.kind, EventKind::UniqueViolation { .. }))
        .collect();
    assert_eq!(unique_violations.len(), 1);
    assert!(unique_violations[0].is_fatal());
}

// ── 10. Constant mismatch → row excluded ──────────────────────────────────

#[test]
fn test_constant_mismatch_excluded() {
    let schema = JsonTSchemaBuilder::straight("Event")
        .field(JsonTFieldBuilder::scalar("id",      ScalarType::I64).build().unwrap()).unwrap()
        .field(
            JsonTFieldBuilder::scalar("version", ScalarType::I32)
                .constant_value(JsonTValue::i32(1))
                .build()
                .unwrap(),
        ).unwrap()
        .build()
        .unwrap();

    let (sink, buf) = CaptureSink::new();
    let pipeline = ValidationPipeline::builder(schema)
        .without_console()
        .with_sink(Box::new(sink))
        .build().unwrap();

    let rows = vec![
        JsonTRowBuilder::new().push(JsonTValue::i64(1)).push(JsonTValue::i32(1)).build(), // OK
        JsonTRowBuilder::new().push(JsonTValue::i64(2)).push(JsonTValue::i32(2)).build(), // wrong version
    ];
    let clean = pipeline.validate_rows(rows);
    pipeline.finish().unwrap();

    assert_eq!(clean.len(), 1);

    let events = buf.lock().unwrap();
    let violations: Vec<_> = events
        .iter()
        .filter(|e| matches!(&e.kind, EventKind::ConstraintViolation { .. }) && e.is_fatal())
        .collect();
    assert_eq!(violations.len(), 1, "constant mismatch must be Fatal");
}

// ── 11. ConditionalRequirement triggered → row excluded ───────────────────

#[test]
fn test_conditional_requirement_excluded() {
    // Rule: if status == "SHIPPED" then tracking_number must be present.
    let condition = JsonTExpression::eq(
        JsonTExpression::field_name("status"),
        JsonTExpression::literal(JsonTValue::str("SHIPPED")),
    );
    let rule = JsonTRule::ConditionalRequirement {
        condition,
        required_fields: vec![FieldPath::single("tracking_number")],
    };
    let validation = JsonTValidationBlock {
        rules:   vec![rule],
        unique:  vec![],
        dataset: vec![],
    };

    let schema = JsonTSchemaBuilder::straight("Shipment")
        .field(JsonTFieldBuilder::scalar("status",          ScalarType::Str).build().unwrap()).unwrap()
        .field(JsonTFieldBuilder::scalar("tracking_number", ScalarType::Str).optional().build().unwrap()).unwrap()
        .validation(validation)
        .build()
        .unwrap();

    let (sink, buf) = CaptureSink::new();
    let pipeline = ValidationPipeline::builder(schema)
        .without_console()
        .with_sink(Box::new(sink))
        .build().unwrap();

    let rows = vec![
        // PENDING — no tracking number required
        JsonTRowBuilder::new().push(JsonTValue::str("PENDING")).push(JsonTValue::Null).build(),
        // SHIPPED — tracking number required but missing → Fatal
        JsonTRowBuilder::new().push(JsonTValue::str("SHIPPED")).push(JsonTValue::Null).build(),
        // SHIPPED — tracking number present → OK
        JsonTRowBuilder::new().push(JsonTValue::str("SHIPPED")).push(JsonTValue::str("TRACK-001")).build(),
    ];
    let clean = pipeline.validate_rows(rows);
    pipeline.finish().unwrap();

    assert_eq!(clean.len(), 2, "SHIPPED row without tracking number must be excluded");

    let events = buf.lock().unwrap();
    let cond_violations: Vec<_> = events
        .iter()
        .filter(|e| matches!(&e.kind, EventKind::ConditionalRequirementViolation { .. }))
        .collect();
    assert_eq!(cond_violations.len(), 1);
    assert!(cond_violations[0].is_fatal());
}

// ── 12. validate_each — streaming callback, no Vec buffering ──────────────

#[test]
fn test_validate_each_streaming() {
    let schema = order_schema();
    let pipeline = ValidationPipeline::builder(schema)
        .without_console()
        .build().unwrap();

    // Supply rows as a lazy iterator — no Vec allocation for input.
    let input = (1i64..=3).map(|i| make_order(i, "Item", 1, 1.0));

    let mut received = Vec::new();
    pipeline.validate_each(input, |row| received.push(row));
    pipeline.finish().unwrap();

    assert_eq!(received.len(), 3, "all three rows should arrive via callback");
}

// ── 13. ProcessCompleted summary event emitted ────────────────────────────

#[test]
fn test_process_completed_event_emitted() {
    let schema = order_schema();
    let (sink, buf) = CaptureSink::new();
    let pipeline = ValidationPipeline::builder(schema)
        .without_console()
        .with_sink(Box::new(sink))
        .build().unwrap();

    let rows = vec![
        make_order(1, "Widget", 10, 9.99),
        // row 2: missing required name
        JsonTRowBuilder::new()
            .push(JsonTValue::i64(2))
            .push(JsonTValue::Null)
            .push(JsonTValue::i32(1))
            .push(JsonTValue::d64(5.0))
            .build(),
    ];
    let clean = pipeline.validate_rows(rows);
    pipeline.finish().unwrap();

    assert_eq!(clean.len(), 1);

    let events = buf.lock().unwrap();
    let completed: Vec<_> = events
        .iter()
        .filter(|e| matches!(&e.kind, EventKind::ProcessCompleted { .. }))
        .collect();
    assert_eq!(completed.len(), 1, "exactly one ProcessCompleted event");

    if let EventKind::ProcessCompleted {
        total_rows,
        valid_rows,
        invalid_rows,
        ..
    } = &completed[0].kind
    {
        assert_eq!(*total_rows, 2);
        assert_eq!(*valid_rows, 1);
        assert_eq!(*invalid_rows, 1);
    } else {
        panic!("expected ProcessCompleted event kind");
    }
}
