// =============================================================================
// json/writer.rs — JsonTRow → JSON conversion
// =============================================================================
// JsonWriter serialises positional JsonTRow values back to standard JSON,
// using the schema's field names as JSON object keys.
//
// Output modes: NDJSON (one object per line) or Array (wrapped in [ ]).
// Only straight schemas are supported directly.
// =============================================================================

use std::io::{self, Write};

use crate::error::{JsonTError, StringifyError};
use crate::model::data::{JsonTNumber, JsonTRow, JsonTString, JsonTValue};
use crate::model::field::JsonTField;
use crate::model::schema::{JsonTSchema, SchemaKind};

// ─────────────────────────────────────────────────────────────────────────────
// Configuration enum
// ─────────────────────────────────────────────────────────────────────────────

/// Controls how JSON output is structured.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Default)]
pub enum JsonOutputMode {
    /// One JSON object per line — default, true streaming.
    #[default]
    Ndjson,
    /// Wrap all rows in a JSON array: `[{...}, {...}]`.
    Array,
}

// ─────────────────────────────────────────────────────────────────────────────
// Builder
// ─────────────────────────────────────────────────────────────────────────────

/// Builder returned by [`JsonWriter::with_schema`].
pub struct JsonWriterBuilder {
    schema: JsonTSchema,
    mode:   JsonOutputMode,
    pretty: bool,
}

impl JsonWriterBuilder {
    /// Set the output mode (NDJSON or Array). Default: NDJSON.
    pub fn mode(mut self, mode: JsonOutputMode) -> Self {
        self.mode = mode;
        self
    }

    /// Enable pretty-printed output (indented). Default: compact.
    pub fn pretty(mut self, pretty: bool) -> Self {
        self.pretty = pretty;
        self
    }

    /// Build the [`JsonWriter`].
    pub fn build(self) -> JsonWriter {
        JsonWriter { schema: self.schema, mode: self.mode, pretty: self.pretty }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Writer
// ─────────────────────────────────────────────────────────────────────────────

/// Serialises [`JsonTRow`] values to JSON, guided by a [`JsonTSchema`].
///
/// Field names come from the schema; values come from the row by position.
///
/// ```text
/// let writer = JsonWriter::with_schema(schema)
///     .mode(JsonOutputMode::Ndjson)
///     .build();
///
/// let mut out = Vec::new();
/// writer.write_rows(&rows, &mut out)?;
/// ```
///
/// # Schema requirement
///
/// Only **straight** schemas are supported. For derived schemas, resolve the
/// full field list first.
pub struct JsonWriter {
    schema: JsonTSchema,
    mode:   JsonOutputMode,
    pretty: bool,
}

impl JsonWriter {
    /// Create a builder for a writer bound to the given schema.
    pub fn with_schema(schema: JsonTSchema) -> JsonWriterBuilder {
        JsonWriterBuilder { schema, mode: JsonOutputMode::default(), pretty: false }
    }

    /// Write a single row as a JSON object to `w`.
    pub fn write_row<W: Write>(
        &self,
        row: &JsonTRow,
        w: &mut W,
    ) -> Result<(), JsonTError> {
        let fields = self.resolved_fields()?;
        write_json_object(row, fields, self.pretty, 0, w)
            .map_err(|e| JsonTError::Stringify(StringifyError::UnstringifiableValue(e.to_string())))
    }

    /// Write multiple rows to `w`. In NDJSON mode rows are newline-separated;
    /// in Array mode they are wrapped in `[...]`.
    pub fn write_rows<W: Write>(
        &self,
        rows: &[JsonTRow],
        w: &mut W,
    ) -> Result<(), JsonTError> {
        self.write_streaming(rows.iter().cloned(), w)
    }

    /// Write rows from any iterator. Streaming-safe for NDJSON mode.
    pub fn write_streaming<I, W>(
        &self,
        iter: I,
        w: &mut W,
    ) -> Result<(), JsonTError>
    where
        I: Iterator<Item = JsonTRow>,
        W: Write,
    {
        let fields = self.resolved_fields()?;
        let result = match self.mode {
            JsonOutputMode::Ndjson => write_ndjson(iter, fields, self.pretty, w),
            JsonOutputMode::Array  => write_array(iter, fields, self.pretty, w),
        };
        result.map_err(|e| JsonTError::Stringify(StringifyError::UnstringifiableValue(e.to_string())))
    }

    // ── Private ───────────────────────────────────────────────────────────────

    fn resolved_fields(&self) -> Result<&[JsonTField], JsonTError> {
        match &self.schema.kind {
            SchemaKind::Straight { fields } => Ok(fields),
            SchemaKind::Derived { .. } => Err(JsonTError::Stringify(
                StringifyError::UnresolvedSchemaRef(
                    "JsonWriter requires a straight schema; resolve derived schemas first".to_string(),
                ),
            )),
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Output helpers
// ─────────────────────────────────────────────────────────────────────────────

fn write_ndjson<I, W>(
    iter:   I,
    fields: &[JsonTField],
    pretty: bool,
    w:      &mut W,
) -> io::Result<()>
where
    I: Iterator<Item = JsonTRow>,
    W: Write,
{
    let mut first = true;
    for row in iter {
        if !first { w.write_all(b"\n")?; }
        first = false;
        write_json_object(&row, fields, pretty, 0, w)?;
    }
    Ok(())
}

fn write_array<I, W>(
    iter:   I,
    fields: &[JsonTField],
    pretty: bool,
    w:      &mut W,
) -> io::Result<()>
where
    I: Iterator<Item = JsonTRow>,
    W: Write,
{
    w.write_all(b"[")?;
    if pretty { w.write_all(b"\n")?; }
    let mut first = true;
    for row in iter {
        if !first {
            w.write_all(b",")?;
            if pretty { w.write_all(b"\n")?; }
        }
        first = false;
        if pretty { w.write_all(b"  ")?; }
        write_json_object(&row, fields, pretty, 1, w)?;
    }
    if pretty { w.write_all(b"\n")?; }
    w.write_all(b"]")
}

fn write_json_object<W: Write>(
    row:    &JsonTRow,
    fields: &[JsonTField],
    pretty: bool,
    depth:  usize,
    w:      &mut W,
) -> io::Result<()> {
    let inner = if pretty { "  ".repeat(depth + 1) } else { String::new() };
    let close = if pretty { "  ".repeat(depth) }     else { String::new() };
    let sep   = if pretty { ": " }                    else { ":" };
    let nl    = if pretty { "\n" }                    else { "" };
    let comma = if pretty { ",\n" }                   else { "," };

    w.write_all(b"{")?;
    w.write_all(nl.as_bytes())?;

    let count = fields.len().min(row.fields.len());
    for i in 0..count {
        if i > 0 { w.write_all(comma.as_bytes())?; }
        w.write_all(inner.as_bytes())?;
        write_json_string_lit(&fields[i].name, w)?;
        w.write_all(sep.as_bytes())?;
        write_json_value(&row.fields[i], pretty, depth + 1, w)?;
    }

    w.write_all(nl.as_bytes())?;
    w.write_all(close.as_bytes())?;
    w.write_all(b"}")
}

fn write_json_value<W: Write>(
    value:  &JsonTValue,
    pretty: bool,
    depth:  usize,
    w:      &mut W,
) -> io::Result<()> {
    match value {
        JsonTValue::Null | JsonTValue::Unspecified => w.write_all(b"null"),
        JsonTValue::Bool(b)   => w.write_all(if *b { b"true" } else { b"false" }),
        JsonTValue::Number(n) => write_json_number(n, w),
        JsonTValue::Str(s)    => write_json_string_lit(s.as_raw_str(), w),
        JsonTValue::Enum(s)   => write_json_string_lit(s, w),
        JsonTValue::Object(row) => {
            // Nested object — write as a JSON object with positional keys "_0", "_1", ...
            // (field names are unavailable without the nested schema).
            w.write_all(b"{")?;
            for (i, v) in row.fields.iter().enumerate() {
                if i > 0 { w.write_all(b",")?; }
                let key = format!("\"_{i}\":");
                w.write_all(key.as_bytes())?;
                write_json_value(v, pretty, depth, w)?;
            }
            w.write_all(b"}")
        }
        JsonTValue::Array(arr) => write_json_array(&arr.items, pretty, depth, w),
        // Encrypted values cannot be serialised to JSON without first decrypting.
        // Emit as JSON null to avoid leaking ciphertext into JSON output.
        JsonTValue::Encrypted(_) => w.write_all(b"null"),
    }
}

fn write_json_number<W: Write>(n: &JsonTNumber, w: &mut W) -> io::Result<()> {
    match n {
        JsonTNumber::I16(v)       => write!(w, "{v}"),
        JsonTNumber::I32(v)       => write!(w, "{v}"),
        JsonTNumber::I64(v)       => write!(w, "{v}"),
        JsonTNumber::U16(v)       => write!(w, "{v}"),
        JsonTNumber::U32(v)       => write!(w, "{v}"),
        JsonTNumber::U64(v)       => write!(w, "{v}"),
        JsonTNumber::D32(v)       => write!(w, "{v}"),
        JsonTNumber::D64(v)       => write!(w, "{v}"),
        JsonTNumber::D128(v)      => write!(w, "{}", v.to_string()),
        JsonTNumber::Date(v)      => write!(w, "{v}"),
        JsonTNumber::Time(v)      => write!(w, "{v}"),
        JsonTNumber::DateTime(v)  => write!(w, "{v}"),
        JsonTNumber::Timestamp(v) => write!(w, "{v}"),
    }
}

fn write_json_string_lit<W: Write>(s: &str, w: &mut W) -> io::Result<()> {
    w.write_all(b"\"")?;
    for ch in s.chars() {
        match ch {
            '"'  => w.write_all(b"\\\"")?,
            '\\' => w.write_all(b"\\\\")?,
            '\n' => w.write_all(b"\\n")?,
            '\r' => w.write_all(b"\\r")?,
            '\t' => w.write_all(b"\\t")?,
            c if (c as u32) < 0x20 => write!(w, "\\u{:04x}", c as u32)?,
            c => {
                let mut buf = [0u8; 4];
                w.write_all(c.encode_utf8(&mut buf).as_bytes())?;
            }
        }
    }
    w.write_all(b"\"")
}

fn write_json_array<W: Write>(
    items:  &[JsonTValue],
    pretty: bool,
    depth:  usize,
    w:      &mut W,
) -> io::Result<()> {
    w.write_all(b"[")?;
    for (i, item) in items.iter().enumerate() {
        if i > 0 { w.write_all(b",")?; }
        write_json_value(item, pretty, depth, w)?;
    }
    w.write_all(b"]")
}
