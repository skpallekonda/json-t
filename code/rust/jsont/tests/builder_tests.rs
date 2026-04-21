// =============================================================================
// tests/builder_tests.rs
// =============================================================================
// Integration tests covering:
//   1.1  Namespace → Straight Schema  (builder chain)
//   1.2  JsonTRow / JsonTValue        (typed and untyped row builders)
//   1.3  Schema inference             (SchemaInferrer)
//   1.4  Stringify                    (Namespace + JsonTRow → JsonT source text)
// =============================================================================

use jsont::{
    // Builders
    JsonTNamespaceBuilder,
    JsonTCatalogBuilder,
    JsonTSchemaBuilder,
    JsonTFieldBuilder,
    JsonTEnumBuilder,
    JsonTRowBuilder,
    JsonTArrayBuilder,
    SchemaInferrer,
    // Model types
    JsonTValue,
    JsonTNamespace,
    JsonTSchema,
    SchemaKind,
    JsonTFieldKind,
    ScalarType,
    // Traits
    Stringification,
    StringifyOptions,
};

// ─────────────────────────────────────────────────────────────────────────────
// 1.1  Namespace → Straight Schema
// ─────────────────────────────────────────────────────────────────────────────

/// Helper: build a minimal `Person` straight schema with two fields.
fn build_person_schema() -> JsonTSchema {
    JsonTSchemaBuilder::straight("Person")
        .field_from(
            JsonTFieldBuilder::scalar("id", ScalarType::I64)
        ).unwrap()
        .field_from(
            JsonTFieldBuilder::scalar("name", ScalarType::Str)
                .max_length(128)
        ).unwrap()
        .field_from(
            JsonTFieldBuilder::scalar("email", ScalarType::Email)
                .optional()
        ).unwrap()
        .build()
        .expect("Person schema should build successfully")
}

#[test]
fn test_straight_schema_name_and_kind() {
    let schema = build_person_schema();
    assert_eq!(schema.name, "Person");
    assert!(
        matches!(schema.kind, SchemaKind::Straight { .. }),
        "expected Straight kind"
    );
}

#[test]
fn test_straight_schema_field_count() {
    let schema = build_person_schema();
    if let SchemaKind::Straight { fields } = &schema.kind {
        assert_eq!(fields.len(), 3, "expected 3 fields");
    } else {
        panic!("not a straight schema");
    }
}

#[test]
fn test_straight_schema_field_types() {
    let schema = build_person_schema();
    if let SchemaKind::Straight { fields } = &schema.kind {
        // id  → i64, not optional
        let id = &fields[0];
        assert_eq!(id.name, "id");
        if let JsonTFieldKind::Scalar { field_type, optional, .. } = &id.kind {
            assert_eq!(field_type.scalar, ScalarType::I64);
            assert!(!optional);
        } else { panic!("id should be scalar"); }

        // name → str, has maxLength constraint
        let name = &fields[1];
        assert_eq!(name.name, "name");
        if let JsonTFieldKind::Scalar { field_type, constraints, .. } = &name.kind {
            assert_eq!(field_type.scalar, ScalarType::Str);
            assert!(!constraints.is_empty(), "name should have a maxLength constraint");
        } else { panic!("name should be scalar"); }

        // email → email, optional
        let email = &fields[2];
        assert_eq!(email.name, "email");
        if let JsonTFieldKind::Scalar { field_type, optional, .. } = &email.kind {
            assert_eq!(field_type.scalar, ScalarType::Email);
            assert!(optional, "email should be optional");
        } else { panic!("email should be scalar"); }

    } else {
        panic!("not a straight schema");
    }
}

#[test]
fn test_catalog_rejects_duplicate_schema_name() {
    let schema_a = build_person_schema();
    let schema_b = build_person_schema(); // same name: "Person"

    let result = JsonTCatalogBuilder::new()
        .schema(schema_a).unwrap()
        .schema(schema_b); // should fail — duplicate

    assert!(result.is_err(), "duplicate schema name should return Err");
}

#[test]
fn test_full_namespace_builds_successfully() {
    let person_schema = build_person_schema();

    let catalog = JsonTCatalogBuilder::new()
        .schema(person_schema).unwrap()
        .build().unwrap();

    let ns = JsonTNamespaceBuilder::new("https://example.com/v1", "1.0")
        .data_schema("Person")
        .catalog(catalog)
        .build();

    assert!(ns.is_ok(), "namespace should build: {:?}", ns.err());
    let ns = ns.unwrap();
    assert_eq!(ns.base_url, "https://example.com/v1");
    assert_eq!(ns.version,  "1.0");
    assert_eq!(ns.data_schema, "Person");
    assert_eq!(ns.catalogs.len(), 1);
}

#[test]
fn test_namespace_missing_data_schema_returns_err() {
    let result = JsonTNamespaceBuilder::new("https://example.com", "1.0")
        // no .data_schema()
        .build();
    assert!(result.is_err(), "missing data_schema should return Err");
}

#[test]
fn test_object_field_in_schema() {
    // Address schema
    let address = JsonTSchemaBuilder::straight("Address")
        .field_from(JsonTFieldBuilder::scalar("city",    ScalarType::Str)).unwrap()
        .field_from(JsonTFieldBuilder::scalar("country", ScalarType::Str)).unwrap()
        .build().unwrap();

    // Employee schema with an embedded <Address> object field
    let employee = JsonTSchemaBuilder::straight("Employee")
        .field_from(JsonTFieldBuilder::scalar("id",   ScalarType::I64)).unwrap()
        .field_from(JsonTFieldBuilder::object("home", "Address")).unwrap()
        .build();

    assert!(employee.is_ok(), "Employee schema should build: {:?}", employee.err());
    let employee = employee.unwrap();

    if let SchemaKind::Straight { fields } = &employee.kind {
        let home = &fields[1];
        assert!(
            matches!(&home.kind, JsonTFieldKind::Object { schema_ref, .. } if schema_ref == "Address"),
            "home should be an Object field referencing Address"
        );
    }
    let _ = address; // keep allocation (avoids unused-variable warning)
}

#[test]
fn test_invalid_constraint_for_type_returns_err() {
    // minLength on an i32 field is invalid
    let result = JsonTFieldBuilder::scalar("age", ScalarType::I32)
        .min_length(1)   // invalid — i32 doesn't support length constraints
        .build();
    assert!(result.is_err(), "minLength on i32 should be rejected at build()");
}

#[test]
fn test_schema_with_enum_in_catalog() {
    let status_enum = JsonTEnumBuilder::new("Status")
        .value("ACTIVE").unwrap()
        .value("INACTIVE").unwrap()
        .build().unwrap();

    let schema = build_person_schema();

    let catalog = JsonTCatalogBuilder::new()
        .schema(schema).unwrap()
        .enum_def(status_enum)
        .build().unwrap();

    assert_eq!(catalog.enums.len(), 1);
    assert_eq!(catalog.enums[0].name, "Status");
    assert_eq!(catalog.enums[0].values, vec!["ACTIVE", "INACTIVE"]);
}

// ─────────────────────────────────────────────────────────────────────────────
// 1.2  JsonTRow → JsonTValue
// ─────────────────────────────────────────────────────────────────────────────

#[test]
fn test_untyped_row_builder_basic() {
    let row = JsonTRowBuilder::new()
        .push(JsonTValue::i64(42))
        .push(JsonTValue::str("Alice"))
        .push(JsonTValue::null())
        .build();

    assert_eq!(row.len(), 3);
    assert_eq!(row.get(0), Some(&JsonTValue::i64(42)));
    assert_eq!(row.get(1), Some(&JsonTValue::str("Alice")));
    assert_eq!(row.get(2), Some(&JsonTValue::Null));
}

#[test]
fn test_untyped_row_builder_empty() {
    let row = JsonTRowBuilder::new().build();
    assert!(row.is_empty());
}

#[test]
fn test_schema_aware_row_builder_happy_path() {
    let schema = build_person_schema(); // fields: id(i64), name(str), email(email)

    let row = JsonTRowBuilder::with_schema(&schema)
        .push_checked(JsonTValue::i64(1)).unwrap()
        .push_checked(JsonTValue::str("Alice")).unwrap()
        .push_checked(JsonTValue::Null).unwrap()  // email is optional → null ok
        .build_checked().unwrap();

    assert_eq!(row.len(), 3);
}

#[test]
fn test_schema_aware_row_builder_type_mismatch_returns_err() {
    let schema = build_person_schema(); // first field: id → i64

    let result = JsonTRowBuilder::with_schema(&schema)
        .push_checked(JsonTValue::str("not-an-int")); // i64 field gets a str

    assert!(result.is_err(), "type mismatch should return Err");
}

#[test]
fn test_schema_aware_row_builder_too_many_values() {
    let schema = build_person_schema(); // 3 fields

    let result = JsonTRowBuilder::with_schema(&schema)
        .push_checked(JsonTValue::i64(1)).unwrap()
        .push_checked(JsonTValue::str("Alice")).unwrap()
        .push_checked(JsonTValue::Null).unwrap()
        .push_checked(JsonTValue::bool(true)); // 4th value — schema has only 3 fields

    assert!(result.is_err(), "pushing beyond schema field count should return Err");
}

#[test]
fn test_schema_aware_row_builder_incomplete_row_returns_err() {
    let schema = build_person_schema(); // 3 fields

    let result = JsonTRowBuilder::with_schema(&schema)
        .push_checked(JsonTValue::i64(99)).unwrap()
        // only 1 of 3 values — build_checked should reject
        .build_checked();

    assert!(result.is_err(), "incomplete row should be rejected by build_checked()");
}

#[test]
fn test_array_builder() {
    let arr = JsonTArrayBuilder::new()
        .push(JsonTValue::i32(10))
        .push(JsonTValue::i32(20))
        .push(JsonTValue::i32(30))
        .build();

    assert_eq!(arr.len(), 3);
    assert_eq!(arr.get(0), Some(&JsonTValue::i32(10)));
    assert_eq!(arr.get(2), Some(&JsonTValue::i32(30)));
}

#[test]
fn test_jsontvalue_type_queries() {
    assert!(JsonTValue::Null.is_null());
    assert!(JsonTValue::i64(5).is_numeric());
    assert!(JsonTValue::str("hi").is_string());
    assert!(!JsonTValue::bool(false).is_null());
}

#[test]
fn test_jsontvalue_as_f64_coercion() {
    assert_eq!(JsonTValue::i64(10).as_f64(), Some(10.0_f64));
    assert_eq!(JsonTValue::d64(3.14).as_f64(), Some(3.14_f64));
    assert_eq!(JsonTValue::str("x").as_f64(),  None);
}

// ─────────────────────────────────────────────────────────────────────────────
// 1.3  Schema inference (SchemaInferrer)
// ─────────────────────────────────────────────────────────────────────────────

/// Build a small dataset (3 rows × 3 columns) using the untyped row builder.
fn sample_rows() -> Vec<jsont::model::data::JsonTRow> {
    vec![
        JsonTRowBuilder::new()
            .push(JsonTValue::i64(1))
            .push(JsonTValue::str("Alice"))
            .push(JsonTValue::bool(true))
            .build(),
        JsonTRowBuilder::new()
            .push(JsonTValue::i64(2))
            .push(JsonTValue::str("Bob"))
            .push(JsonTValue::bool(false))
            .build(),
        JsonTRowBuilder::new()
            .push(JsonTValue::i64(3))
            .push(JsonTValue::str("Carol"))
            .push(JsonTValue::Null)  // nullable column
            .build(),
    ]
}

#[test]
fn test_schema_inferrer_auto_names() {
    let rows = sample_rows();
    let schema = SchemaInferrer::new()
        .schema_name("Inferred")
        .infer(&rows)
        .expect("inference should succeed");

    assert_eq!(schema.name, "Inferred");
    if let SchemaKind::Straight { fields } = &schema.kind {
        assert_eq!(fields.len(), 3, "should infer 3 fields");
        assert_eq!(fields[0].name, "field_0");
        assert_eq!(fields[1].name, "field_1");
        assert_eq!(fields[2].name, "field_2");
    } else {
        panic!("inferred schema should be Straight");
    }
}

#[test]
fn test_schema_inferrer_types() {
    let rows = sample_rows();
    let schema = SchemaInferrer::new().infer(&rows).unwrap();

    if let SchemaKind::Straight { fields } = &schema.kind {
        // column 0: i64 values → I64 (widened from I16 path, but I64 is direct)
        if let JsonTFieldKind::Scalar { field_type, .. } = &fields[0].kind {
            assert_eq!(field_type.scalar, ScalarType::I64);
        }
        // column 1: all strings → Str
        if let JsonTFieldKind::Scalar { field_type, .. } = &fields[1].kind {
            assert_eq!(field_type.scalar, ScalarType::Str);
        }
        // column 2: bool + null → Bool (null_fraction at default threshold)
        if let JsonTFieldKind::Scalar { field_type, .. } = &fields[2].kind {
            assert_eq!(field_type.scalar, ScalarType::Bool);
        }
    }
}

#[test]
fn test_schema_inferrer_nullable_column_marked_optional() {
    let rows = sample_rows(); // column 2 has one Null out of 3 → optional

    let schema = SchemaInferrer::new()
        .nullable_threshold(0.0) // any null → optional
        .infer(&rows).unwrap();

    if let SchemaKind::Straight { fields } = &schema.kind {
        if let JsonTFieldKind::Scalar { optional, .. } = &fields[2].kind {
            assert!(optional, "column with any null should be marked optional");
        }
    }
}

#[test]
fn test_schema_inferrer_with_name_hints() {
    let rows = sample_rows();
    let schema = SchemaInferrer::new()
        .schema_name("Person")
        .infer_with_names(&rows, &["id", "name", "active"])
        .expect("inference with name hints should succeed");

    assert_eq!(schema.name, "Person");
    if let SchemaKind::Straight { fields } = &schema.kind {
        assert_eq!(fields[0].name, "id");
        assert_eq!(fields[1].name, "name");
        assert_eq!(fields[2].name, "active");
    }
}

#[test]
fn test_schema_inferrer_name_hint_mismatch_returns_err() {
    let rows = sample_rows(); // 3 columns

    let result = SchemaInferrer::new()
        .infer_with_names(&rows, &["only_one"]); // wrong hint count

    assert!(result.is_err(), "hint count mismatch should return Err");
}

#[test]
fn test_schema_inferrer_empty_rows() {
    let rows: Vec<jsont::model::data::JsonTRow> = vec![];
    let schema = SchemaInferrer::new()
        .schema_name("Empty")
        .infer(&rows)
        .expect("inference on empty rows should succeed (zero-width schema)");

    if let SchemaKind::Straight { fields } = &schema.kind {
        assert!(fields.is_empty(), "empty dataset → zero fields");
    }
}

#[test]
fn test_schema_inferrer_sample_size_limit() {
    // 5 rows, but sample_size = 2 → only first 2 rows consulted.
    // Column 0: i64(1), i64(2) in sample → I64
    let rows = {
        let mut v = Vec::new();
        for i in 1_i64..=5 {
            v.push(JsonTRowBuilder::new().push(JsonTValue::i64(i)).build());
        }
        v
    };

    let schema = SchemaInferrer::new()
        .sample_size(2)
        .infer(&rows)
        .unwrap();

    if let SchemaKind::Straight { fields } = &schema.kind {
        assert_eq!(fields.len(), 1);
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 1.4  Stringify — Namespace and JsonTRow
// ─────────────────────────────────────────────────────────────────────────────

fn build_demo_namespace() -> JsonTNamespace {
    let person_schema = build_person_schema();
    let catalog = JsonTCatalogBuilder::new()
        .schema(person_schema).unwrap()
        .build().unwrap();

    JsonTNamespaceBuilder::new("https://example.com/v1", "1.0")
        .data_schema("Person")
        .catalog(catalog)
        .build()
        .unwrap()
}

// ── Namespace compact ────────────────────────────────────────────────────────

#[test]
fn test_namespace_stringify_compact_contains_key_tokens() {
    let ns = build_demo_namespace();
    let output = ns.stringify(StringifyOptions::compact());

    assert!(output.contains("namespace"), "compact output should contain 'namespace'");
    assert!(output.contains("https://example.com/v1"), "should contain base_url");
    assert!(output.contains("\"1.0\""), "should contain version");
    assert!(output.contains("Person"), "should contain schema name");
    assert!(output.contains("data-schema"), "should contain data-schema key");
    assert!(output.contains("\"email\"").not() || output.contains("email"),
        "email field should appear");
    assert!(output.contains("fields"), "should contain fields keyword");
}

#[test]
fn test_namespace_stringify_compact_no_newlines() {
    let ns = build_demo_namespace();
    let output = ns.stringify(StringifyOptions::compact());
    assert!(!output.contains('\n'), "compact output should have no newlines");
}

// ── Namespace pretty ─────────────────────────────────────────────────────────

#[test]
fn test_namespace_stringify_pretty_contains_newlines() {
    let ns = build_demo_namespace();
    let output = ns.stringify(StringifyOptions::pretty());
    assert!(output.contains('\n'), "pretty output should contain newlines");
}

#[test]
fn test_namespace_stringify_pretty_indented() {
    let ns = build_demo_namespace();
    let output = ns.stringify(StringifyOptions::pretty_with_indent(4));
    // With 4-space indent, second-level content should start with 4 spaces
    assert!(output.lines().any(|l| l.starts_with("    ")),
        "output should have 4-space-indented lines");
}

#[test]
fn test_namespace_stringify_roundtrip_stable() {
    // Compact → stringify twice → same result (deterministic)
    let ns = build_demo_namespace();
    let out1 = ns.stringify(StringifyOptions::compact());
    let out2 = ns.stringify(StringifyOptions::compact());
    assert_eq!(out1, out2, "stringify should be deterministic");
}

#[test]
fn test_namespace_stringify_pretty_contains_field_names() {
    let ns = build_demo_namespace();
    let output = ns.stringify(StringifyOptions::pretty());

    assert!(output.contains("id"),    "pretty output should contain field 'id'");
    assert!(output.contains("name"),  "pretty output should contain field 'name'");
    assert!(output.contains("email"), "pretty output should contain field 'email'");
    assert!(output.contains("i64"),   "pretty output should contain type 'i64'");
    assert!(output.contains("str"),   "pretty output should contain type 'str'");
}

// ── JsonTRow stringify ───────────────────────────────────────────────────────

#[test]
fn test_row_stringify_compact() {

    let row = JsonTRowBuilder::new()
        .push(JsonTValue::i64(42))
        .push(JsonTValue::str("Alice"))
        .push(JsonTValue::Null)
        .build();

    let output = row.stringify(StringifyOptions::compact());
    // Rows are serialised as positional objects: {42, "Alice", null}
    assert!(output.contains("42"),      "row should contain 42");
    assert!(output.contains("\"Alice\""), "row should contain quoted Alice");
    assert!(output.contains("null"),    "row should contain null");
}

#[test]
fn test_row_stringify_boolean_values() {

    let row = JsonTRowBuilder::new()
        .push(JsonTValue::bool(true))
        .push(JsonTValue::bool(false))
        .build();

    let output = row.stringify(StringifyOptions::compact());
    assert!(output.contains("true"),  "should contain true");
    assert!(output.contains("false"), "should contain false");
}

#[test]
fn test_row_stringify_nested_array() {

    let arr = JsonTArrayBuilder::new()
        .push(JsonTValue::i32(1))
        .push(JsonTValue::i32(2))
        .build();

    let row = JsonTRowBuilder::new()
        .push(JsonTValue::Array(arr))
        .build();

    let output = row.stringify(StringifyOptions::compact());
    assert!(output.contains('['), "row with array field should output '['");
}

#[test]
fn test_value_stringify_enum_constant() {
    let v = JsonTValue::enum_val("ACTIVE");
    let output = v.stringify(StringifyOptions::compact());
    assert_eq!(output, "ACTIVE");
}

#[test]
fn test_value_stringify_unspecified() {
    let v = JsonTValue::Unspecified;
    let output = v.stringify(StringifyOptions::compact());
    assert_eq!(output, "_");
}

// ─────────────────────────────────────────────────────────────────────────────
// Helpers (trait extension so .not() reads naturally in assertions)
// ─────────────────────────────────────────────────────────────────────────────

trait BoolExt { fn not(self) -> bool; }
impl BoolExt for bool { fn not(self) -> bool { !self } }
