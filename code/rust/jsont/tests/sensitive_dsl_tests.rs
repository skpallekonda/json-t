// =============================================================================
// tests/sensitive_dsl_tests.rs — Step 10.2: DSL Round-trip (Rust)
// =============================================================================
// Covers:
//   • Parse DSL with `~` sensitive marker → sensitive: true in model
//   • Parse DSL with `decrypt(...)` operation → SchemaOperation::Decrypt
//   • Stringify back → `~` and `decrypt(...)` appear in output
//   • Round-trip: parse → stringify → re-parse → model unchanged
//   • Combinations: sensitive + optional, sensitive + array
//   • Straight schema with sensitive fields (no decrypt op)
//   • Derived schema with decrypt op
// =============================================================================

use jsont::{
    JsonTNamespace, JsonTFieldKind, Parseable, SchemaKind, SchemaOperation,
    StringifyOptions, Stringification,
};

// =============================================================================
// Helpers
// =============================================================================

fn minimal_ns(schema_dsl: &str) -> String {
    format!(
        r#"{{
    namespace: {{
        baseUrl: "https://example.com",
        version: "1.0",
        catalogs: [
            {{
                schemas: [
                    {schema_dsl}
                ]
            }}
        ],
        data-schema: Person
    }}
}}"#
    )
}

fn parse_ns(schema_dsl: &str) -> JsonTNamespace {
    JsonTNamespace::parse(&minimal_ns(schema_dsl)).expect("namespace should parse")
}

fn first_schema(ns: &JsonTNamespace) -> &jsont::JsonTSchema {
    &ns.catalogs[0].schemas[0]
}

fn first_field(ns: &JsonTNamespace) -> &jsont::JsonTField {
    match &first_schema(ns).kind {
        SchemaKind::Straight { fields } => &fields[0],
        _ => panic!("expected straight schema"),
    }
}

fn compact(schema: &jsont::JsonTSchema) -> String {
    schema.stringify(StringifyOptions::compact())
}

// =============================================================================
// Parse: sensitive_mark → sensitive: true
// =============================================================================

#[test]
fn parse_sensitive_scalar_field() {
    let ns = parse_ns(r#"Person: {
        fields: {
            str~: ssn,
            str: name
        }
    }"#);

    let schema = first_schema(&ns);
    if let SchemaKind::Straight { fields } = &schema.kind {
        assert_eq!(fields[0].name, "ssn");
        assert!(fields[0].kind.is_sensitive(), "ssn should be sensitive");
        assert_eq!(fields[1].name, "name");
        assert!(!fields[1].kind.is_sensitive(), "name should not be sensitive");
    } else {
        panic!("expected straight schema");
    }
}

#[test]
fn parse_non_sensitive_field_has_false() {
    let ns = parse_ns(r#"Person: {
        fields: {
            str: name,
            i32: age
        }
    }"#);

    let f = first_field(&ns);
    assert!(!f.kind.is_sensitive());
}

#[test]
fn parse_sensitive_preserves_scalar_type() {
    let ns = parse_ns(r#"Person: {
        fields: {
            date~: dob
        }
    }"#);

    let f = first_field(&ns);
    if let JsonTFieldKind::Scalar { field_type, sensitive, .. } = &f.kind {
        assert!(sensitive);
        assert_eq!(field_type.scalar, jsont::ScalarType::Date);
    } else {
        panic!("expected scalar field");
    }
}

#[test]
fn parse_sensitive_array_field() {
    let ns = parse_ns(r#"Person: {
        fields: {
            str[]~: tags
        }
    }"#);

    let f = first_field(&ns);
    assert!(f.kind.is_sensitive());
    assert!(f.kind.is_array());
}

#[test]
fn parse_multiple_sensitive_fields() {
    let ns = parse_ns(r#"Employee: {
        fields: {
            str~: ssn,
            d64~: salary,
            str: name
        }
    }"#);

    let schema = first_schema(&ns);
    if let SchemaKind::Straight { fields } = &schema.kind {
        assert_eq!(fields.len(), 3);
        assert!(fields[0].kind.is_sensitive(), "ssn sensitive");
        assert!(fields[1].kind.is_sensitive(), "salary sensitive");
        assert!(!fields[2].kind.is_sensitive(), "name not sensitive");
    } else {
        panic!("expected straight schema");
    }
}

// =============================================================================
// Parse: decrypt operation
// =============================================================================

#[test]
fn parse_decrypt_single_field() {
    let ns = parse_ns(r#"EmployeeView: FROM Employee {
        operations: (
            decrypt(ssn)
        )
    }"#);

    let schema = first_schema(&ns);
    let ops = match &schema.kind {
        SchemaKind::Derived { operations, .. } => operations,
        _ => panic!("expected derived schema"),
    };
    assert_eq!(ops.len(), 1);
    assert!(matches!(&ops[0], SchemaOperation::Decrypt { fields } if fields == &["ssn"]));
}

#[test]
fn parse_decrypt_multiple_fields() {
    let ns = parse_ns(r#"EmployeeView: FROM Employee {
        operations: (
            decrypt(ssn, salary)
        )
    }"#);

    let schema = first_schema(&ns);
    let ops = match &schema.kind {
        SchemaKind::Derived { operations, .. } => operations,
        _ => panic!("expected derived schema"),
    };
    assert!(matches!(&ops[0], SchemaOperation::Decrypt { fields } if fields == &["ssn", "salary"]));
}

#[test]
fn parse_decrypt_mixed_with_other_ops() {
    let ns = parse_ns(r#"View: FROM Base {
        operations: (
            decrypt(sensitiveField),
            exclude(internalId)
        )
    }"#);

    let schema = first_schema(&ns);
    let ops = match &schema.kind {
        SchemaKind::Derived { operations, .. } => operations,
        _ => panic!("expected derived schema"),
    };
    assert_eq!(ops.len(), 2);
    assert!(matches!(&ops[0], SchemaOperation::Decrypt { fields } if fields == &["sensitiveField"]));
    assert!(matches!(&ops[1], SchemaOperation::Exclude(_)));
}

#[test]
fn parse_multiple_decrypt_ops() {
    let ns = parse_ns(r#"View: FROM Base {
        operations: (
            decrypt(fieldA),
            decrypt(fieldB)
        )
    }"#);

    let schema = first_schema(&ns);
    let ops = match &schema.kind {
        SchemaKind::Derived { operations, .. } => operations,
        _ => panic!("expected derived schema"),
    };
    assert_eq!(ops.len(), 2);
    assert!(matches!(&ops[0], SchemaOperation::Decrypt { fields } if fields == &["fieldA"]));
    assert!(matches!(&ops[1], SchemaOperation::Decrypt { fields } if fields == &["fieldB"]));
}

// =============================================================================
// Stringify: sensitive fields emit `~`
// =============================================================================

#[test]
fn stringify_sensitive_scalar_emits_tilde() {
    let ns = parse_ns(r#"Person: {
        fields: {
            str~: ssn
        }
    }"#);

    let schema = first_schema(&ns);
    let out = compact(schema);
    assert!(out.contains("str~:"), "expected 'str~:' in: {out}");
}

#[test]
fn stringify_non_sensitive_no_tilde() {
    let ns = parse_ns(r#"Person: {
        fields: {
            str: name
        }
    }"#);

    let schema = first_schema(&ns);
    let out = compact(schema);
    assert!(!out.contains('~'), "unexpected '~' in: {out}");
}

#[test]
fn stringify_sensitive_array_emits_tilde() {
    let ns = parse_ns(r#"Person: {
        fields: {
            str[]~: tags
        }
    }"#);

    let schema = first_schema(&ns);
    let out = compact(schema);
    assert!(out.contains("str[]~:") || out.contains("str~[]"), "expected array+tilde in: {out}");
}

#[test]
fn stringify_decrypt_op_emits_decrypt() {
    let ns = parse_ns(r#"View: FROM Base {
        operations: (
            decrypt(ssn, salary)
        )
    }"#);

    let schema = first_schema(&ns);
    let out = compact(schema);
    assert!(
        out.contains("decrypt(ssn, salary)") || out.contains("decrypt(ssn,salary)"),
        "expected decrypt op in: {out}"
    );
}

// =============================================================================
// Round-trip: parse → stringify → re-parse → model identical
// =============================================================================

#[test]
fn roundtrip_sensitive_field_preserved() {
    let original_dsl = minimal_ns(r#"Employee: {
        fields: {
            str~: ssn,
            d64~: salary,
            str: name
        }
    }"#);

    let ns1 = JsonTNamespace::parse(&original_dsl).expect("first parse");
    let schema1 = &ns1.catalogs[0].schemas[0];
    let stringified = schema1.stringify(StringifyOptions::compact());

    // Wrap in a fresh namespace for re-parse
    let wrapped = minimal_ns(&stringified);
    let ns2 = JsonTNamespace::parse(&wrapped).expect("second parse");
    let schema2 = &ns2.catalogs[0].schemas[0];

    // Compare field sensitivity
    if let (
        SchemaKind::Straight { fields: f1 },
        SchemaKind::Straight { fields: f2 },
    ) = (&schema1.kind, &schema2.kind)
    {
        assert_eq!(f1.len(), f2.len());
        for (a, b) in f1.iter().zip(f2.iter()) {
            assert_eq!(a.name, b.name, "field name mismatch");
            assert_eq!(a.kind.is_sensitive(), b.kind.is_sensitive(),
                "sensitive mismatch for field '{}'", a.name);
        }
    } else {
        panic!("expected straight schemas");
    }
}

#[test]
fn roundtrip_decrypt_op_preserved() {
    let original_dsl = minimal_ns(r#"EmployeeView: FROM Employee {
        operations: (
            decrypt(ssn, salary)
        )
    }"#);

    let ns1 = JsonTNamespace::parse(&original_dsl).expect("first parse");
    let schema1 = &ns1.catalogs[0].schemas[0];
    let stringified = schema1.stringify(StringifyOptions::compact());

    let wrapped = minimal_ns(&stringified);
    let ns2 = JsonTNamespace::parse(&wrapped).expect("second parse");
    let schema2 = &ns2.catalogs[0].schemas[0];

    let ops1 = match &schema1.kind { SchemaKind::Derived { operations, .. } => operations, _ => panic!() };
    let ops2 = match &schema2.kind { SchemaKind::Derived { operations, .. } => operations, _ => panic!() };

    assert_eq!(ops1.len(), ops2.len());
    if let (SchemaOperation::Decrypt { fields: f1 }, SchemaOperation::Decrypt { fields: f2 }) = (&ops1[0], &ops2[0]) {
        assert_eq!(f1, f2);
    } else {
        panic!("expected Decrypt ops");
    }
}

// =============================================================================
// Edge cases
// =============================================================================

#[test]
fn parse_sensitive_field_with_constraints() {
    // Sensitive field with a constraint should still parse correctly
    let ns = parse_ns(r#"Person: {
        fields: {
            str~: ssn [(minLength=9, maxLength=11)]
        }
    }"#);

    let f = first_field(&ns);
    assert!(f.kind.is_sensitive());
    assert_eq!(f.name, "ssn");
}

#[test]
fn schema_with_only_non_sensitive_fields_stringifies_without_tilde() {
    let ns = parse_ns(r#"Address: {
        fields: {
            str: street,
            str: city,
            str: zip
        }
    }"#);

    let schema = first_schema(&ns);
    let out = compact(schema);
    assert!(!out.contains('~'), "unexpected ~ in non-sensitive schema: {out}");
}
