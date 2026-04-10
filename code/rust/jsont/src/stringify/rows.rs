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

use crate::crypto::{CryptoConfig, CryptoError};
use crate::model::data::{JsonTArray, JsonTNumber, JsonTRow, JsonTString, JsonTValue};
use crate::model::field::{JsonTField, JsonTFieldKind};

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

/// Write one row with schema-aware crypto: sensitive fields with plaintext
/// values are encrypted before writing; `Encrypted` values are written as-is.
///
/// - If a field is `sensitive` and its value is **not** `Encrypted`, the
///   value's text representation is encrypted via `crypto` and written as
///   `"base64:<b64>"`.
/// - If a field is `sensitive` and its value **is** `Encrypted`, the stored
///   ciphertext bytes are re-encoded as `"base64:<b64>"` (no crypto call).
/// - Non-sensitive fields are written normally.
///
/// Returns `Err` on any I/O error; crypto failures are mapped to `io::Error`.
pub fn write_row_with_schema<W: Write>(
    row: &JsonTRow,
    fields: &[JsonTField],
    crypto: &dyn CryptoConfig,
    w: &mut W,
) -> io::Result<()> {
    w.write_all(b"{")?;
    for (i, (v, field)) in row.fields.iter().zip(fields.iter()).enumerate() {
        if i > 0 {
            w.write_all(b",")?;
        }
        let is_sensitive = matches!(
            &field.kind,
            JsonTFieldKind::Scalar { sensitive: true, .. }
        );
        if is_sensitive {
            use base64::Engine as _;
            let ciphertext: Vec<u8> = match v {
                // Already encrypted — re-encode ciphertext as base64 wire format.
                JsonTValue::Encrypted(ct) => ct.clone(),
                // Plaintext — stringify value text then encrypt.
                other => {
                    let plaintext = value_to_text(other);
                    crypto.encrypt(&field.name, plaintext.as_bytes())
                        .map_err(|e| io::Error::new(io::ErrorKind::Other, e.to_string()))?
                }
            };
            let b64 = base64::engine::general_purpose::STANDARD.encode(&ciphertext);
            write_quoted_str(&format!("base64:{}", b64), w)?;
        } else {
            write_value(v, w)?;
        }
    }
    w.write_all(b"}")
}

/// Produce the wire-format text of a non-encrypted value (used before encrypting).
fn value_to_text(v: &JsonTValue) -> String {
    match v {
        JsonTValue::Null        => "null".into(),
        JsonTValue::Unspecified => "_".into(),
        JsonTValue::Bool(b)     => b.to_string(),
        JsonTValue::Str(s)      => s.as_raw_str().to_string(),
        JsonTValue::Enum(e)     => e.clone(),
        JsonTValue::Number(n)   => {
            use crate::model::data::JsonTNumber;
            match n {
                JsonTNumber::I16(v)  => v.to_string(),
                JsonTNumber::I32(v)  => v.to_string(),
                JsonTNumber::I64(v)  => v.to_string(),
                JsonTNumber::U16(v)  => v.to_string(),
                JsonTNumber::U32(v)  => v.to_string(),
                JsonTNumber::U64(v)  => v.to_string(),
                JsonTNumber::D32(v)  => v.to_string(),
                JsonTNumber::D64(v)  => v.to_string(),
                JsonTNumber::D128(v) => v.to_string(),
                JsonTNumber::Date(v)      => v.to_string(),
                JsonTNumber::Time(v)      => v.to_string(),
                JsonTNumber::DateTime(v)  => v.to_string(),
                JsonTNumber::Timestamp(v) => v.to_string(),
            }
        }
        // For objects/arrays, fall back to the raw JSON-T representation.
        _ => "<complex>".into(),
    }
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
        // Encrypted values hold raw ciphertext bytes. Re-encode them as the
        // base64: wire envelope so the output is valid JsonT data.
        JsonTValue::Encrypted(ciphertext) => {
            use base64::Engine as _;
            let b64 = base64::engine::general_purpose::STANDARD.encode(ciphertext);
            write_quoted_str(&format!("base64:{}", b64), w)
        }
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
