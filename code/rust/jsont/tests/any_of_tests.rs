// =============================================================================
// tests/any_of_tests.rs — anyOf union field tests
// =============================================================================
// Coverage:
//   1. AnyOfVariant helpers (is_scalar, is_schema_ref, constructors)
//   2. JsonTFieldKind helpers (is_any_of, is_array, any_of_variants)
//   3. ScalarType helpers (is_numeric, is_string_like)
//   4. Builder: factory, as_array, discriminator, build validation
//   5. Stringify round-trips (compact + pretty)
//   6. DSL parse: scalar variants, schema-ref variants, array, discriminator
//   7. JSON reader dispatch: bool / number / string / error cases
//   8. promote_any_of: first-match-wins numeric, string fallthrough, bool
// =============================================================================

use jsont::{
    AnyOfVariant, JsonTFieldBuilder, JsonTFieldKind, JsonTSchemaBuilder,
    JsonTValue, Parseable, ScalarType, StringifyOptions, JsonTNamespace,
};
use jsont::json::{JsonInputMode, JsonReader, MissingFieldPolicy, UnknownFieldPolicy};

// ─────────────────────────────────────────────────────────────────────────────
// 1. AnyOfVariant helpers
// ─────────────────────────────────────────────────────────────────────────────

#[test]
fn variant_scalar_constructor_and_predicate() {
    let v = AnyOfVariant::scalar(ScalarType::I32);
    assert!(v.is_scalar());
    assert!(!v.is_schema_ref());
    assert_eq!(v, AnyOfVariant::Scalar(ScalarType::I32));
}

#[test]
fn variant_schema_ref_constructor_and_predicate() {
    let v = AnyOfVariant::schema_ref("Address");
    assert!(v.is_schema_ref());
    assert!(!v.is_scalar());
    assert_eq!(v, AnyOfVariant::SchemaRef("Address".into()));
}

// ─────────────────────────────────────────────────────────────────────────────
// 2. JsonTFieldKind helpers
// ─────────────────────────────────────────────────────────────────────────────

fn build_any_of_field(name: &str, variants: Vec<AnyOfVariant>) -> jsont::JsonTField {
    JsonTFieldBuilder::any_of(name, variants).build().unwrap()
}

#[test]
fn field_kind_is_any_of() {
    let f = build_any_of_field("val", vec![
        AnyOfVariant::scalar(ScalarType::I32),
        AnyOfVariant::scalar(ScalarType::Str),
    ]);
    assert!(f.kind.is_any_of());
}

#[test]
fn field_kind_is_not_any_of_for_scalar() {
    let f = JsonTFieldBuilder::scalar("x", ScalarType::I32).build().unwrap();
    assert!(!f.kind.is_any_of());
}

#[test]
fn field_kind_is_array_false_by_default() {
    let f = build_any_of_field("val", vec![
        AnyOfVariant::scalar(ScalarType::I32),
        AnyOfVariant::scalar(ScalarType::Str),
    ]);
    assert!(!f.kind.is_array());
}

#[test]
fn field_kind_is_array_true_after_as_array() {
    let f = JsonTFieldBuilder::any_of("vals", vec![
        AnyOfVariant::scalar(ScalarType::I32),
        AnyOfVariant::scalar(ScalarType::Str),
    ])
    .as_array()
    .build()
    .unwrap();
    assert!(f.kind.is_array());
}

#[test]
fn field_kind_any_of_variants_returns_slice() {
    let variants = vec![
        AnyOfVariant::scalar(ScalarType::I32),
        AnyOfVariant::schema_ref("Category"),
    ];
    let f = build_any_of_field("val", variants.clone());
    assert_eq!(f.kind.any_of_variants(), variants.as_slice());
}

#[test]
fn field_kind_any_of_variants_empty_for_scalar_field() {
    let f = JsonTFieldBuilder::scalar("x", ScalarType::I32).build().unwrap();
    assert!(f.kind.any_of_variants().is_empty());
}

// ─────────────────────────────────────────────────────────────────────────────
// 3. ScalarType helpers
// ─────────────────────────────────────────────────────────────────────────────

#[test]
fn scalar_type_is_numeric() {
    for t in [ScalarType::I16, ScalarType::I32, ScalarType::I64,
              ScalarType::U16, ScalarType::U32, ScalarType::U64,
              ScalarType::D32, ScalarType::D64, ScalarType::D128] {
        assert!(t.is_numeric(), "{:?} should be numeric", t);
    }
    for t in [ScalarType::Bool, ScalarType::Str, ScalarType::Uuid, ScalarType::Date] {
        assert!(!t.is_numeric(), "{:?} should not be numeric", t);
    }
}

#[test]
fn scalar_type_is_string_like() {
    for t in [ScalarType::Str, ScalarType::NStr, ScalarType::Uri, ScalarType::Uuid,
              ScalarType::Email, ScalarType::Date, ScalarType::DateTime] {
        assert!(t.is_string_like(), "{:?} should be string-like", t);
    }
    for t in [ScalarType::I32, ScalarType::Bool, ScalarType::D64] {
        assert!(!t.is_string_like(), "{:?} should not be string-like", t);
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 4. Builder validation
// ─────────────────────────────────────────────────────────────────────────────

#[test]
fn builder_rejects_single_variant() {
    let result = JsonTFieldBuilder::any_of("val", vec![
        AnyOfVariant::scalar(ScalarType::I32),
    ])
    .build();
    assert!(result.is_err(), "single variant should fail");
}

#[test]
fn builder_rejects_zero_variants() {
    let result = JsonTFieldBuilder::any_of("val", vec![]).build();
    assert!(result.is_err(), "zero variants should fail");
}

#[test]
fn builder_accepts_two_scalar_variants() {
    let result = JsonTFieldBuilder::any_of("val", vec![
        AnyOfVariant::scalar(ScalarType::I32),
        AnyOfVariant::scalar(ScalarType::Str),
    ])
    .build();
    assert!(result.is_ok());
}

#[test]
fn builder_discriminator_stored() {
    let f = JsonTFieldBuilder::any_of("shape", vec![
        AnyOfVariant::schema_ref("Circle"),
        AnyOfVariant::schema_ref("Square"),
    ])
    .discriminator("type")
    .build()
    .unwrap();
    if let JsonTFieldKind::AnyOf { discriminator, .. } = &f.kind {
        assert_eq!(discriminator.as_deref(), Some("type"));
    } else {
        panic!("expected AnyOf");
    }
}

#[test]
fn builder_optional_stored() {
    let f = JsonTFieldBuilder::any_of("val", vec![
        AnyOfVariant::scalar(ScalarType::I32),
        AnyOfVariant::scalar(ScalarType::Str),
    ])
    .optional()
    .build()
    .unwrap();
    if let JsonTFieldKind::AnyOf { optional, .. } = &f.kind {
        assert!(*optional);
    } else {
        panic!("expected AnyOf");
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 5. Stringify
// ─────────────────────────────────────────────────────────────────────────────

fn ns_with_schema(schema: jsont::JsonTSchema) -> String {
    use jsont::{JsonTCatalogBuilder, JsonTNamespaceBuilder, Stringification};
    let name = schema.name.clone();
    let catalog = JsonTCatalogBuilder::new().schema(schema).unwrap().build().unwrap();
    let ns = JsonTNamespaceBuilder::new("https://x.com", "1.0")
        .data_schema(&name)
        .catalog(catalog)
        .build()
        .unwrap();
    ns.stringify(StringifyOptions::compact())
}

fn any_of_ns_src() -> String {
    let schema = JsonTSchemaBuilder::straight("Event")
        .field_from(JsonTFieldBuilder::any_of("payload", vec![
            AnyOfVariant::scalar(ScalarType::I32),
            AnyOfVariant::scalar(ScalarType::Str),
        ]))
        .unwrap()
        .field_from(JsonTFieldBuilder::any_of("tags", vec![
            AnyOfVariant::scalar(ScalarType::Str),
            AnyOfVariant::scalar(ScalarType::Uuid),
        ])
        .as_array())
        .unwrap()
        .build()
        .unwrap();
    ns_with_schema(schema)
}

#[test]
fn stringify_any_of_scalar_variants_compact() {
    let out = any_of_ns_src();
    // payload field: anyOf(i32 | str):payload
    assert!(out.contains("anyOf(i32 | str):payload"), "got: {out}");
    // tags field: anyOf(str | uuid)[]:tags
    assert!(out.contains("anyOf(str | uuid)[]:tags"), "got: {out}");
}

#[test]
fn stringify_any_of_with_schema_ref_compact() {
    let schema = JsonTSchemaBuilder::straight("Order")
        .field_from(JsonTFieldBuilder::any_of("item", vec![
            AnyOfVariant::schema_ref("Product"),
            AnyOfVariant::schema_ref("Bundle"),
        ])
        .discriminator("kind"))
        .unwrap()
        .build()
        .unwrap();
    let out = ns_with_schema(schema);
    assert!(out.contains("anyOf(<Product> | <Bundle>)"), "got: {out}");
    assert!(out.contains("on \"kind\""), "got: {out}");
}

#[test]
fn stringify_any_of_optional_field() {
    let schema = JsonTSchemaBuilder::straight("Msg")
        .field_from(JsonTFieldBuilder::any_of("extra", vec![
            AnyOfVariant::scalar(ScalarType::Bool),
            AnyOfVariant::scalar(ScalarType::Str),
        ])
        .optional())
        .unwrap()
        .build()
        .unwrap();
    let out = ns_with_schema(schema);
    assert!(out.contains("extra?"), "got: {out}");
}

// ─────────────────────────────────────────────────────────────────────────────
// 6. DSL parse
// ─────────────────────────────────────────────────────────────────────────────

fn wrap_schema(schema_body: &str) -> String {
    format!(
        r#"{{
            namespace: {{
                baseUrl: "https://example.com",
                version: "1.0",
                catalogs: [{{ schemas: [{}] }}],
                data-schema: Test
            }}
        }}"#,
        schema_body
    )
}

#[test]
fn parse_any_of_two_scalar_variants() {
    let src = wrap_schema(r#"
        Test: {
            fields: {
                anyOf(i32 | str): payload
            }
        }
    "#);
    let ns = JsonTNamespace::parse(&src).expect("should parse");
    let schema = &ns.catalogs[0].schemas[0];
    if let jsont::SchemaKind::Straight { fields } = &schema.kind {
        assert_eq!(fields.len(), 1);
        assert_eq!(fields[0].name, "payload");
        if let JsonTFieldKind::AnyOf { variants, is_array, optional, discriminator, .. } = &fields[0].kind {
            assert_eq!(variants.len(), 2);
            assert_eq!(variants[0], AnyOfVariant::Scalar(ScalarType::I32));
            assert_eq!(variants[1], AnyOfVariant::Scalar(ScalarType::Str));
            assert!(!is_array);
            assert!(!optional);
            assert!(discriminator.is_none());
        } else { panic!("expected AnyOf field"); }
    } else { panic!("expected straight schema"); }
}

#[test]
fn parse_any_of_array_variant() {
    let src = wrap_schema(r#"
        Test: {
            fields: {
                anyOf(str | uuid)[]: tags
            }
        }
    "#);
    let ns = JsonTNamespace::parse(&src).expect("should parse");
    let field = &ns.catalogs[0].schemas[0];
    if let jsont::SchemaKind::Straight { fields } = &field.kind {
        if let JsonTFieldKind::AnyOf { is_array, .. } = &fields[0].kind {
            assert!(*is_array, "expected array");
        } else { panic!("expected AnyOf"); }
    } else { panic!("expected straight schema"); }
}

#[test]
fn parse_any_of_optional_field() {
    let src = wrap_schema(r#"
        Test: {
            fields: {
                anyOf(i32 | d64): amount?
            }
        }
    "#);
    let ns = JsonTNamespace::parse(&src).expect("should parse");
    if let jsont::SchemaKind::Straight { fields } = &ns.catalogs[0].schemas[0].kind {
        if let JsonTFieldKind::AnyOf { optional, .. } = &fields[0].kind {
            assert!(*optional);
        } else { panic!("expected AnyOf"); }
    } else { panic!("expected straight schema"); }
}

#[test]
fn parse_any_of_with_schema_refs_and_discriminator() {
    let src = wrap_schema(r#"
        Test: {
            fields: {
                anyOf(<Cat> | <Dog>) on "species": pet
            }
        }
    "#);
    let ns = JsonTNamespace::parse(&src).expect("should parse");
    if let jsont::SchemaKind::Straight { fields } = &ns.catalogs[0].schemas[0].kind {
        if let JsonTFieldKind::AnyOf { variants, discriminator, .. } = &fields[0].kind {
            assert_eq!(variants[0], AnyOfVariant::SchemaRef("Cat".into()));
            assert_eq!(variants[1], AnyOfVariant::SchemaRef("Dog".into()));
            assert_eq!(discriminator.as_deref(), Some("species"));
        } else { panic!("expected AnyOf"); }
    } else { panic!("expected straight schema"); }
}

// ─────────────────────────────────────────────────────────────────────────────
// 7. JSON reader dispatch
// ─────────────────────────────────────────────────────────────────────────────

fn event_schema_for_json() -> jsont::JsonTSchema {
    JsonTSchemaBuilder::straight("Event")
        .field_from(JsonTFieldBuilder::scalar("id", ScalarType::I64)).unwrap()
        .field_from(JsonTFieldBuilder::any_of("value", vec![
            AnyOfVariant::scalar(ScalarType::Bool),
            AnyOfVariant::scalar(ScalarType::I32),
            AnyOfVariant::scalar(ScalarType::Str),
        ])).unwrap()
        .build()
        .unwrap()
}

fn read_json_value(json: &str) -> Vec<jsont::JsonTRow> {
    let reader = JsonReader::with_schema(event_schema_for_json())
        .mode(JsonInputMode::Array)
        .missing_fields(MissingFieldPolicy::UseDefault)
        .unknown_fields(UnknownFieldPolicy::Skip)
        .build();
    let mut rows = Vec::new();
    reader.read(json, |row| rows.push(row)).expect("read should succeed");
    rows
}

#[test]
fn json_reader_any_of_dispatches_bool() {
    let rows = read_json_value(r#"[{"id": 1, "value": true}]"#);
    assert_eq!(rows.len(), 1);
    assert_eq!(rows[0].fields[1], JsonTValue::bool(true));
}

#[test]
fn json_reader_any_of_dispatches_integer() {
    let rows = read_json_value(r#"[{"id": 1, "value": 42}]"#);
    assert_eq!(rows.len(), 1);
    // Bool is first variant but 42 is not bool — should match I32
    assert_eq!(rows[0].fields[1], JsonTValue::i32(42));
}

#[test]
fn json_reader_any_of_dispatches_string() {
    let rows = read_json_value(r#"[{"id": 1, "value": "hello"}]"#);
    assert_eq!(rows.len(), 1);
    assert_eq!(rows[0].fields[1], JsonTValue::str("hello"));
}

#[test]
fn json_reader_any_of_null_when_optional() {
    let schema = JsonTSchemaBuilder::straight("Msg")
        .field_from(JsonTFieldBuilder::scalar("id", ScalarType::I64)).unwrap()
        .field_from(JsonTFieldBuilder::any_of("extra", vec![
            AnyOfVariant::scalar(ScalarType::I32),
            AnyOfVariant::scalar(ScalarType::Str),
        ]).optional())
        .unwrap()
        .build()
        .unwrap();

    let reader = JsonReader::with_schema(schema)
        .mode(JsonInputMode::Array)
        .missing_fields(MissingFieldPolicy::UseDefault)
        .unknown_fields(UnknownFieldPolicy::Skip)
        .build();
    let mut rows = Vec::new();
    // `extra` field absent → Null (UseDefault falls back to Null for anyOf)
    reader.read(r#"[{"id": 10}]"#, |row| rows.push(row)).expect("read should succeed");
    assert_eq!(rows.len(), 1);
    assert_eq!(rows[0].fields[1], JsonTValue::null());
}

#[test]
fn json_reader_any_of_error_on_no_match() {
    // Schema only accepts bool or i32 — supply a plain string
    let schema = JsonTSchemaBuilder::straight("Ev")
        .field_from(JsonTFieldBuilder::scalar("id", ScalarType::I64)).unwrap()
        .field_from(JsonTFieldBuilder::any_of("code", vec![
            AnyOfVariant::scalar(ScalarType::Bool),
            AnyOfVariant::scalar(ScalarType::I32),
        ])).unwrap()
        .build()
        .unwrap();

    let reader = JsonReader::with_schema(schema)
        .mode(JsonInputMode::Array)
        .missing_fields(MissingFieldPolicy::UseDefault)
        .unknown_fields(UnknownFieldPolicy::Skip)
        .build();
    let mut rows = Vec::new();
    let result = reader.read(r#"[{"id": 1, "code": "oops"}]"#, |row| rows.push(row));
    assert!(result.is_err(), "string should not match bool|i32 variants");
}

// ─────────────────────────────────────────────────────────────────────────────
// 8. Stringify → parse round-trip
// ─────────────────────────────────────────────────────────────────────────────

#[test]
fn stringify_parse_roundtrip_any_of_field() {
    // Build schema with anyOf field
    let original = JsonTSchemaBuilder::straight("Roundtrip")
        .field_from(JsonTFieldBuilder::any_of("mixed", vec![
            AnyOfVariant::scalar(ScalarType::I32),
            AnyOfVariant::scalar(ScalarType::Uuid),
            AnyOfVariant::scalar(ScalarType::Bool),
        ])).unwrap()
        .build()
        .unwrap();

    // Stringify to a full namespace so we can parse it back
    let ns_src = ns_with_schema(original);

    let ns = JsonTNamespace::parse(&ns_src).expect("round-trip parse should succeed");
    let reparsed = &ns.catalogs[0].schemas[0];
    if let jsont::SchemaKind::Straight { fields } = &reparsed.kind {
        assert_eq!(fields[0].name, "mixed");
        if let JsonTFieldKind::AnyOf { variants, .. } = &fields[0].kind {
            assert_eq!(variants.len(), 3);
            assert_eq!(variants[0], AnyOfVariant::Scalar(ScalarType::I32));
            assert_eq!(variants[1], AnyOfVariant::Scalar(ScalarType::Uuid));
            assert_eq!(variants[2], AnyOfVariant::Scalar(ScalarType::Bool));
        } else { panic!("expected AnyOf after round-trip"); }
    } else { panic!("expected straight schema after round-trip"); }
}
