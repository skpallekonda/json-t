// =============================================================================
// transform_tests.rs — Integration tests for RowTransformer on JsonTSchema
// =============================================================================

use jsont::{
    BinaryOp, EvalError, FieldPath, JsonTError, JsonTExpression, JsonTSchema, JsonTValue,
    JsonTFieldBuilder, JsonTSchemaBuilder, RenamePair, RowTransformer, ScalarType, SchemaOperation,
    SchemaRegistry, TransformError,
};
use jsont::model::data::JsonTRow;

// ─────────────────────────────────────────────────────────────────────────────
// Helper builders
// ─────────────────────────────────────────────────────────────────────────────

/// Build a straight schema with three scalar fields: id (i64), name (str), age (i32).
fn person_schema() -> JsonTSchema {
    JsonTSchemaBuilder::straight("Person")
        .field_from(
            JsonTFieldBuilder::scalar("id", ScalarType::I64)
        ).unwrap()
        .field_from(
            JsonTFieldBuilder::scalar("name", ScalarType::Str)
        ).unwrap()
        .field_from(
            JsonTFieldBuilder::scalar("age", ScalarType::I32)
        ).unwrap()
        .build()
        .unwrap()
}

/// A row matching person_schema: (1, "Alice", 30)
fn person_row() -> JsonTRow {
    JsonTRow::new(vec![
        JsonTValue::i64(1),
        JsonTValue::str("Alice"),
        JsonTValue::i32(30),
    ])
}

/// Register multiple schemas in a new registry.
fn registry(schemas: Vec<JsonTSchema>) -> SchemaRegistry {
    let mut r = SchemaRegistry::new();
    for s in schemas {
        r.register(s);
    }
    r
}

// ─────────────────────────────────────────────────────────────────────────────
// Straight schema — no-op transform
// ─────────────────────────────────────────────────────────────────────────────

#[test]
fn test_straight_schema_is_noop() {
    let schema = person_schema();
    let row = person_row();
    let reg = registry(vec![schema.clone()]);

    let result = schema.transform(row.clone(), &reg).unwrap();
    assert_eq!(result, row, "straight schema must return row unchanged");
}

// ─────────────────────────────────────────────────────────────────────────────
// Rename
// ─────────────────────────────────────────────────────────────────────────────

#[test]
fn test_rename_single_field() {
    let parent = person_schema();
    let derived = JsonTSchemaBuilder::derived("PersonView", "Person")
        .operation(SchemaOperation::Rename(vec![
            RenamePair {
                from: FieldPath::single("name"),
                to:   "fullName".into(),
            },
        ])).unwrap()
        .build().unwrap();

    let reg = registry(vec![parent, derived.clone()]);
    let result = derived.transform(person_row(), &reg).unwrap();

    // Values must be unchanged; field count must be the same.
    assert_eq!(result.fields, person_row().fields,
        "rename must not change values, only field names in the schema");
}

#[test]
fn test_rename_unknown_field_returns_error() {
    let parent = person_schema();
    let derived = JsonTSchemaBuilder::derived("Bad", "Person")
        .operation(SchemaOperation::Rename(vec![
            RenamePair {
                from: FieldPath::single("nonexistent"),
                to:   "x".into(),
            },
        ])).unwrap()
        .build().unwrap();

    let reg = registry(vec![parent, derived.clone()]);
    let err = derived.transform(person_row(), &reg).unwrap_err();
    assert!(
        matches!(err, JsonTError::Transform(TransformError::FieldNotFound(_))),
        "expected FieldNotFound, got: {err}"
    );
}

// ─────────────────────────────────────────────────────────────────────────────
// Exclude
// ─────────────────────────────────────────────────────────────────────────────

#[test]
fn test_exclude_one_field() {
    let parent = person_schema();
    let derived = JsonTSchemaBuilder::derived("PersonNoAge", "Person")
        .operation(SchemaOperation::Exclude(vec![
            FieldPath::single("age"),
        ])).unwrap()
        .build().unwrap();

    let reg = registry(vec![parent, derived.clone()]);
    let result = derived.transform(person_row(), &reg).unwrap();

    assert_eq!(result.len(), 2, "one field was excluded, so 2 remain");
    assert_eq!(result.fields[0], JsonTValue::i64(1));
    assert_eq!(result.fields[1], JsonTValue::str("Alice"));
}

#[test]
fn test_exclude_multiple_fields() {
    let parent = person_schema();
    let derived = JsonTSchemaBuilder::derived("PersonIdOnly", "Person")
        .operation(SchemaOperation::Exclude(vec![
            FieldPath::single("name"),
            FieldPath::single("age"),
        ])).unwrap()
        .build().unwrap();

    let reg = registry(vec![parent, derived.clone()]);
    let result = derived.transform(person_row(), &reg).unwrap();

    assert_eq!(result.len(), 1);
    assert_eq!(result.fields[0], JsonTValue::i64(1));
}

#[test]
fn test_exclude_unknown_field_returns_error() {
    let parent = person_schema();
    let derived = JsonTSchemaBuilder::derived("Bad", "Person")
        .operation(SchemaOperation::Exclude(vec![
            FieldPath::single("missing"),
        ])).unwrap()
        .build().unwrap();

    let reg = registry(vec![parent, derived.clone()]);
    let err = derived.transform(person_row(), &reg).unwrap_err();
    assert!(
        matches!(err, JsonTError::Transform(TransformError::FieldNotFound(_))),
        "expected FieldNotFound, got: {err}"
    );
}

// ─────────────────────────────────────────────────────────────────────────────
// Project
// ─────────────────────────────────────────────────────────────────────────────

#[test]
fn test_project_subset() {
    let parent = person_schema();
    let derived = JsonTSchemaBuilder::derived("PersonSummary", "Person")
        .operation(SchemaOperation::Project(vec![
            FieldPath::single("id"),
            FieldPath::single("name"),
        ])).unwrap()
        .build().unwrap();

    let reg = registry(vec![parent, derived.clone()]);
    let result = derived.transform(person_row(), &reg).unwrap();

    assert_eq!(result.len(), 2);
    assert_eq!(result.fields[0], JsonTValue::i64(1));
    assert_eq!(result.fields[1], JsonTValue::str("Alice"));
}

#[test]
fn test_project_unknown_field_returns_error() {
    let parent = person_schema();
    let derived = JsonTSchemaBuilder::derived("Bad", "Person")
        .operation(SchemaOperation::Project(vec![
            FieldPath::single("id"),
            FieldPath::single("ghost"),
        ])).unwrap()
        .build().unwrap();

    let reg = registry(vec![parent, derived.clone()]);
    let err = derived.transform(person_row(), &reg).unwrap_err();
    assert!(
        matches!(err, JsonTError::Transform(TransformError::FieldNotFound(_))),
        "expected FieldNotFound, got: {err}"
    );
}

// ─────────────────────────────────────────────────────────────────────────────
// Filter
// ─────────────────────────────────────────────────────────────────────────────

#[test]
fn test_filter_passes_matching_row() {
    let parent = person_schema();
    // age > 18  →  30 > 18 = true
    let filter_expr = JsonTExpression::binary(
        BinaryOp::Gt,
        JsonTExpression::field_name("age"),
        JsonTExpression::literal(JsonTValue::i32(18)),
    );
    let derived = JsonTSchemaBuilder::derived("Adults", "Person")
        .operation(SchemaOperation::Filter(filter_expr)).unwrap()
        .build().unwrap();

    let reg = registry(vec![parent, derived.clone()]);
    let result = derived.transform(person_row(), &reg).unwrap();

    // Filter doesn't change the row, just keeps it.
    assert_eq!(result, person_row());
}

#[test]
fn test_filter_rejects_non_matching_row() {
    let parent = person_schema();
    // age > 50  →  30 > 50 = false
    let filter_expr = JsonTExpression::binary(
        BinaryOp::Gt,
        JsonTExpression::field_name("age"),
        JsonTExpression::literal(JsonTValue::i32(50)),
    );
    let derived = JsonTSchemaBuilder::derived("Seniors", "Person")
        .operation(SchemaOperation::Filter(filter_expr)).unwrap()
        .build().unwrap();

    let reg = registry(vec![parent, derived.clone()]);
    let err = derived.transform(person_row(), &reg).unwrap_err();

    assert!(
        matches!(err, JsonTError::Transform(TransformError::Filtered)),
        "expected Filtered, got: {err}"
    );
}

#[test]
fn test_filter_non_boolean_result_returns_filter_failed() {
    let parent = person_schema();
    // Returning a non-boolean (age itself) is an error.
    let filter_expr = JsonTExpression::field_name("age");
    let derived = JsonTSchemaBuilder::derived("Bad", "Person")
        .operation(SchemaOperation::Filter(filter_expr)).unwrap()
        .build().unwrap();

    let reg = registry(vec![parent, derived.clone()]);
    let err = derived.transform(person_row(), &reg).unwrap_err();

    assert!(
        matches!(
            err,
            JsonTError::Transform(TransformError::FilterFailed(
                EvalError::InvalidExpression(_)
            ))
        ),
        "expected FilterFailed(InvalidExpression), got: {err}"
    );
}

// ─────────────────────────────────────────────────────────────────────────────
// Transform (field value replacement)
// ─────────────────────────────────────────────────────────────────────────────

#[test]
fn test_transform_doubles_age() {
    let parent = person_schema();
    // age * 2
    let expr = JsonTExpression::binary(
        BinaryOp::Mul,
        JsonTExpression::field_name("age"),
        JsonTExpression::literal(JsonTValue::i32(2)),
    );
    let derived = JsonTSchemaBuilder::derived("PersonDoubleAge", "Person")
        .operation(SchemaOperation::Transform {
            target: FieldPath::single("age"),
            expr,
        }).unwrap()
        .build().unwrap();

    let reg = registry(vec![parent, derived.clone()]);
    let result = derived.transform(person_row(), &reg).unwrap();

    assert_eq!(result.len(), 3);
    assert_eq!(result.fields[0], JsonTValue::i64(1));
    assert_eq!(result.fields[1], JsonTValue::str("Alice"));
    // 30 * 2 = 60 (eval returns d64 for arithmetic)
    match &result.fields[2] {
        JsonTValue::Number(n) => {
            let v = n.as_f64();
            assert!((v - 60.0).abs() < 1e-9, "expected 60.0, got {v}");
        }
        other => panic!("expected Number, got {other:?}"),
    }
}

#[test]
fn test_transform_unknown_field_returns_error() {
    let parent = person_schema();
    let derived = JsonTSchemaBuilder::derived("Bad", "Person")
        .operation(SchemaOperation::Transform {
            target: FieldPath::single("ghost"),
            expr: JsonTExpression::literal(JsonTValue::i32(0)),
        }).unwrap()
        .build().unwrap();

    let reg = registry(vec![parent, derived.clone()]);
    let err = derived.transform(person_row(), &reg).unwrap_err();
    assert!(
        matches!(err, JsonTError::Transform(TransformError::FieldNotFound(_))),
        "expected FieldNotFound, got: {err}"
    );
}

// ─────────────────────────────────────────────────────────────────────────────
// Chained derivation: Derived → Derived → Straight
// ─────────────────────────────────────────────────────────────────────────────

#[test]
fn test_chained_derivation() {
    // Person (Straight): id, name, age
    // PersonNoAge (Derived from Person): Exclude(age)  →  id, name
    // PersonSummary (Derived from PersonNoAge): Rename(name → fullName)  →  id, fullName
    let person     = person_schema();
    let no_age = JsonTSchemaBuilder::derived("PersonNoAge", "Person")
        .operation(SchemaOperation::Exclude(vec![FieldPath::single("age")])).unwrap()
        .build().unwrap();
    let summary = JsonTSchemaBuilder::derived("PersonSummary", "PersonNoAge")
        .operation(SchemaOperation::Rename(vec![RenamePair {
            from: FieldPath::single("name"),
            to:   "fullName".into(),
        }])).unwrap()
        .build().unwrap();

    let reg = registry(vec![person, no_age.clone(), summary.clone()]);

    // Transform with PersonNoAge first.
    let after_no_age = no_age.transform(person_row(), &reg).unwrap();
    assert_eq!(after_no_age.len(), 2, "age excluded");

    // Then transform with PersonSummary (input is PersonNoAge output).
    let final_row = summary.transform(after_no_age, &reg).unwrap();
    assert_eq!(final_row.len(), 2, "rename does not change count");
    assert_eq!(final_row.fields[0], JsonTValue::i64(1));
    assert_eq!(final_row.fields[1], JsonTValue::str("Alice"));
}

// ─────────────────────────────────────────────────────────────────────────────
// Error: unknown parent schema
// ─────────────────────────────────────────────────────────────────────────────

#[test]
fn test_unknown_parent_schema_returns_error() {
    let derived = JsonTSchemaBuilder::derived("Orphan", "GhostParent")
        .operation(SchemaOperation::Exclude(vec![FieldPath::single("x")])).unwrap()
        .build().unwrap();

    // Registry does NOT contain GhostParent.
    let reg = registry(vec![derived.clone()]);
    let err = derived.transform(person_row(), &reg).unwrap_err();

    assert!(
        matches!(
            err,
            JsonTError::Transform(TransformError::UnknownSchema(ref name)) if name == "GhostParent"
        ),
        "expected UnknownSchema(GhostParent), got: {err}"
    );
}

// ─────────────────────────────────────────────────────────────────────────────
// Error: cyclic derivation
// ─────────────────────────────────────────────────────────────────────────────

#[test]
fn test_cyclic_derivation_returns_error() {
    // A derives from B, B derives from A.
    let schema_a = JsonTSchemaBuilder::derived("A", "B")
        .operation(SchemaOperation::Exclude(vec![FieldPath::single("x")])).unwrap()
        .build().unwrap();
    let schema_b = JsonTSchemaBuilder::derived("B", "A")
        .operation(SchemaOperation::Exclude(vec![FieldPath::single("y")])).unwrap()
        .build().unwrap();

    let reg = registry(vec![schema_a.clone(), schema_b]);

    let err = schema_a
        .transform(JsonTRow::new(vec![JsonTValue::i64(1), JsonTValue::i64(2)]), &reg)
        .unwrap_err();

    assert!(
        matches!(err, JsonTError::Transform(TransformError::CyclicDerivation(_))),
        "expected CyclicDerivation, got: {err}"
    );
}

// ─────────────────────────────────────────────────────────────────────────────
// Combined operations: Exclude + Rename in one pipeline
// ─────────────────────────────────────────────────────────────────────────────

#[test]
fn test_exclude_then_rename() {
    let parent = person_schema();
    let derived = JsonTSchemaBuilder::derived("ContactCard", "Person")
        .operation(SchemaOperation::Exclude(vec![FieldPath::single("age")])).unwrap()
        .operation(SchemaOperation::Rename(vec![RenamePair {
            from: FieldPath::single("name"),
            to:   "displayName".into(),
        }])).unwrap()
        .build().unwrap();

    let reg = registry(vec![parent, derived.clone()]);
    let result = derived.transform(person_row(), &reg).unwrap();

    assert_eq!(result.len(), 2);
    assert_eq!(result.fields[0], JsonTValue::i64(1));
    assert_eq!(result.fields[1], JsonTValue::str("Alice"));
}

// ─────────────────────────────────────────────────────────────────────────────
// Filter then Transform
// ─────────────────────────────────────────────────────────────────────────────

#[test]
fn test_filter_then_transform() {
    let parent = person_schema();
    // Filter: age > 18, then Transform: age = age + 1
    let filter_expr = JsonTExpression::binary(
        BinaryOp::Gt,
        JsonTExpression::field_name("age"),
        JsonTExpression::literal(JsonTValue::i32(18)),
    );
    let transform_expr = JsonTExpression::binary(
        BinaryOp::Add,
        JsonTExpression::field_name("age"),
        JsonTExpression::literal(JsonTValue::i32(1)),
    );
    let derived = JsonTSchemaBuilder::derived("OlderAdults", "Person")
        .operation(SchemaOperation::Filter(filter_expr)).unwrap()
        .operation(SchemaOperation::Transform {
            target: FieldPath::single("age"),
            expr:   transform_expr,
        }).unwrap()
        .build().unwrap();

    let reg = registry(vec![parent, derived.clone()]);
    let result = derived.transform(person_row(), &reg).unwrap();

    // age was 30, should now be 31 (as f64 from arithmetic eval)
    match &result.fields[2] {
        JsonTValue::Number(n) => {
            let v = n.as_f64();
            assert!((v - 31.0).abs() < 1e-9, "expected 31.0, got {v}");
        }
        other => panic!("expected Number for age, got {other:?}"),
    }
}

// =============================================================================
// validate_schema — schema-level static analysis
// =============================================================================

// ── Valid pipelines ───────────────────────────────────────────────────────────

#[test]
fn test_validate_ops_straight_schema_always_ok() {
    let schema = person_schema();
    let reg = registry(vec![schema.clone()]);
    schema.validate_schema(&reg).unwrap();
}

#[test]
fn test_validate_ops_exclude_then_filter_remaining_field_is_valid() {
    let parent = person_schema();
    // Exclude age, then filter on id (id is still available — valid).
    let filter_expr = JsonTExpression::binary(
        BinaryOp::Gt,
        JsonTExpression::field_name("id"),
        JsonTExpression::literal(JsonTValue::i64(0)),
    );
    let derived = JsonTSchemaBuilder::derived("Valid", "Person")
        .operation(SchemaOperation::Exclude(vec![FieldPath::single("age")])).unwrap()
        .operation(SchemaOperation::Filter(filter_expr)).unwrap()
        .build().unwrap();

    let reg = registry(vec![parent, derived.clone()]);
    derived.validate_schema(&reg).unwrap();
}

#[test]
fn test_validate_ops_transform_using_available_field_is_valid() {
    let parent = person_schema();
    let expr = JsonTExpression::binary(
        BinaryOp::Add,
        JsonTExpression::field_name("age"),
        JsonTExpression::literal(JsonTValue::i32(1)),
    );
    let derived = JsonTSchemaBuilder::derived("BumpAge", "Person")
        .operation(SchemaOperation::Transform {
            target: FieldPath::single("age"),
            expr,
        }).unwrap()
        .build().unwrap();

    let reg = registry(vec![parent, derived.clone()]);
    derived.validate_schema(&reg).unwrap();
}

// ── Filter references excluded field ─────────────────────────────────────────

#[test]
fn test_validate_ops_filter_references_excluded_field() {
    // The exact scenario described: Exclude(name, age) then Filter(age > 18).
    let parent = person_schema(); // fields: id, name, age
    let filter_expr = JsonTExpression::binary(
        BinaryOp::Gt,
        JsonTExpression::field_name("age"), // age will be excluded by the time Filter runs
        JsonTExpression::literal(JsonTValue::i32(18)),
    );
    let derived = JsonTSchemaBuilder::derived("Bad", "Person")
        .operation(SchemaOperation::Exclude(vec![
            FieldPath::single("name"),
            FieldPath::single("age"),  // age excluded here
        ])).unwrap()
        .operation(SchemaOperation::Filter(filter_expr)).unwrap() // age referenced here
        .build().unwrap();

    let reg = registry(vec![parent, derived.clone()]);
    let err = derived.validate_schema(&reg).unwrap_err();

    assert!(
        matches!(err, JsonTError::Transform(TransformError::FieldNotFound(ref msg))
            if msg.contains("age") && msg.contains("Filter")),
        "expected FieldNotFound mentioning 'age' and 'Filter', got: {err}"
    );
}

#[test]
fn test_validate_ops_filter_references_projected_away_field() {
    let parent = person_schema(); // id, name, age
    // Project keeps only id and name; filter references age (gone).
    let filter_expr = JsonTExpression::binary(
        BinaryOp::Gt,
        JsonTExpression::field_name("age"),
        JsonTExpression::literal(JsonTValue::i32(0)),
    );
    let derived = JsonTSchemaBuilder::derived("Bad", "Person")
        .operation(SchemaOperation::Project(vec![
            FieldPath::single("id"),
            FieldPath::single("name"),
        ])).unwrap()
        .operation(SchemaOperation::Filter(filter_expr)).unwrap()
        .build().unwrap();

    let reg = registry(vec![parent, derived.clone()]);
    let err = derived.validate_schema(&reg).unwrap_err();

    assert!(
        matches!(err, JsonTError::Transform(TransformError::FieldNotFound(ref msg))
            if msg.contains("age") && msg.contains("Filter")),
        "expected FieldNotFound for 'age' in Filter, got: {err}"
    );
}

// ── Transform expression references excluded field ────────────────────────────

#[test]
fn test_validate_ops_transform_expr_references_excluded_field() {
    let parent = person_schema(); // id, name, age
    // Exclude age, then try to use age in Transform expression for id.
    let expr = JsonTExpression::binary(
        BinaryOp::Add,
        JsonTExpression::field_name("age"),  // excluded above
        JsonTExpression::literal(JsonTValue::i32(1)),
    );
    let derived = JsonTSchemaBuilder::derived("Bad", "Person")
        .operation(SchemaOperation::Exclude(vec![FieldPath::single("age")])).unwrap()
        .operation(SchemaOperation::Transform {
            target: FieldPath::single("id"),
            expr,
        }).unwrap()
        .build().unwrap();

    let reg = registry(vec![parent, derived.clone()]);
    let err = derived.validate_schema(&reg).unwrap_err();

    assert!(
        matches!(err, JsonTError::Transform(TransformError::FieldNotFound(ref msg))
            if msg.contains("age") && msg.contains("Transform")),
        "expected FieldNotFound for 'age' in Transform expression, got: {err}"
    );
}

#[test]
fn test_validate_ops_transform_target_was_excluded() {
    let parent = person_schema();
    let derived = JsonTSchemaBuilder::derived("Bad", "Person")
        .operation(SchemaOperation::Exclude(vec![FieldPath::single("age")])).unwrap()
        .operation(SchemaOperation::Transform {
            target: FieldPath::single("age"), // target no longer exists
            expr: JsonTExpression::literal(JsonTValue::i32(99)),
        }).unwrap()
        .build().unwrap();

    let reg = registry(vec![parent, derived.clone()]);
    let err = derived.validate_schema(&reg).unwrap_err();

    assert!(
        matches!(err, JsonTError::Transform(TransformError::FieldNotFound(ref msg))
            if msg.contains("age") && msg.contains("Transform")),
        "expected FieldNotFound for excluded target 'age', got: {err}"
    );
}

// ── Rename makes field available under new name ───────────────────────────────

#[test]
fn test_validate_ops_filter_uses_renamed_field_valid() {
    let parent = person_schema(); // id, name, age
    // Rename age → years, then filter on years (the new name — valid).
    let filter_expr = JsonTExpression::binary(
        BinaryOp::Gt,
        JsonTExpression::field_name("years"),
        JsonTExpression::literal(JsonTValue::i32(18)),
    );
    let derived = JsonTSchemaBuilder::derived("Valid", "Person")
        .operation(SchemaOperation::Rename(vec![RenamePair {
            from: FieldPath::single("age"),
            to:   "years".into(),
        }])).unwrap()
        .operation(SchemaOperation::Filter(filter_expr)).unwrap()
        .build().unwrap();

    let reg = registry(vec![parent, derived.clone()]);
    derived.validate_schema(&reg).unwrap();
}

#[test]
fn test_validate_ops_filter_uses_old_name_after_rename_fails() {
    let parent = person_schema(); // id, name, age
    // Rename age → years, then filter on age (old name — no longer valid).
    let filter_expr = JsonTExpression::binary(
        BinaryOp::Gt,
        JsonTExpression::field_name("age"), // old name, gone after rename
        JsonTExpression::literal(JsonTValue::i32(18)),
    );
    let derived = JsonTSchemaBuilder::derived("Bad", "Person")
        .operation(SchemaOperation::Rename(vec![RenamePair {
            from: FieldPath::single("age"),
            to:   "years".into(),
        }])).unwrap()
        .operation(SchemaOperation::Filter(filter_expr)).unwrap()
        .build().unwrap();

    let reg = registry(vec![parent, derived.clone()]);
    let err = derived.validate_schema(&reg).unwrap_err();

    assert!(
        matches!(err, JsonTError::Transform(TransformError::FieldNotFound(ref msg))
            if msg.contains("age") && msg.contains("Filter")),
        "expected FieldNotFound for stale name 'age' after Rename, got: {err}"
    );
}

// =============================================================================
// validate_schema — straight schema checks
// =============================================================================

// ── Object field schema_ref ───────────────────────────────────────────────────

#[test]
fn test_validate_schema_object_ref_exists_is_valid() {
    // Address is in the registry; Person.address field references it.
    let address = JsonTSchemaBuilder::straight("Address")
        .field_from(JsonTFieldBuilder::scalar("city", ScalarType::Str)).unwrap()
        .build().unwrap();

    let person_with_address = JsonTSchemaBuilder::straight("PersonWithAddr")
        .field_from(JsonTFieldBuilder::scalar("id", ScalarType::I64)).unwrap()
        .field_from(
            jsont::JsonTFieldBuilder::object("address", "Address")
        ).unwrap()
        .build().unwrap();

    let reg = registry(vec![address, person_with_address.clone()]);
    person_with_address.validate_schema(&reg).unwrap();
}

#[test]
fn test_validate_schema_object_ref_missing_returns_error() {
    let person_with_address = JsonTSchemaBuilder::straight("PersonWithAddr")
        .field_from(JsonTFieldBuilder::scalar("id", ScalarType::I64)).unwrap()
        .field_from(
            jsont::JsonTFieldBuilder::object("address", "Address") // Address not in registry
        ).unwrap()
        .build().unwrap();

    // Registry intentionally does NOT contain Address.
    let reg = registry(vec![person_with_address.clone()]);
    let err = person_with_address.validate_schema(&reg).unwrap_err();

    assert!(
        matches!(
            err,
            JsonTError::Transform(TransformError::UnknownSchema(ref msg))
                if msg.contains("address") && msg.contains("Address")
        ),
        "expected UnknownSchema mentioning field 'address' and schema 'Address', got: {err}"
    );
}

// ── Validation rule FieldRefs ─────────────────────────────────────────────────

#[test]
fn test_validate_schema_rule_expr_valid_field_refs() {
    use jsont::model::validation::JsonTValidationBlock;

    let schema = JsonTSchemaBuilder::straight("Order")
        .field_from(JsonTFieldBuilder::scalar("qty",   ScalarType::I32)).unwrap()
        .field_from(JsonTFieldBuilder::scalar("price", ScalarType::D64)).unwrap()
        .validation(JsonTValidationBlock {
            rules: vec![
                jsont::model::validation::JsonTRule::Expression(
                    JsonTExpression::binary(
                        BinaryOp::Gt,
                        JsonTExpression::field_name("qty"),
                        JsonTExpression::literal(JsonTValue::i32(0)),
                    )
                ),
            ],
            unique:  vec![],
            dataset: vec![],
        })
        .build().unwrap();

    let reg = registry(vec![schema.clone()]);
    schema.validate_schema(&reg).unwrap();
}

#[test]
fn test_validate_schema_rule_expr_undeclared_field_returns_error() {
    use jsont::model::validation::JsonTValidationBlock;

    let schema = JsonTSchemaBuilder::straight("Order")
        .field_from(JsonTFieldBuilder::scalar("qty", ScalarType::I32)).unwrap()
        .validation(JsonTValidationBlock {
            rules: vec![
                jsont::model::validation::JsonTRule::Expression(
                    JsonTExpression::binary(
                        BinaryOp::Gt,
                        JsonTExpression::field_name("ghost"), // not a declared field
                        JsonTExpression::literal(JsonTValue::i32(0)),
                    )
                ),
            ],
            unique:  vec![],
            dataset: vec![],
        })
        .build().unwrap();

    let reg = registry(vec![schema.clone()]);
    let err = schema.validate_schema(&reg).unwrap_err();

    assert!(
        matches!(err, JsonTError::Transform(TransformError::FieldNotFound(ref msg))
            if msg.contains("ghost") && msg.contains("rule #1")),
        "expected FieldNotFound for 'ghost' in rule #1, got: {err}"
    );
}

// ── Conditional requirement: condition + required_fields both checked ──────────

#[test]
fn test_validate_schema_conditional_requirement_undeclared_required_field() {
    use jsont::model::validation::{JsonTRule, JsonTValidationBlock};

    let schema = JsonTSchemaBuilder::straight("Order")
        .field_from(JsonTFieldBuilder::scalar("status", ScalarType::Str)).unwrap()
        .field_from(JsonTFieldBuilder::scalar("qty",    ScalarType::I32)).unwrap()
        .validation(JsonTValidationBlock {
            rules: vec![
                JsonTRule::ConditionalRequirement {
                    condition: JsonTExpression::binary(
                        BinaryOp::Gt,
                        JsonTExpression::field_name("qty"),
                        JsonTExpression::literal(JsonTValue::i32(0)),
                    ),
                    required_fields: vec![
                        FieldPath::single("discount"), // not declared
                    ],
                },
            ],
            unique:  vec![],
            dataset: vec![],
        })
        .build().unwrap();

    let reg = registry(vec![schema.clone()]);
    let err = schema.validate_schema(&reg).unwrap_err();

    assert!(
        matches!(err, JsonTError::Transform(TransformError::FieldNotFound(ref msg))
            if msg.contains("discount") && msg.contains("rule #1")),
        "expected FieldNotFound for required field 'discount', got: {err}"
    );
}

// ── Unique constraint paths ───────────────────────────────────────────────────

#[test]
fn test_validate_schema_unique_undeclared_field_returns_error() {
    use jsont::model::validation::JsonTValidationBlock;

    let schema = JsonTSchemaBuilder::straight("Order")
        .field_from(JsonTFieldBuilder::scalar("id",  ScalarType::I64)).unwrap()
        .field_from(JsonTFieldBuilder::scalar("qty", ScalarType::I32)).unwrap()
        .validation(JsonTValidationBlock {
            rules:   vec![],
            unique:  vec![vec![FieldPath::single("ref_code")]], // not declared
            dataset: vec![],
        })
        .build().unwrap();

    let reg = registry(vec![schema.clone()]);
    let err = schema.validate_schema(&reg).unwrap_err();

    assert!(
        matches!(err, JsonTError::Transform(TransformError::FieldNotFound(ref msg))
            if msg.contains("ref_code") && msg.contains("unique")),
        "expected FieldNotFound for 'ref_code' in unique constraint, got: {err}"
    );
}

// ── Derived schema: validation block checked against output fields ─────────────

#[test]
fn test_validate_schema_derived_validation_block_uses_output_fields() {
    use jsont::model::validation::JsonTValidationBlock;

    let parent = person_schema(); // id, name, age

    // Exclude age; then add a validation rule that references age — should fail.
    let derived = JsonTSchemaBuilder::derived("NoAge", "Person")
        .operation(SchemaOperation::Exclude(vec![FieldPath::single("age")])).unwrap()
        .validation(JsonTValidationBlock {
            rules: vec![
                jsont::model::validation::JsonTRule::Expression(
                    JsonTExpression::binary(
                        BinaryOp::Gt,
                        JsonTExpression::field_name("age"), // age was excluded
                        JsonTExpression::literal(JsonTValue::i32(0)),
                    )
                ),
            ],
            unique:  vec![],
            dataset: vec![],
        })
        .build().unwrap();

    let reg = registry(vec![parent, derived.clone()]);
    let err = derived.validate_schema(&reg).unwrap_err();

    assert!(
        matches!(err, JsonTError::Transform(TransformError::FieldNotFound(ref msg))
            if msg.contains("age") && msg.contains("rule #1")),
        "expected FieldNotFound for 'age' in rule (excluded by prior op), got: {err}"
    );
}
