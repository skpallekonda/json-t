// =============================================================================
// tests/sensitive_tests.rs — Step 10.1: Model + Builder
// =============================================================================
// Covers:
//   • JsonTValue::Encrypted construction and predicates
//   • sensitive flag on JsonTFieldKind::Scalar
//   • Builder: .sensitive() sets flag; rejects non-scalar kinds
//   • SchemaOperation::Decrypt construction
//   • JsonTSchemaBuilder::decrypt() appends Decrypt op to derived schema
//   • JsonTSchemaBuilder::decrypt() errors on straight schema
// =============================================================================

use jsont::{
    AnyOfVariant, JsonTFieldBuilder, JsonTFieldKind, JsonTSchemaBuilder, JsonTValue,
    ScalarType, SchemaOperation,
};

// =============================================================================
// JsonTValue::Encrypted
// =============================================================================

#[test]
fn encrypted_constructor_stores_bytes() {
    let bytes = vec![0xDE, 0xAD, 0xBE, 0xEF];
    let v = JsonTValue::encrypted(bytes.clone());
    assert!(matches!(v, JsonTValue::Encrypted(ref b) if b == &bytes));
}

#[test]
fn encrypted_is_encrypted_predicate() {
    let v = JsonTValue::encrypted(vec![1, 2, 3]);
    assert!(v.is_encrypted());
}

#[test]
fn encrypted_other_predicates_are_false() {
    let v = JsonTValue::encrypted(vec![1, 2, 3]);
    assert!(!v.is_null());
    assert!(!v.is_numeric());
    assert!(!v.is_string());
}

#[test]
fn encrypted_type_name() {
    let v = JsonTValue::encrypted(vec![0xFF]);
    assert_eq!(v.type_name(), "encrypted");
}

#[test]
fn encrypted_as_encrypted_returns_bytes() {
    let bytes = vec![10u8, 20, 30];
    let v = JsonTValue::encrypted(bytes.clone());
    assert_eq!(v.as_encrypted(), Some(bytes.as_slice()));
}

#[test]
fn non_encrypted_as_encrypted_returns_none() {
    assert_eq!(JsonTValue::null().as_encrypted(), None);
    assert_eq!(JsonTValue::str("hello").as_encrypted(), None);
    assert_eq!(JsonTValue::i32(42).as_encrypted(), None);
}

#[test]
fn encrypted_empty_envelope_is_valid() {
    let v = JsonTValue::encrypted(vec![]);
    assert!(v.is_encrypted());
    assert_eq!(v.as_encrypted(), Some([].as_slice()));
}

// =============================================================================
// sensitive flag — JsonTFieldKind::Scalar
// =============================================================================

#[test]
fn scalar_field_sensitive_defaults_to_false() {
    let field = JsonTFieldBuilder::scalar("name", ScalarType::Str)
        .build()
        .expect("build should succeed");
    assert!(!field.kind.is_sensitive());
}

#[test]
fn sensitive_builder_sets_flag_on_scalar() {
    let field = JsonTFieldBuilder::scalar("ssn", ScalarType::Str)
        .sensitive()
        .build()
        .expect("build should succeed");
    assert!(field.kind.is_sensitive());
}

#[test]
fn sensitive_field_name_and_type_preserved() {
    let field = JsonTFieldBuilder::scalar("dob", ScalarType::Date)
        .sensitive()
        .build()
        .expect("build should succeed");
    assert_eq!(field.name, "dob");
    assert!(matches!(
        field.kind,
        JsonTFieldKind::Scalar { sensitive: true, .. }
    ));
}

#[test]
fn sensitive_combined_with_optional() {
    let field = JsonTFieldBuilder::scalar("middleName", ScalarType::Str)
        .sensitive()
        .optional()
        .build()
        .expect("build should succeed");
    assert!(matches!(
        field.kind,
        JsonTFieldKind::Scalar { sensitive: true, optional: true, .. }
    ));
}

#[test]
fn sensitive_combined_with_as_array() {
    let field = JsonTFieldBuilder::scalar("tags", ScalarType::Str)
        .sensitive()
        .as_array()
        .build()
        .expect("build should succeed");
    assert!(field.kind.is_sensitive());
    assert!(field.kind.is_array());
}

// =============================================================================
// Builder guards: sensitive() rejected on non-scalar kinds
// =============================================================================

#[test]
fn sensitive_on_object_field_is_build_error() {
    let result = JsonTFieldBuilder::object("address", "Address")
        .sensitive()
        .build();
    assert!(result.is_err(), "expected BuildError for sensitive on object field");
    let msg = format!("{:?}", result.unwrap_err());
    assert!(
        msg.contains("sensitive") || msg.contains("scalar"),
        "error message should mention sensitive/scalar: {msg}"
    );
}

#[test]
fn sensitive_on_any_of_field_is_build_error() {
    let result = JsonTFieldBuilder::any_of(
        "value",
        vec![AnyOfVariant::scalar(ScalarType::Str), AnyOfVariant::scalar(ScalarType::I32)],
    )
    .sensitive()
    .build();
    assert!(result.is_err(), "expected BuildError for sensitive on anyOf field");
    let msg = format!("{:?}", result.unwrap_err());
    assert!(
        msg.contains("sensitive") || msg.contains("scalar"),
        "error message should mention sensitive/scalar: {msg}"
    );
}

// =============================================================================
// SchemaOperation::Decrypt
// =============================================================================

#[test]
fn decrypt_operation_constructed_directly() {
    let op = SchemaOperation::Decrypt {
        fields: vec!["firstName".into(), "dob".into()],
    };
    assert!(matches!(op, SchemaOperation::Decrypt { ref fields } if fields.len() == 2));
}

#[test]
fn decrypt_operation_field_list_preserved() {
    let fields = vec!["ssn".to_string(), "creditCard".to_string()];
    let op = SchemaOperation::Decrypt { fields: fields.clone() };
    if let SchemaOperation::Decrypt { fields: f } = &op {
        assert_eq!(f, &fields);
    } else {
        panic!("expected Decrypt variant");
    }
}

// =============================================================================
// JsonTSchemaBuilder::decrypt() — derived schema
// =============================================================================

#[test]
fn schema_builder_decrypt_appends_decrypt_op() {
    let schema = JsonTSchemaBuilder::derived("EmployeeView", "Employee")
        .decrypt(vec!["ssn".into(), "salary".into()])
        .expect("decrypt should succeed on derived schema")
        .build()
        .expect("build should succeed");

    let ops = match &schema.kind {
        jsont::SchemaKind::Derived { operations, .. } => operations,
        _ => panic!("expected derived schema"),
    };
    assert_eq!(ops.len(), 1);
    assert!(
        matches!(&ops[0], SchemaOperation::Decrypt { fields } if fields == &["ssn", "salary"]),
        "expected Decrypt op with correct field list"
    );
}

#[test]
fn schema_builder_decrypt_on_straight_schema_is_error() {
    let result = JsonTSchemaBuilder::straight("Person")
        .decrypt(vec!["ssn".into()]);
    assert!(result.is_err(), "expected error calling decrypt() on a straight schema");
}

#[test]
fn schema_builder_multiple_decrypt_ops_accumulate() {
    let schema = JsonTSchemaBuilder::derived("View", "Base")
        .decrypt(vec!["fieldA".into()])
        .expect("first decrypt ok")
        .decrypt(vec!["fieldB".into()])
        .expect("second decrypt ok")
        .build()
        .expect("build should succeed");

    let ops = match &schema.kind {
        jsont::SchemaKind::Derived { operations, .. } => operations,
        _ => panic!("expected derived schema"),
    };
    assert_eq!(ops.len(), 2);
    assert!(matches!(&ops[0], SchemaOperation::Decrypt { fields } if fields == &["fieldA"]));
    assert!(matches!(&ops[1], SchemaOperation::Decrypt { fields } if fields == &["fieldB"]));
}

#[test]
fn schema_builder_decrypt_mixed_with_other_ops() {
    use jsont::{FieldPath, SchemaOperation};

    let schema = JsonTSchemaBuilder::derived("SummaryView", "Person")
        .decrypt(vec!["ssn".into()])
        .expect("decrypt ok")
        .operation(SchemaOperation::Exclude(vec![FieldPath::single("internalId")]))
        .expect("exclude ok")
        .build()
        .expect("build should succeed");

    let ops = match &schema.kind {
        jsont::SchemaKind::Derived { operations, .. } => operations,
        _ => panic!("expected derived schema"),
    };
    assert_eq!(ops.len(), 2);
    assert!(matches!(&ops[0], SchemaOperation::Decrypt { .. }));
    assert!(matches!(&ops[1], SchemaOperation::Exclude(_)));
}
