// =============================================================================
// tests/data_row_tests.rs
// =============================================================================
// Tests for data row parsing via Vec<JsonTRow>::parse().
//
// Grammar:
//   data_rows    = { data_row ~ ("," ~ data_row)* ~ ","? }
//   data_row     = { object_value }
//   object_value = { "{" ~ value ~ ("," ~ value)* ~ "}" }
//   array_value  = { "[" ~ value ~ ("," ~ value)* ~ "]" }
//   value        = { literal | unspecified | enum_value | object_value | array_value }
//
// Each row is positional: { v1, v2, v3 } — field names come from the schema.
// =============================================================================

use jsont::{JsonTRow, JsonTValue, Parseable};

// =============================================================================
// Single-row parsing
// =============================================================================

#[test]
fn test_parse_single_row_all_scalars() {
    let rows = Vec::<JsonTRow>::parse(r#"{ "alice", 30, true }"#).unwrap();
    assert_eq!(rows.len(), 1);
    let row = &rows[0];
    assert_eq!(row.len(), 3);
    assert_eq!(row.get(0), Some(&JsonTValue::str("alice")));
    assert_eq!(row.get(1), Some(&JsonTValue::d64(30.0)));
    assert_eq!(row.get(2), Some(&JsonTValue::bool(true)));
}

#[test]
fn test_parse_single_row_with_null() {
    let rows = Vec::<JsonTRow>::parse(r#"{ "bob", null, false }"#).unwrap();
    let row = &rows[0];
    assert_eq!(row.get(0), Some(&JsonTValue::str("bob")));
    assert_eq!(row.get(1), Some(&JsonTValue::Null));
    assert_eq!(row.get(2), Some(&JsonTValue::bool(false)));
}

#[test]
fn test_parse_single_row_with_nil_alias() {
    // "nil" is a grammar alias for null [D-5]
    let rows = Vec::<JsonTRow>::parse(r#"{ "carol", nil }"#).unwrap();
    assert_eq!(rows[0].get(1), Some(&JsonTValue::Null));
}

#[test]
fn test_parse_single_row_with_unspecified() {
    // "_" is the unspecified sentinel
    let rows = Vec::<JsonTRow>::parse(r#"{ "dave", _ }"#).unwrap();
    assert_eq!(rows[0].get(1), Some(&JsonTValue::Unspecified));
}

#[test]
fn test_parse_single_row_with_enum_value() {
    // CONSTID (all-caps, 2+ chars) is an enum constant
    let rows = Vec::<JsonTRow>::parse(r#"{ "eve", ADMIN }"#).unwrap();
    assert_eq!(rows[0].get(1), Some(&JsonTValue::Enum("ADMIN".into())));
}

#[test]
fn test_parse_single_row_with_float() {
    let rows = Vec::<JsonTRow>::parse(r#"{ 3.14, 2.71 }"#).unwrap();
    let row = &rows[0];
    let a = row.get(0).unwrap().as_f64().unwrap();
    let b = row.get(1).unwrap().as_f64().unwrap();
    assert!((a - 3.14).abs() < 1e-10);
    assert!((b - 2.71).abs() < 1e-10);
}

#[test]
fn test_parse_single_row_single_field() {
    let rows = Vec::<JsonTRow>::parse(r#"{ "only" }"#).unwrap();
    assert_eq!(rows[0].len(), 1);
    assert_eq!(rows[0].get(0), Some(&JsonTValue::str("only")));
}

// =============================================================================
// Multi-row parsing
// =============================================================================

#[test]
fn test_parse_multiple_rows() {
    let input = r#"
        { "alice", 30 },
        { "bob",   25 },
        { "carol", 40 }
    "#;
    let rows = Vec::<JsonTRow>::parse(input).unwrap();
    assert_eq!(rows.len(), 3);

    assert_eq!(rows[0].get(0), Some(&JsonTValue::str("alice")));
    assert_eq!(rows[0].get(1), Some(&JsonTValue::d64(30.0)));

    assert_eq!(rows[1].get(0), Some(&JsonTValue::str("bob")));
    assert_eq!(rows[1].get(1), Some(&JsonTValue::d64(25.0)));

    assert_eq!(rows[2].get(0), Some(&JsonTValue::str("carol")));
    assert_eq!(rows[2].get(1), Some(&JsonTValue::d64(40.0)));
}

#[test]
fn test_parse_trailing_comma_is_accepted() {
    // Grammar allows an optional trailing comma after the last row [D-4]
    let input = r#"
        { "alice", 1 },
        { "bob",   2 },
    "#;
    let rows = Vec::<JsonTRow>::parse(input).unwrap();
    assert_eq!(rows.len(), 2);
}

#[test]
fn test_parse_mixed_null_and_enum_across_rows() {
    let input = r#"
        { "alice", ACTIVE,   30 },
        { "bob",   INACTIVE, null }
    "#;
    let rows = Vec::<JsonTRow>::parse(input).unwrap();

    assert_eq!(rows[0].get(1), Some(&JsonTValue::Enum("ACTIVE".into())));
    assert_eq!(rows[0].get(2), Some(&JsonTValue::d64(30.0)));

    assert_eq!(rows[1].get(1), Some(&JsonTValue::Enum("INACTIVE".into())));
    assert_eq!(rows[1].get(2), Some(&JsonTValue::Null));
}

// =============================================================================
// Nested object values (object field within a row)
// =============================================================================

#[test]
fn test_parse_row_with_nested_object() {
    // Inner object_value becomes JsonTValue::Object(JsonTRow)
    let input = r#"{ "alice", { "123 Main St", "Springfield" } }"#;
    let rows = Vec::<JsonTRow>::parse(input).unwrap();
    assert_eq!(rows.len(), 1);

    let row = &rows[0];
    assert_eq!(row.get(0), Some(&JsonTValue::str("alice")));

    // Second field is a nested object row
    match row.get(1) {
        Some(JsonTValue::Object(nested)) => {
            assert_eq!(nested.len(), 2);
            assert_eq!(nested.get(0), Some(&JsonTValue::str("123 Main St")));
            assert_eq!(nested.get(1), Some(&JsonTValue::str("Springfield")));
        }
        other => panic!("expected Object, got {other:?}"),
    }
}

#[test]
fn test_parse_row_with_null_nested_object() {
    // A null where a nested object would appear
    let input = r#"{ "bob", null }"#;
    let rows = Vec::<JsonTRow>::parse(input).unwrap();
    assert_eq!(rows[0].get(1), Some(&JsonTValue::Null));
}

#[test]
fn test_parse_multiple_rows_with_nested_objects() {
    let input = r#"
        { "alice", { "1 A St", "NY" } },
        { "bob",   { "2 B Ave", "LA" } }
    "#;
    let rows = Vec::<JsonTRow>::parse(input).unwrap();
    assert_eq!(rows.len(), 2);

    for (i, (name, street, city)) in [("alice", "1 A St", "NY"), ("bob", "2 B Ave", "LA")]
        .iter()
        .enumerate()
    {
        assert_eq!(rows[i].get(0), Some(&JsonTValue::str(*name)));
        if let Some(JsonTValue::Object(addr)) = rows[i].get(1) {
            assert_eq!(addr.get(0), Some(&JsonTValue::str(*street)));
            assert_eq!(addr.get(1), Some(&JsonTValue::str(*city)));
        } else {
            panic!("row {i}: expected nested Object");
        }
    }
}

// =============================================================================
// Array values within a row
// =============================================================================

#[test]
fn test_parse_row_with_array_field() {
    // array_value becomes JsonTValue::Array(JsonTArray)
    let input = r#"{ "alice", [ "rust", "python", "go" ] }"#;
    let rows = Vec::<JsonTRow>::parse(input).unwrap();
    let row = &rows[0];

    assert_eq!(row.get(0), Some(&JsonTValue::str("alice")));

    match row.get(1) {
        Some(JsonTValue::Array(arr)) => {
            assert_eq!(arr.len(), 3);
            assert_eq!(arr.get(0), Some(&JsonTValue::str("rust")));
            assert_eq!(arr.get(1), Some(&JsonTValue::str("python")));
            assert_eq!(arr.get(2), Some(&JsonTValue::str("go")));
        }
        other => panic!("expected Array, got {other:?}"),
    }
}

#[test]
fn test_parse_row_with_numeric_array() {
    let input = r#"{ "scores", [ 10, 20, 30 ] }"#;
    let rows = Vec::<JsonTRow>::parse(input).unwrap();

    if let Some(JsonTValue::Array(arr)) = rows[0].get(1) {
        assert_eq!(arr.len(), 3);
        assert!((arr.get(0).unwrap().as_f64().unwrap() - 10.0).abs() < 1e-10);
        assert!((arr.get(2).unwrap().as_f64().unwrap() - 30.0).abs() < 1e-10);
    } else {
        panic!("expected Array");
    }
}

#[test]
fn test_parse_row_with_mixed_array() {
    let input = r#"{ [ "hello", 42, true, null ] }"#;
    let rows = Vec::<JsonTRow>::parse(input).unwrap();

    if let Some(JsonTValue::Array(arr)) = rows[0].get(0) {
        assert_eq!(arr.len(), 4);
        assert_eq!(arr.get(0), Some(&JsonTValue::str("hello")));
        assert_eq!(arr.get(2), Some(&JsonTValue::bool(true)));
        assert_eq!(arr.get(3), Some(&JsonTValue::Null));
    } else {
        panic!("expected Array");
    }
}

// =============================================================================
// Complete document parse: namespace + data rows in one source string
// =============================================================================

#[test]
fn test_parse_full_document_namespace_then_data_rows() {
    // A full .jsont document — namespace first, then data rows.
    // JsonTNamespace::parse() extracts the schema side;
    // Vec<JsonTRow>::parse() is called on just the data rows portion.
    use jsont::JsonTNamespace;

    let namespace_src = r#"
    {
        namespace: {
            baseUrl: "https://example.com",
            version: "1.0",
            catalogs: [{
                schemas: [
                    Person: {
                        fields: { i64: id, str: name, bool: active }
                    }
                ]
            }],
            data-schema: Person
        }
    }
    "#;

    let data_src = r#"
        { 1, "alice", true  },
        { 2, "bob",   false },
        { 3, "carol", true  }
    "#;

    // Namespace parsed successfully
    let ns = JsonTNamespace::parse(namespace_src).unwrap();
    assert_eq!(ns.data_schema, "Person");

    // Data rows parsed separately
    let rows = Vec::<JsonTRow>::parse(data_src).unwrap();
    assert_eq!(rows.len(), 3);

    assert_eq!(rows[0].get(0), Some(&JsonTValue::d64(1.0)));
    assert_eq!(rows[0].get(1), Some(&JsonTValue::str("alice")));
    assert_eq!(rows[0].get(2), Some(&JsonTValue::bool(true)));

    assert_eq!(rows[1].get(1), Some(&JsonTValue::str("bob")));
    assert_eq!(rows[2].get(0), Some(&JsonTValue::d64(3.0)));
}

// =============================================================================
// Round-trip tests: build → stringify → parse → compare
//
// The parser always produces JsonTValue::d64 for numeric literals regardless
// of the source type (no schema context at parse time). Numeric values in the
// builder must therefore use d64 for the parsed row to be structurally equal
// to the original.
// =============================================================================

#[test]
fn test_roundtrip_scalar_fields() {
    use jsont::{JsonTRowBuilder, Stringification, StringifyOptions};

    // Step a. Build a row with scalars covering the main value types.
    let original = JsonTRowBuilder::new()
        .push(JsonTValue::str("alice"))
        .push(JsonTValue::d64(30.0))
        .push(JsonTValue::bool(true))
        .push(JsonTValue::Null)
        .push(JsonTValue::d64(99.5))
        .build();

    // Step b. Stringify to a JsonT data-row string.
    let text = original.stringify(StringifyOptions::compact());
    // Produces: {"alice", 30, true, null, 99.5}
    assert!(text.starts_with('{') && text.ends_with('}'),
        "stringified row should be wrapped in braces, got: {text}");

    // Step c. Parse the string back into a row.
    let rows = Vec::<JsonTRow>::parse(&text).unwrap();
    assert_eq!(rows.len(), 1, "expected exactly one row");
    let parsed = &rows[0];

    // Step d. Both rows must be identical.
    assert_eq!(parsed, &original,
        "parsed row differs from original\n  original: {original:?}\n  parsed:   {parsed:?}");
}

#[test]
fn test_roundtrip_with_enum_and_unspecified() {
    use jsont::{JsonTRowBuilder, Stringification, StringifyOptions};

    let original = JsonTRowBuilder::new()
        .push(JsonTValue::str("bob"))
        .push(JsonTValue::Enum("ADMIN".into()))
        .push(JsonTValue::Unspecified)
        .push(JsonTValue::d64(42.0))
        .build();

    let text = original.stringify(StringifyOptions::compact());

    let rows = Vec::<JsonTRow>::parse(&text).unwrap();
    assert_eq!(rows.len(), 1);
    assert_eq!(&rows[0], &original);
}

#[test]
fn test_roundtrip_with_nested_object() {
    use jsont::{JsonTRowBuilder, Stringification, StringifyOptions};

    // Build a nested address row to embed as an object field.
    let address = JsonTRowBuilder::new()
        .push(JsonTValue::str("123 Main St"))
        .push(JsonTValue::str("Springfield"))
        .build();

    let original = JsonTRowBuilder::new()
        .push(JsonTValue::str("carol"))
        .push(JsonTValue::d64(28.0))
        .push(JsonTValue::Object(address))
        .build();

    let text = original.stringify(StringifyOptions::compact());
    // Produces: {"carol", 28, {"123 Main St", "Springfield"}}

    let rows = Vec::<JsonTRow>::parse(&text).unwrap();
    assert_eq!(rows.len(), 1);
    assert_eq!(&rows[0], &original);
}

#[test]
fn test_roundtrip_with_array_field() {
    use jsont::{JsonTArrayBuilder, JsonTRowBuilder, Stringification, StringifyOptions};

    let tags = JsonTArrayBuilder::new()
        .push(JsonTValue::str("rust"))
        .push(JsonTValue::str("systems"))
        .push(JsonTValue::str("fast"))
        .build();

    let original = JsonTRowBuilder::new()
        .push(JsonTValue::str("dave"))
        .push(JsonTValue::d64(5.0))
        .push(JsonTValue::Array(tags))
        .build();

    let text = original.stringify(StringifyOptions::compact());
    // Produces: {"dave", 5, ["rust", "systems", "fast"]}

    let rows = Vec::<JsonTRow>::parse(&text).unwrap();
    assert_eq!(rows.len(), 1);
    assert_eq!(&rows[0], &original);
}

// =============================================================================
// Row structure checks
// =============================================================================

#[test]
fn test_row_len_and_is_empty() {
    let rows = Vec::<JsonTRow>::parse(r#"{ "a", "b", "c" }"#).unwrap();
    let row = &rows[0];
    assert_eq!(row.len(), 3);
    assert!(!row.is_empty());
}

#[test]
fn test_row_get_out_of_bounds_returns_none() {
    let rows = Vec::<JsonTRow>::parse(r#"{ "x" }"#).unwrap();
    assert!(rows[0].get(1).is_none());
}

// =============================================================================
// Error paths
// =============================================================================

#[test]
fn test_parse_empty_input_returns_err() {
    assert!(Vec::<JsonTRow>::parse("").is_err());
}

#[test]
fn test_parse_invalid_syntax_returns_err() {
    // Missing closing brace
    assert!(Vec::<JsonTRow>::parse(r#"{ "alice", 30"#).is_err());
}

#[test]
fn test_parse_empty_object_returns_err() {
    // object_value requires at least one value — grammar: value ~ ("," ~ value)*
    assert!(Vec::<JsonTRow>::parse(r#"{}"#).is_err());
}
