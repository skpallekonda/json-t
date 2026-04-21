// =============================================================================
// tests/streaming_tests.rs — Unit tests for parse_rows_streaming and RowIter
// =============================================================================
//
// Key goal: exercise chunk-boundary crossing by using tiny BufReader capacities
// (as small as 1 byte), ensuring the state machine correctly accumulates rows
// that span multiple fill_buf() refills.
//
// Run:
//   cargo test --test streaming_tests -- --nocapture
// =============================================================================

use std::io::{BufReader, Cursor};

use jsont::{parse_rows_streaming, RowIter, JsonTValue};

// ── Helpers ────────────────────────────────────────────────────────────────────

/// Parse `input` with a BufReader of exactly `chunk` bytes capacity.
/// Returns the list of field-value vectors for each row.
fn parse_with_chunk(input: &str, chunk: usize) -> Vec<Vec<JsonTValue>> {
    let reader = BufReader::with_capacity(chunk, Cursor::new(input.as_bytes()));
    let mut rows = Vec::new();
    parse_rows_streaming(reader, |row| {
        rows.push(row.fields);
    })
    .expect("parse_rows_streaming failed");
    rows
}

/// Same, using RowIter instead of the callback form.
fn iter_with_chunk(input: &str, chunk: usize) -> Vec<Vec<JsonTValue>> {
    let reader = BufReader::with_capacity(chunk, Cursor::new(input.as_bytes()));
    RowIter::new(reader).map(|row| row.fields).collect()
}

// ── Basic single-row ──────────────────────────────────────────────────────────

#[test]
fn single_row_large_chunk() {
    let rows = parse_with_chunk("{ 1, \"alice\" }", 4096);
    assert_eq!(rows.len(), 1);
    assert_eq!(rows[0][0], JsonTValue::d64(1.0));
    assert_eq!(rows[0][1], JsonTValue::Str("alice".into()));
}

#[test]
fn single_row_1_byte_chunk() {
    // Every single byte is a separate fill_buf() call.
    let rows = parse_with_chunk("{ 42, \"hello\" }", 1);
    assert_eq!(rows.len(), 1);
    assert_eq!(rows[0][0], JsonTValue::d64(42.0));
    assert_eq!(rows[0][1], JsonTValue::Str("hello".into()));
}

// ── Multiple rows ─────────────────────────────────────────────────────────────

#[test]
fn two_rows_standard_chunk() {
    let input = "{ 1, \"alice\" },\n{ 2, \"bob\" }";
    let rows  = parse_with_chunk(input, 4096);
    assert_eq!(rows.len(), 2);
    assert_eq!(rows[1][1], JsonTValue::Str("bob".into()));
}

#[test]
fn two_rows_2_byte_chunk() {
    // Row boundary falls at many different offsets depending on chunk size.
    let input = "{ 1, \"alice\" },\n{ 2, \"bob\" }";
    let rows  = parse_with_chunk(input, 2);
    assert_eq!(rows.len(), 2);
    assert_eq!(rows[0][0], JsonTValue::d64(1.0));
    assert_eq!(rows[1][0], JsonTValue::d64(2.0));
}

#[test]
fn five_rows_3_byte_chunk() {
    let input = "{ 1 }, { 2 }, { 3 }, { 4 }, { 5 }";
    let rows  = parse_with_chunk(input, 3);
    assert_eq!(rows.len(), 5);
    for (i, row) in rows.iter().enumerate() {
        assert_eq!(row[0], JsonTValue::d64((i + 1) as f64));
    }
}

// ── Trailing comma (grammar allows it) ───────────────────────────────────────

#[test]
fn trailing_comma_after_last_row() {
    let rows = parse_with_chunk("{ 1 }, { 2 },", 4096);
    assert_eq!(rows.len(), 2);
}

#[test]
fn trailing_comma_tiny_chunk() {
    let rows = parse_with_chunk("{ 1 }, { 2 },", 2);
    assert_eq!(rows.len(), 2);
}

// ── Empty input ───────────────────────────────────────────────────────────────

#[test]
fn empty_input() {
    let rows = parse_with_chunk("", 4096);
    assert!(rows.is_empty());
}

#[test]
fn whitespace_only() {
    let rows = parse_with_chunk("   \n\t  ", 4096);
    assert!(rows.is_empty());
}

// ── Strings containing braces (must not affect depth tracking) ───────────────

#[test]
fn string_with_braces_large_chunk() {
    let rows = parse_with_chunk(r#"{ 1, "has {braces} inside" }"#, 4096);
    assert_eq!(rows.len(), 1);
    assert_eq!(rows[0][1], JsonTValue::Str("has {braces} inside".into()));
}

#[test]
fn string_with_braces_1_byte_chunk() {
    let rows = parse_with_chunk(r#"{ 1, "has {braces} inside" }"#, 1);
    assert_eq!(rows.len(), 1);
    assert_eq!(rows[0][1], JsonTValue::Str("has {braces} inside".into()));
}

#[test]
fn string_with_escaped_quote() {
    // The raw content between the outer quotes is: say \"hi\"
    let rows = parse_with_chunk(r#"{ "say \"hi\"" }"#, 4096);
    assert_eq!(rows.len(), 1);
    // Scanner stores raw bytes (backslash sequences are not unescaped).
    assert_eq!(rows[0][0], JsonTValue::Str(r#"say \"hi\""#.into()));
}

// ── Nested objects (depth > 1) ────────────────────────────────────────────────

#[test]
fn nested_object_large_chunk() {
    // Inner object: { "street", "city" }
    let rows = parse_with_chunk(r#"{ 1, { "street", "city" } }"#, 4096);
    assert_eq!(rows.len(), 1);
    assert_eq!(rows[0][0], JsonTValue::d64(1.0));
    // Inner value is an Object variant
    assert!(matches!(rows[0][1], JsonTValue::Object(_)));
}

#[test]
fn nested_object_1_byte_chunk() {
    let rows = parse_with_chunk(r#"{ 1, { "street", "city" } }"#, 1);
    assert_eq!(rows.len(), 1);
    assert!(matches!(rows[0][1], JsonTValue::Object(_)));
}

#[test]
fn two_rows_with_nested_objects_tiny_chunk() {
    let input = r#"{ 1, { "a", "b" } }, { 2, { "c", "d" } }"#;
    let rows  = parse_with_chunk(input, 4);
    assert_eq!(rows.len(), 2);
    assert_eq!(rows[0][0], JsonTValue::d64(1.0));
    assert_eq!(rows[1][0], JsonTValue::d64(2.0));
}

// ── Arrays inside rows ────────────────────────────────────────────────────────

#[test]
fn array_in_row_tiny_chunk() {
    let rows = parse_with_chunk("{ 1, [2, 3, 4] }", 3);
    assert_eq!(rows.len(), 1);
    assert!(matches!(rows[0][1], JsonTValue::Array(_)));
}

// ── RowIter — same coverage, iterator form ────────────────────────────────────

#[test]
fn row_iter_single_row() {
    let rows = iter_with_chunk("{ 99, \"z\" }", 4096);
    assert_eq!(rows.len(), 1);
    assert_eq!(rows[0][0], JsonTValue::d64(99.0));
}

#[test]
fn row_iter_chunk_boundary() {
    let input = "{ 1, \"x\" }, { 2, \"y\" }, { 3, \"z\" }";
    let rows  = iter_with_chunk(input, 5);
    assert_eq!(rows.len(), 3);
    assert_eq!(rows[2][1], JsonTValue::Str("z".into()));
}

#[test]
fn row_iter_is_lazy() {
    // Take only 2 rows from a 4-row source; iterator must not panic or over-read.
    let input  = "{ 1 }, { 2 }, { 3 }, { 4 }";
    let reader = BufReader::with_capacity(3, Cursor::new(input.as_bytes()));
    let first2: Vec<_> = RowIter::new(reader).take(2).map(|r| r.fields).collect();
    assert_eq!(first2.len(), 2);
    assert_eq!(first2[0][0], JsonTValue::d64(1.0));
    assert_eq!(first2[1][0], JsonTValue::d64(2.0));
}

// ── Enum constants (CONSTID) ──────────────────────────────────────────────────

#[test]
fn enum_value_in_row() {
    let rows = parse_with_chunk("{ 1, PENDING }", 4096);
    assert_eq!(rows.len(), 1);
    assert_eq!(rows[0][1], JsonTValue::Enum("PENDING".into()));
}

#[test]
fn enum_value_tiny_chunk() {
    let rows = parse_with_chunk("{ 1, SHIPPED }, { 2, DELIVERED }", 2);
    assert_eq!(rows.len(), 2);
    assert_eq!(rows[0][1], JsonTValue::Enum("SHIPPED".into()));
    assert_eq!(rows[1][1], JsonTValue::Enum("DELIVERED".into()));
}

// ── Null / unspecified values ─────────────────────────────────────────────────

#[test]
fn null_and_unspecified() {
    let rows = parse_with_chunk("{ null, _ }", 4096);
    assert_eq!(rows.len(), 1);
    assert_eq!(rows[0][0], JsonTValue::Null);
    assert_eq!(rows[0][1], JsonTValue::Unspecified);
}

// ── Large number of small rows with minimum chunk size ────────────────────────

#[test]
fn many_rows_1_byte_chunk() {
    let input: String = (1u32..=20).map(|i| format!("{{ {} }}", i)).collect::<Vec<_>>().join(", ");
    let rows = parse_with_chunk(&input, 1);
    assert_eq!(rows.len(), 20);
    for (i, row) in rows.iter().enumerate() {
        assert_eq!(row[0], JsonTValue::d64((i + 1) as f64));
    }
}
