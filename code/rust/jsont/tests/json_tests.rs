// =============================================================================
// tests/json_tests.rs — JSON interoperability tests
// =============================================================================
// Coverage:
//   1. Parser boundary conditions (via JsonReader): valid numbers, negatives,
//      floats, exponents, unicode escapes, and INVALID inputs like .1234
//   2. JsonWriter: field names, all modes (NDJSON / Array), string escaping
//   3. Round-trip: JsonTRow → JsonT wire → JSON → JsonTRow → JsonT wire,
//      both strings must be byte-for-byte identical
// =============================================================================

use jsont::{
    JsonTFieldBuilder, JsonTRowBuilder, JsonTSchemaBuilder, JsonTValue, ScalarType,
    write_rows,
};
use jsont::json::{
    JsonInputMode, JsonOutputMode, JsonReader, JsonWriter,
    MissingFieldPolicy, UnknownFieldPolicy,
};

// ── Shared test schema ────────────────────────────────────────────────────────
//
// Order: id(i64) | product(str) | qty(i32) | active(bool) | price(d64, optional)
//
// Uses only integer, string, bool, and exact-binary-float types so that
// JSON serialisation and JsonT stringification produce identical text.

fn order_schema() -> jsont::JsonTSchema {
    JsonTSchemaBuilder::straight("Order")
        .field_from(JsonTFieldBuilder::scalar("id",      ScalarType::I64)).unwrap()
        .field_from(JsonTFieldBuilder::scalar("product", ScalarType::Str)).unwrap()
        .field_from(JsonTFieldBuilder::scalar("qty",     ScalarType::I32)).unwrap()
        .field_from(JsonTFieldBuilder::scalar("active",  ScalarType::Bool)).unwrap()
        .field_from(JsonTFieldBuilder::scalar("price",   ScalarType::D64).optional()).unwrap()
        .build()
        .unwrap()
}

/// Build a single Order row. `price` is `None` → Null.
fn order_row(id: i64, product: &str, qty: i32, active: bool, price: Option<f64>) -> jsont::JsonTRow {
    JsonTRowBuilder::new()
        .push(JsonTValue::i64(id))
        .push(JsonTValue::str(product))
        .push(JsonTValue::i32(qty))
        .push(JsonTValue::bool(active))
        .push(price.map_or(JsonTValue::null(), JsonTValue::d64))
        .build()
}

// =============================================================================
// 1. Parser boundary conditions (exercised through JsonReader::read)
// =============================================================================

#[test]
fn parser_plain_integer() {
    let reader = JsonReader::with_schema(order_schema())
        .mode(JsonInputMode::Object)
        .build();
    let mut rows = Vec::new();
    let n = reader.read(
        r#"{"id":42,"product":"A","qty":1,"active":true,"price":null}"#,
        |row| rows.push(row),
    ).unwrap();
    assert_eq!(n, 1);
    assert_eq!(rows[0].fields[0], JsonTValue::i64(42));
}

#[test]
fn parser_negative_integer() {
    let reader = JsonReader::with_schema(order_schema())
        .mode(JsonInputMode::Object)
        .build();
    let mut rows = Vec::new();
    reader.read(
        r#"{"id":-99,"product":"X","qty":-1,"active":false,"price":null}"#,
        |row| rows.push(row),
    ).unwrap();
    assert_eq!(rows[0].fields[0], JsonTValue::i64(-99));
    assert_eq!(rows[0].fields[2], JsonTValue::i32(-1));
}

#[test]
fn parser_float_decimal() {
    let reader = JsonReader::with_schema(order_schema())
        .mode(JsonInputMode::Object)
        .build();
    let mut rows = Vec::new();
    reader.read(
        r#"{"id":1,"product":"X","qty":1,"active":true,"price":8.5}"#,
        |row| rows.push(row),
    ).unwrap();
    assert_eq!(rows[0].fields[4], JsonTValue::d64(8.5));
}

#[test]
fn parser_float_exponent() {
    // 1.5e2 = 150.0 — exponent notation must parse correctly
    let reader = JsonReader::with_schema(order_schema())
        .mode(JsonInputMode::Object)
        .build();
    let mut rows = Vec::new();
    reader.read(
        r#"{"id":1,"product":"X","qty":1,"active":true,"price":1.5e2}"#,
        |row| rows.push(row),
    ).unwrap();
    assert_eq!(rows[0].fields[4], JsonTValue::d64(150.0));
}

#[test]
fn parser_float_negative_exponent() {
    // 5e-1 = 0.5
    let reader = JsonReader::with_schema(order_schema())
        .mode(JsonInputMode::Object)
        .build();
    let mut rows = Vec::new();
    reader.read(
        r#"{"id":1,"product":"X","qty":1,"active":true,"price":5e-1}"#,
        |row| rows.push(row),
    ).unwrap();
    assert_eq!(rows[0].fields[4], JsonTValue::d64(0.5));
}

/// JSON forbids leading-decimal numbers like .1234 — parser must reject them.
#[test]
fn parser_rejects_leading_decimal_dot() {
    let reader = JsonReader::with_schema(order_schema())
        .mode(JsonInputMode::Object)
        .build();
    // .1234 is not valid JSON — the number field starts with '.'
    let result = reader.read(
        r#"{"id":.1234,"product":"X","qty":1,"active":true,"price":null}"#,
        |_| {},
    );
    assert!(result.is_err(), "leading-decimal .1234 must be rejected as invalid JSON");
}

#[test]
fn parser_rejects_empty_input() {
    let reader = JsonReader::with_schema(order_schema())
        .mode(JsonInputMode::Object)
        .build();
    assert!(reader.read("", |_| {}).is_err(), "empty input must fail");
}

#[test]
fn parser_rejects_bare_number() {
    // A bare number is not a JSON object
    let reader = JsonReader::with_schema(order_schema())
        .mode(JsonInputMode::Object)
        .build();
    assert!(reader.read("42", |_| {}).is_err(), "bare number is not a JSON object");
}

#[test]
fn parser_rejects_truncated_string() {
    let reader = JsonReader::with_schema(order_schema())
        .mode(JsonInputMode::Object)
        .build();
    let result = reader.read(
        r#"{"id":1,"product":"Trunca"#,
        |_| {},
    );
    assert!(result.is_err(), "truncated JSON input must fail");
}

#[test]
fn parser_unicode_escape() {
    // \u00e9 = 'é'
    let reader = JsonReader::with_schema(order_schema())
        .mode(JsonInputMode::Object)
        .build();
    let mut rows = Vec::new();
    reader.read(
        r#"{"id":1,"product":"Caf\u00e9","qty":1,"active":true,"price":null}"#,
        |row| rows.push(row),
    ).unwrap();
    assert_eq!(rows[0].fields[1], JsonTValue::str("Café"));
}

#[test]
fn parser_null_field() {
    let reader = JsonReader::with_schema(order_schema())
        .mode(JsonInputMode::Object)
        .build();
    let mut rows = Vec::new();
    reader.read(
        r#"{"id":1,"product":"X","qty":1,"active":true,"price":null}"#,
        |row| rows.push(row),
    ).unwrap();
    assert_eq!(rows[0].fields[4], JsonTValue::null());
}

#[test]
fn parser_ndjson_three_rows() {
    let reader = JsonReader::with_schema(order_schema())
        .mode(JsonInputMode::Ndjson)
        .build();
    let input = concat!(
        "{\"id\":1,\"product\":\"A\",\"qty\":1,\"active\":true,\"price\":null}\n",
        "{\"id\":2,\"product\":\"B\",\"qty\":2,\"active\":false,\"price\":2.5}\n",
        "{\"id\":3,\"product\":\"C\",\"qty\":3,\"active\":true,\"price\":null}",
    );
    let mut rows = Vec::new();
    let n = reader.read(input, |row| rows.push(row)).unwrap();
    assert_eq!(n, 3);
    assert_eq!(rows[0].fields[0], JsonTValue::i64(1));
    assert_eq!(rows[1].fields[4], JsonTValue::d64(2.5));
    assert_eq!(rows[2].fields[1], JsonTValue::str("C"));
}

#[test]
fn parser_ndjson_skips_blank_lines() {
    let reader = JsonReader::with_schema(order_schema())
        .mode(JsonInputMode::Ndjson)
        .build();
    let input = "\n{\"id\":1,\"product\":\"A\",\"qty\":1,\"active\":true,\"price\":null}\n\n\
                 {\"id\":2,\"product\":\"B\",\"qty\":2,\"active\":false,\"price\":null}\n";
    let mut rows = Vec::new();
    let n = reader.read(input, |row| rows.push(row)).unwrap();
    assert_eq!(n, 2);
}

#[test]
fn parser_array_mode() {
    let reader = JsonReader::with_schema(order_schema())
        .mode(JsonInputMode::Array)
        .build();
    let input = r#"[
        {"id":10,"product":"P1","qty":5,"active":true,"price":null},
        {"id":20,"product":"P2","qty":3,"active":false,"price":9.5}
    ]"#;
    let mut rows = Vec::new();
    let n = reader.read(input, |row| rows.push(row)).unwrap();
    assert_eq!(n, 2);
    assert_eq!(rows[0].fields[0], JsonTValue::i64(10));
    assert_eq!(rows[1].fields[0], JsonTValue::i64(20));
}

#[test]
fn parser_unknown_field_reject() {
    let reader = JsonReader::with_schema(order_schema())
        .mode(JsonInputMode::Object)
        .unknown_fields(UnknownFieldPolicy::Reject)
        .build();
    let result = reader.read(
        r#"{"id":1,"product":"X","qty":1,"active":true,"price":null,"extra":"oops"}"#,
        |_| {},
    );
    assert!(result.is_err(), "unknown field must be rejected");
}

#[test]
fn parser_missing_field_reject() {
    let reader = JsonReader::with_schema(order_schema())
        .mode(JsonInputMode::Object)
        .missing_fields(MissingFieldPolicy::Reject)
        .build();
    // Only 'id' and 'product' present — qty, active, price missing
    let result = reader.read(r#"{"id":1,"product":"X"}"#, |_| {});
    assert!(result.is_err(), "missing required field must be rejected");
}

#[test]
fn parser_missing_field_use_default() {
    // With UseDefault policy missing optional fields become Null
    let reader = JsonReader::with_schema(order_schema())
        .mode(JsonInputMode::Object)
        .missing_fields(MissingFieldPolicy::UseDefault)
        .build();
    let mut rows = Vec::new();
    reader.read(
        r#"{"id":1,"product":"X","qty":5,"active":true}"#,
        |row| rows.push(row),
    ).unwrap();
    // price is missing → Null
    assert_eq!(rows[0].fields[4], JsonTValue::null());
}

// =============================================================================
// 2. Writer
// =============================================================================

#[test]
fn writer_single_row_compact_keys() {
    let writer = JsonWriter::with_schema(order_schema()).build();
    let row = order_row(1, "Widget", 5, true, None);
    let mut out = Vec::new();
    writer.write_row(&row, &mut out).unwrap();
    let json = String::from_utf8(out).unwrap();
    // All schema field names must appear
    assert!(json.contains(r#""id""#),      "missing 'id'");
    assert!(json.contains(r#""product""#), "missing 'product'");
    assert!(json.contains(r#""qty""#),     "missing 'qty'");
    assert!(json.contains(r#""active""#),  "missing 'active'");
    assert!(json.contains(r#""price""#),   "missing 'price'");
    // Spot-check values
    assert!(json.contains(r#""id":1"#));
    assert!(json.contains(r#""product":"Widget""#));
    assert!(json.contains(r#""active":true"#));
    assert!(json.contains(r#""price":null"#));
}

#[test]
fn writer_ndjson_produces_one_line_per_row() {
    let writer = JsonWriter::with_schema(order_schema())
        .mode(JsonOutputMode::Ndjson)
        .build();
    let rows = vec![
        order_row(1, "A", 1, true,  None),
        order_row(2, "B", 2, false, Some(3.5)),
        order_row(3, "C", 3, true,  None),
    ];
    let mut out = Vec::new();
    writer.write_rows(&rows, &mut out).unwrap();
    let text = String::from_utf8(out).unwrap();
    let lines: Vec<&str> = text.lines().collect();
    assert_eq!(lines.len(), 3, "NDJSON must produce exactly one line per row");
    assert!(lines[0].contains(r#""id":1"#));
    assert!(lines[2].contains(r#""id":3"#));
}

#[test]
fn writer_array_mode_wraps_in_brackets() {
    let writer = JsonWriter::with_schema(order_schema())
        .mode(JsonOutputMode::Array)
        .build();
    let rows = vec![order_row(1, "A", 1, true, None), order_row(2, "B", 2, false, None)];
    let mut out = Vec::new();
    writer.write_rows(&rows, &mut out).unwrap();
    let text = String::from_utf8(out).unwrap();
    assert!(text.trim_start().starts_with('['), "Array mode must start with '['");
    assert!(text.trim_end().ends_with(']'),   "Array mode must end with ']'");
}

#[test]
fn writer_pretty_mode_indented() {
    let writer = JsonWriter::with_schema(order_schema())
        .pretty(true)
        .build();
    let row = order_row(1, "Gadget", 3, true, Some(8.5));
    let mut out = Vec::new();
    writer.write_row(&row, &mut out).unwrap();
    let text = String::from_utf8(out).unwrap();
    // Pretty output must span multiple lines
    assert!(text.lines().count() > 1, "pretty output must be multi-line");
}

#[test]
fn writer_string_escaping() {
    // Strings with special characters must be properly escaped
    let writer = JsonWriter::with_schema(order_schema()).build();
    let row = order_row(1, "say \"hi\"\nline2\ttab", 1, true, None);
    let mut out = Vec::new();
    writer.write_row(&row, &mut out).unwrap();
    let json = String::from_utf8(out).unwrap();
    assert!(json.contains(r#"\""#), "double-quote must be escaped as \\\"");
    assert!(json.contains(r#"\n"#), "newline must be escaped as \\n");
    assert!(json.contains(r#"\t"#), "tab must be escaped as \\t");
}

#[test]
fn writer_empty_row_slice() {
    let writer = JsonWriter::with_schema(order_schema())
        .mode(JsonOutputMode::Array)
        .build();
    let mut out = Vec::new();
    writer.write_rows(&[], &mut out).unwrap();
    let text = String::from_utf8(out).unwrap();
    // Should produce "[]" (empty array)
    assert!(text.contains('[') && text.contains(']'));
}

// =============================================================================
// 3. Round-trip: JsonT → JSON → JsonT (strings must be byte-for-byte identical)
// =============================================================================

fn jsont_string(rows: &[jsont::JsonTRow]) -> String {
    let mut buf = Vec::new();
    write_rows(rows, &mut buf).unwrap();
    String::from_utf8(buf).unwrap()
}

#[test]
fn round_trip_ndjson() {
    let schema = order_schema();
    let rows = vec![
        order_row(1001, "Gadget",  7, true,  None),
        order_row(1002, "Widget",  3, false, Some(8.5)),
        order_row(1003, "Doohick", 1, true,  None),
    ];

    // Original JsonT wire string
    let original = jsont_string(&rows);

    // Write to JSON (NDJSON)
    let writer = JsonWriter::with_schema(schema.clone())
        .mode(JsonOutputMode::Ndjson)
        .build();
    let mut json_buf = Vec::new();
    writer.write_rows(&rows, &mut json_buf).unwrap();
    let json_str = String::from_utf8(json_buf).unwrap();

    // Read JSON back into rows
    let reader = JsonReader::with_schema(schema)
        .mode(JsonInputMode::Ndjson)
        .build();
    let mut roundtrip_rows = Vec::new();
    reader.read(&json_str, |row| roundtrip_rows.push(row)).unwrap();
    assert_eq!(roundtrip_rows.len(), 3);

    // Re-stringify and compare
    let roundtrip = jsont_string(&roundtrip_rows);
    assert_eq!(original, roundtrip,
        "JsonT wire strings must match after NDJSON round-trip\nOriginal : {original}\nRoundtrip: {roundtrip}");
}

#[test]
fn round_trip_array_mode() {
    let schema = order_schema();
    let rows: Vec<_> = (1..=4)
        .map(|i| order_row(i, &format!("Item{i}"), i as i32 * 10, i % 2 == 0, None))
        .collect();

    let original = jsont_string(&rows);

    let writer = JsonWriter::with_schema(schema.clone())
        .mode(JsonOutputMode::Array)
        .build();
    let mut json_buf = Vec::new();
    writer.write_rows(&rows, &mut json_buf).unwrap();
    let json_str = String::from_utf8(json_buf).unwrap();

    let reader = JsonReader::with_schema(schema)
        .mode(JsonInputMode::Array)
        .build();
    let mut roundtrip_rows = Vec::new();
    reader.read(&json_str, |row| roundtrip_rows.push(row)).unwrap();

    assert_eq!(original, jsont_string(&roundtrip_rows),
        "JsonT wire strings must match after Array round-trip");
}

#[test]
fn round_trip_with_nulls_preserved() {
    // Optional fields that are null must survive the JSON round-trip as null
    let schema = order_schema();
    let rows = vec![
        order_row(1, "A", 1, true,  None),   // price = null
        order_row(2, "B", 2, false, Some(1.0)), // price = 1.0
        order_row(3, "C", 3, true,  None),   // price = null
    ];
    let original = jsont_string(&rows);

    let mut json_buf = Vec::new();
    JsonWriter::with_schema(schema.clone())
        .mode(JsonOutputMode::Ndjson)
        .build()
        .write_rows(&rows, &mut json_buf)
        .unwrap();

    let mut roundtrip_rows = Vec::new();
    JsonReader::with_schema(schema)
        .mode(JsonInputMode::Ndjson)
        .build()
        .read(&String::from_utf8(json_buf).unwrap(), |row| roundtrip_rows.push(row))
        .unwrap();

    assert_eq!(original, jsont_string(&roundtrip_rows));
}

#[test]
fn round_trip_special_chars_in_strings() {
    // Strings with quotes and backslashes must survive the round-trip intact
    let schema = order_schema();
    let rows = vec![order_row(1, r#"O'Brien & "Co" \ path"#, 1, true, None)];
    let original = jsont_string(&rows);

    let mut json_buf = Vec::new();
    JsonWriter::with_schema(schema.clone())
        .build()
        .write_row(&rows[0], &mut json_buf)
        .unwrap();

    let mut roundtrip_rows = Vec::new();
    JsonReader::with_schema(schema)
        .mode(JsonInputMode::Object)
        .build()
        .read(&String::from_utf8(json_buf).unwrap(), |row| roundtrip_rows.push(row))
        .unwrap();

    assert_eq!(original, jsont_string(&roundtrip_rows),
        "special characters must survive JSON round-trip");
}
