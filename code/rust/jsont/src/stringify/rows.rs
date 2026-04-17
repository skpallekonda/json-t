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

use sha2::{Digest, Sha256};
use rand::RngCore;

use crate::crypto::{
    assemble_field_payload, build_encrypt_header_row,
    CryptoConfig, CryptoContext, CryptoError,
};
use crate::model::data::{JsonTArray, JsonTNumber, JsonTRow, JsonTString, JsonTValue};
use crate::model::field::{JsonTField, JsonTFieldKind};

const DEK_LEN: usize = 32; // AES-256 key size in bytes

// =============================================================================
// Public API — schema-free writers
// =============================================================================

/// Write one data row to `w` in compact JsonT format: `{v1,v2,...}`.
///
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
// Public API — stream-level encrypted writers (Step 5)
// =============================================================================

/// Write a complete encrypted JsonT stream:
///
/// 1. Generate a random 256-bit DEK.
/// 2. Wrap the DEK via `crypto.wrap_dek(version, &dek)` → write EncryptHeader row.
/// 3. For each data row: write with schema-aware encryption using the shared DEK.
///    Each sensitive field gets a fresh IV; the per-field payload carries
///    `[len_iv][len_digest][iv][SHA-256(plaintext)][enc_content]`.
/// 4. Zero the DEK before returning.
///
/// Rows are separated by `,\n` (header + first data row, then between data rows).
pub fn write_encrypted_stream<W: Write>(
    rows: &[JsonTRow],
    fields: &[JsonTField],
    crypto: &dyn CryptoConfig,
    version: u16,
    w: &mut W,
) -> io::Result<()> {
    // Generate raw DEK.
    let mut dek = [0u8; DEK_LEN];
    rand::thread_rng().fill_bytes(&mut dek);

    let result = write_encrypted_stream_with_dek(rows, fields, &dek, crypto, version, w);

    // Zero the DEK regardless of outcome.
    dek.fill(0);
    result
}

fn write_encrypted_stream_with_dek<W: Write>(
    rows: &[JsonTRow],
    fields: &[JsonTField],
    dek: &[u8],
    crypto: &dyn CryptoConfig,
    version: u16,
    w: &mut W,
) -> io::Result<()> {
    // Wrap DEK and write EncryptHeader.
    let enc_dek = crypto
        .wrap_dek(version, dek)
        .map_err(|e| io::Error::new(io::ErrorKind::Other, e.to_string()))?;
    let ctx = CryptoContext::new(version, enc_dek);
    let header = build_encrypt_header_row(&ctx);
    write_row(&header, w)?;

    // Write data rows.
    for row in rows {
        w.write_all(b",\n")?;
        write_row_with_dek(row, fields, dek, crypto, w)?;
    }
    Ok(())
}

/// Write one data row with schema-aware encryption using a plaintext DEK.
///
/// - Sensitive fields with a **plaintext** value: encrypt via `crypto.encrypt_field`,
///   compute SHA-256(plaintext), assemble per-field payload, base64-encode.
/// - Sensitive fields already holding an `Encrypted` value: re-encode the stored
///   payload bytes as base64 (no crypto call — payload already assembled).
/// - Non-sensitive fields: written normally.
pub fn write_row_with_dek<W: Write>(
    row: &JsonTRow,
    fields: &[JsonTField],
    dek: &[u8],
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
            let payload: Vec<u8> = match v {
                // Already a fully-assembled payload — re-encode as-is.
                JsonTValue::Encrypted(payload_bytes) => payload_bytes.clone(),
                // Plaintext — encrypt and assemble payload.
                other => {
                    let plaintext = value_to_text(other).into_bytes();
                    let digest = Sha256::digest(&plaintext).to_vec();
                    let (iv, enc_content) = crypto
                        .encrypt_field(dek, &plaintext)
                        .map_err(|e| io::Error::new(io::ErrorKind::Other, e.to_string()))?;
                    assemble_field_payload(&iv, &digest, &enc_content)
                }
            };
            let b64 = base64::engine::general_purpose::STANDARD.encode(&payload);
            write_quoted_str(&b64, w)?;
        } else {
            write_value(v, w)?;
        }
    }
    w.write_all(b"}")
}

// =============================================================================
// Private helpers
// =============================================================================

/// Produce the wire-format text of a non-encrypted value (used before encrypting).
fn value_to_text(v: &JsonTValue) -> String {
    match v {
        JsonTValue::Null        => "null".into(),
        JsonTValue::Unspecified => "_".into(),
        JsonTValue::Bool(b)     => b.to_string(),
        JsonTValue::Str(s)      => s.as_raw_str().to_string(),
        JsonTValue::Enum(e)     => e.clone(),
        JsonTValue::Number(n)   => {
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
        _ => "<complex>".into(),
    }
}

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
        // Encrypted values hold raw payload bytes. Re-encode as plain base64.
        JsonTValue::Encrypted(payload) => {
            use base64::Engine as _;
            let b64 = base64::engine::general_purpose::STANDARD.encode(payload);
            write_quoted_str(&b64, w)
        }
    }
}

/// Write a string surrounded by double-quotes, escaping `"` and `\`.
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
