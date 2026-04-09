// =============================================================================
// stringify/rows.rs — Zero-allocation direct writer for JsonT data rows
// =============================================================================
// WHY: The Stringification trait returns a newly-allocated String per call.
// For N rows that means N heap allocations plus N copies into the output
// buffer — measured at ~1,100 rec/ms for the benchmark schema.
//
// These functions write directly into any `impl Write` (e.g. BufWriter<File>)
// with no intermediate String, reducing stringify throughput to a single
// pass over the data with minimal allocations.
// =============================================================================

use std::io::{self, Write};

use crate::model::data::{JsonTArray, JsonTNumber, JsonTRow, JsonTValue};

// =============================================================================
// Public API
// =============================================================================

/// Write one data row to `w` in compact JsonT format.
///
/// Output: `{v1,v2,...}` with no surrounding whitespace.
/// No intermediate `String` is allocated.
pub fn write_row<W: Write>(row: &JsonTRow, w: &mut W) -> io::Result<()> {
    w.write_all(b"{")?;
    for (i, v) in row.fields.iter().enumerate() {
        if i > 0 {
            w.write_all(b",")?;
        }
        write_value(v, w)?;
    }
    w.write_all(b"}")
}

/// Write a slice of rows to `w`, separated by `,\n`.
/// No trailing newline is written after the last row.
pub fn write_rows<W: Write>(rows: &[JsonTRow], w: &mut W) -> io::Result<()> {
    let last = rows.len().saturating_sub(1);
    for (i, row) in rows.iter().enumerate() {
        write_row(row, w)?;
        if i < last {
            w.write_all(b",\n")?;
        }
    }
    Ok(())
}

// =============================================================================
// Private helpers
// =============================================================================

fn write_value<W: Write>(v: &JsonTValue, w: &mut W) -> io::Result<()> {
    match v {
        JsonTValue::Null => w.write_all(b"null"),
        JsonTValue::Unspecified => w.write_all(b"_"),
        JsonTValue::Bool(true) => w.write_all(b"true"),
        JsonTValue::Bool(false) => w.write_all(b"false"),
        JsonTValue::Str(js) => write_quoted_str(js.as_raw_str(), w),
        JsonTValue::Enum(c) => w.write_all(c.as_bytes()),
        JsonTValue::Number(n) => write_number(n, w),
        JsonTValue::Object(row) => write_row(row, w),
        JsonTValue::Array(arr) => write_array(arr, w),
        // Encrypted values carry a Base64 envelope; CryptoConfig enforcement
        // is handled in the schema-aware stringify path (Phase 7). The raw-row
        // writer should never receive an Encrypted value without prior crypto
        // resolution — panic here surfaces the programming error early.
        JsonTValue::Encrypted(_) => panic!(
            "Encrypted value cannot be written without CryptoConfig; \
             use StringifyOptions::with_crypto() (Phase 7)"
        ),
    }
}

/// Write a string surrounded by double-quotes, escaping `"` and `\`.
/// Uses a single-pass scan: flushes unescaped slices in bulk, only
/// emitting a `\` prefix for characters that need it.
fn write_quoted_str<W: Write>(s: &str, w: &mut W) -> io::Result<()> {
    w.write_all(b"\"")?;
    let bytes = s.as_bytes();
    let mut start = 0;
    for (i, &b) in bytes.iter().enumerate() {
        if b == b'"' || b == b'\\' {
            w.write_all(&bytes[start..i])?;
            w.write_all(b"\\")?;
            w.write_all(&bytes[i..i + 1])?;
            start = i + 1;
        }
    }
    w.write_all(&bytes[start..])?;
    w.write_all(b"\"")
}

fn write_number<W: Write>(n: &JsonTNumber, w: &mut W) -> io::Result<()> {
    match n {
        JsonTNumber::I16(v) => write!(w, "{}", v),
        JsonTNumber::I32(v) => write!(w, "{}", v),
        JsonTNumber::I64(v) => write!(w, "{}", v),
        JsonTNumber::U16(v) => write!(w, "{}", v),
        JsonTNumber::U32(v) => write!(w, "{}", v),
        JsonTNumber::U64(v) => write!(w, "{}", v),
        JsonTNumber::D32(v) => write!(w, "{}", v),
        JsonTNumber::D64(v) => write!(w, "{}", v),
        JsonTNumber::D128(v) => write!(w, "{}", v),
        JsonTNumber::Date(v) => write!(w, "{}", v),
        JsonTNumber::Time(v) => write!(w, "{}", v),
        JsonTNumber::DateTime(v) => write!(w, "{}", v),
        JsonTNumber::Timestamp(v) => write!(w, "{}", v),
    }
}

fn write_array<W: Write>(arr: &JsonTArray, w: &mut W) -> io::Result<()> {
    w.write_all(b"[")?;
    for (i, v) in arr.items.iter().enumerate() {
        if i > 0 {
            w.write_all(b",")?;
        }
        write_value(v, w)?;
    }
    w.write_all(b"]")
}
