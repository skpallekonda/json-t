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

use rand::RngCore;
use sha2::{Digest, Sha256};

use crate::crypto::{
    assemble_field_payload, build_encrypt_header_row, CipherSession, CryptoConfig, CryptoContext,
};
use crate::error::{JsonTError, TransformError};
use crate::model::data::{JsonTArray, JsonTNumber, JsonTRow, JsonTValue};
use crate::model::field::{JsonTField, JsonTFieldKind};
use crate::model::schema::{JsonTSchema, SchemaKind};

const DEK_LEN: usize = 32;

// =============================================================================
// RowWriter — schema-bound, construction-guarded writer (D4)
// =============================================================================

/// Schema-bound stream writer that enforces the crypto contract at construction time.
///
/// If the schema declares any sensitive (`~`) fields, `crypto` **must** be `Some`;
/// construction fails with an error otherwise.
///
/// # Lifecycle
/// ```text
/// // For an encrypted stream:
/// let enc_dek = config.wrap_dek(version, &dek)?;
/// let ctx     = CryptoContext::new(version, enc_dek);
/// let session = config.open_session(&ctx)?;
/// let writer  = RowWriter::new(schema, Some((ctx, session)))?;
/// writer.write_stream(&rows, &mut w)?;
/// ```
pub struct RowWriter {
    schema: JsonTSchema,
    /// Bundled context + session.  Both are needed together: context for the
    /// EncryptHeader row, session for per-field AEAD encryption.
    crypto: Option<(CryptoContext, CipherSession)>,
}

impl RowWriter {
    /// Construct a `RowWriter`.
    ///
    /// Returns `Err` when `schema.has_sensitive_fields()` is `true` but
    /// `crypto` is `None`.
    pub fn new(
        schema: JsonTSchema,
        crypto: Option<(CryptoContext, CipherSession)>,
    ) -> Result<Self, JsonTError> {
        if schema.has_sensitive_fields() && crypto.is_none() {
            return Err(TransformError::DecryptFailed {
                field:  String::new(),
                reason: format!(
                    "schema '{}' has sensitive fields but no CryptoContext/CipherSession \
                     was provided",
                    schema.name
                ),
            }
            .into());
        }
        Ok(Self { schema, crypto })
    }

    /// Write a complete encrypted (or plain) JsonT stream to `w`.
    ///
    /// - If `crypto` is `Some`: emits an `EncryptHeader` row first, then one
    ///   data row per entry with sensitive fields AEAD-encrypted.
    /// - If `crypto` is `None`: writes plain rows with no header.
    pub fn write_stream<W: Write>(&self, rows: &[JsonTRow], w: &mut W) -> io::Result<()> {
        let fields: &[JsonTField] = match &self.schema.kind {
            SchemaKind::Straight { fields } => fields.as_slice(),
            SchemaKind::Derived { .. }      => &[],
        };

        match &self.crypto {
            Some((ctx, session)) => {
                let header = build_encrypt_header_row(ctx);
                write_row(&header, w)?;
                for row in rows {
                    w.write_all(b",\n")?;
                    write_row_with_session(row, fields, session, w)?;
                }
            }
            None => {
                write_rows(rows, w)?;
            }
        }
        Ok(())
    }
}

// =============================================================================
// Public API — schema-free writers
// =============================================================================

/// Write one data row to `w` in compact JsonT format: `{v1,v2,...}`.
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
// Public API — stream-level encrypted writers
// =============================================================================

/// Write a complete encrypted JsonT stream using a `CryptoConfig`.
///
/// 1. Generates a random 256-bit DEK.
/// 2. Wraps the DEK via `crypto.wrap_dek(version, &dek)` → writes `EncryptHeader` row.
/// 3. Opens a `CipherSession` for field-level AEAD encryption.
/// 4. For each data row: sensitive fields are encrypted; non-sensitive written normally.
/// 5. Zeros the DEK before returning.
pub fn write_encrypted_stream<W: Write>(
    rows: &[JsonTRow],
    fields: &[JsonTField],
    crypto: &dyn CryptoConfig,
    version: u16,
    w: &mut W,
) -> io::Result<()> {
    let mut dek = [0u8; DEK_LEN];
    rand::thread_rng().fill_bytes(&mut dek);

    let result = write_encrypted_stream_inner(rows, fields, &dek, crypto, version, w);

    dek.fill(0);
    result
}

fn write_encrypted_stream_inner<W: Write>(
    rows: &[JsonTRow],
    fields: &[JsonTField],
    dek: &[u8],
    crypto: &dyn CryptoConfig,
    version: u16,
    w: &mut W,
) -> io::Result<()> {
    let enc_dek = crypto
        .wrap_dek(version, dek)
        .map_err(|e| io::Error::new(io::ErrorKind::Other, e.to_string()))?;
    let ctx    = CryptoContext::new(version, enc_dek);
    let header = build_encrypt_header_row(&ctx);
    write_row(&header, w)?;

    let session = crypto
        .open_session(&ctx)
        .map_err(|e| io::Error::new(io::ErrorKind::Other, e.to_string()))?;

    for row in rows {
        w.write_all(b",\n")?;
        write_row_with_session(row, fields, &session, w)?;
    }
    Ok(())
}

/// Write one data row using schema-aware encryption via a `CipherSession`.
///
/// - Sensitive fields with a plaintext value: encrypt via `session.encrypt_field`,
///   compute SHA-256(plaintext), assemble per-field payload, base64-encode.
/// - Sensitive fields already holding an `Encrypted` value: re-encode the stored
///   payload bytes as base64 (no crypto call).
/// - Non-sensitive fields: written normally.
pub fn write_row_with_session<W: Write>(
    row: &JsonTRow,
    fields: &[JsonTField],
    session: &CipherSession,
    w: &mut W,
) -> io::Result<()> {
    w.write_all(b"{")?;
    for (i, (v, field)) in row.fields.iter().zip(fields.iter()).enumerate() {
        if i > 0 {
            w.write_all(b",")?;
        }
        let is_sensitive = matches!(&field.kind, JsonTFieldKind::Scalar { sensitive: true, .. });
        if is_sensitive {
            use base64::Engine as _;
            let payload: Vec<u8> = match v {
                JsonTValue::Encrypted(payload_bytes) => payload_bytes.clone(),
                other => {
                    let plaintext = value_to_text(other).into_bytes();
                    let digest    = Sha256::digest(&plaintext).to_vec();
                    let ef        = session
                        .encrypt_field(&plaintext)
                        .map_err(|e| io::Error::new(io::ErrorKind::Other, e.to_string()))?;
                    assemble_field_payload(&ef.iv, &digest, &ef.enc_content)
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

fn value_to_text(v: &JsonTValue) -> String {
    match v {
        JsonTValue::Null        => "null".into(),
        JsonTValue::Unspecified => "_".into(),
        JsonTValue::Bool(b)     => b.to_string(),
        JsonTValue::Str(s)      => s.as_raw_str().to_string(),
        JsonTValue::Enum(e)     => e.clone(),
        JsonTValue::Number(n)   => match n {
            JsonTNumber::I16(v)       => v.to_string(),
            JsonTNumber::I32(v)       => v.to_string(),
            JsonTNumber::I64(v)       => v.to_string(),
            JsonTNumber::U16(v)       => v.to_string(),
            JsonTNumber::U32(v)       => v.to_string(),
            JsonTNumber::U64(v)       => v.to_string(),
            JsonTNumber::D32(v)       => v.to_string(),
            JsonTNumber::D64(v)       => v.to_string(),
            JsonTNumber::D128(v)      => v.to_string(),
            JsonTNumber::Date(v)      => v.to_string(),
            JsonTNumber::Time(v)      => v.to_string(),
            JsonTNumber::DateTime(v)  => v.to_string(),
            JsonTNumber::Timestamp(v) => v.to_string(),
        },
        _ => "<complex>".into(),
    }
}

fn write_value<W: Write>(v: &JsonTValue, w: &mut W) -> io::Result<()> {
    match v {
        JsonTValue::Null               => w.write_all(b"null"),
        JsonTValue::Unspecified        => w.write_all(b"_"),
        JsonTValue::Bool(true)         => w.write_all(b"true"),
        JsonTValue::Bool(false)        => w.write_all(b"false"),
        JsonTValue::Str(js)            => write_quoted_str(js.as_raw_str(), w),
        JsonTValue::Enum(c)            => w.write_all(c.as_bytes()),
        JsonTValue::Number(n)          => write_number(n, w),
        JsonTValue::Object(row)        => write_row(row, w),
        JsonTValue::Array(arr)         => write_array(arr, w),
        JsonTValue::Encrypted(payload) => {
            use base64::Engine as _;
            let b64 = base64::engine::general_purpose::STANDARD.encode(payload);
            write_quoted_str(&b64, w)
        }
    }
}

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
        JsonTNumber::I16(v)       => write!(w, "{}", v),
        JsonTNumber::I32(v)       => write!(w, "{}", v),
        JsonTNumber::I64(v)       => write!(w, "{}", v),
        JsonTNumber::U16(v)       => write!(w, "{}", v),
        JsonTNumber::U32(v)       => write!(w, "{}", v),
        JsonTNumber::U64(v)       => write!(w, "{}", v),
        JsonTNumber::D32(v)       => write!(w, "{}", v),
        JsonTNumber::D64(v)       => write!(w, "{}", v),
        JsonTNumber::D128(v)      => write!(w, "{}", v),
        JsonTNumber::Date(v)      => write!(w, "{}", v),
        JsonTNumber::Time(v)      => write!(w, "{}", v),
        JsonTNumber::DateTime(v)  => write!(w, "{}", v),
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
