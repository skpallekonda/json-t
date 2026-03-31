// =============================================================================
// json/reader.rs — JSON → JsonTRow conversion
// =============================================================================
// JsonReader reads standard JSON (NDJSON, array, or single object) and converts
// each JSON object into a positional JsonTRow using a JsonTSchema as the mapping
// guide.
//
// Only straight schemas are supported directly. For derived schemas, resolve
// the field list via a SchemaRegistry first.
// =============================================================================

use std::collections::HashMap;
use std::io::{BufRead, Read};

use crate::error::{JsonTError, ParseError, StringifyError};
use crate::model::data::{JsonTArray, JsonTNumber, JsonTRow, JsonTString, JsonTValue};
use crate::model::field::{JsonTField, JsonTFieldKind, ScalarType};
use crate::model::schema::{JsonTSchema, SchemaKind};
use super::parser::{JsonNode, JsonParser};

// ─────────────────────────────────────────────────────────────────────────────
// Configuration enums
// ─────────────────────────────────────────────────────────────────────────────

/// Controls how JSON input is structured.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Default)]
pub enum JsonInputMode {
    /// One JSON object per line — default, true streaming (O(1) memory).
    #[default]
    Ndjson,
    /// A JSON array of objects: `[{...}, {...}]`.
    Array,
    /// A single JSON object; produces exactly one row.
    Object,
}

/// Policy for JSON keys that have no matching schema field.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Default)]
pub enum UnknownFieldPolicy {
    /// Silently ignore unknown fields — default.
    #[default]
    Skip,
    /// Return an error if an unknown field is encountered.
    Reject,
}

/// Policy for schema fields that are absent from the JSON object.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Default)]
pub enum MissingFieldPolicy {
    /// Use the field's declared default value; fall back to `Null` — default.
    #[default]
    UseDefault,
    /// Return an error if any schema field is missing from the JSON object.
    Reject,
}

// ─────────────────────────────────────────────────────────────────────────────
// Builder
// ─────────────────────────────────────────────────────────────────────────────

/// Builder returned by [`JsonReader::with_schema`].
pub struct JsonReaderBuilder {
    schema: JsonTSchema,
    mode:    JsonInputMode,
    unknown: UnknownFieldPolicy,
    missing: MissingFieldPolicy,
}

impl JsonReaderBuilder {
    /// Set the input mode (NDJSON, Array, or Object). Default: NDJSON.
    pub fn mode(mut self, mode: JsonInputMode) -> Self {
        self.mode = mode;
        self
    }

    /// Set the policy for JSON keys not present in the schema. Default: Skip.
    pub fn unknown_fields(mut self, policy: UnknownFieldPolicy) -> Self {
        self.unknown = policy;
        self
    }

    /// Set the policy for schema fields absent from the JSON object. Default: UseDefault.
    pub fn missing_fields(mut self, policy: MissingFieldPolicy) -> Self {
        self.missing = policy;
        self
    }

    /// Build the [`JsonReader`].
    pub fn build(self) -> JsonReader {
        JsonReader {
            schema:  self.schema,
            mode:    self.mode,
            unknown: self.unknown,
            missing: self.missing,
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Reader
// ─────────────────────────────────────────────────────────────────────────────

/// Converts JSON input into [`JsonTRow`] values guided by a [`JsonTSchema`].
///
/// Obtain a builder via [`JsonReader::with_schema`]:
///
/// ```text
/// let reader = JsonReader::with_schema(schema)
///     .mode(JsonInputMode::Ndjson)
///     .build();
///
/// reader.read(json_str, |row| { /* consume */ })?;
/// ```
///
/// # Schema requirement
///
/// Only **straight** schemas are supported. For derived schemas, resolve the
/// full field list using a [`crate::SchemaRegistry`] before constructing the
/// reader.
pub struct JsonReader {
    schema:  JsonTSchema,
    mode:    JsonInputMode,
    unknown: UnknownFieldPolicy,
    missing: MissingFieldPolicy,
}

impl JsonReader {
    /// Create a builder for a reader bound to the given schema.
    pub fn with_schema(schema: JsonTSchema) -> JsonReaderBuilder {
        JsonReaderBuilder {
            schema,
            mode:    JsonInputMode::default(),
            unknown: UnknownFieldPolicy::default(),
            missing: MissingFieldPolicy::default(),
        }
    }

    /// Parse all rows from a string slice. Returns the number of rows produced.
    pub fn read(
        &self,
        input: &str,
        mut on_row: impl FnMut(JsonTRow),
    ) -> Result<usize, JsonTError> {
        match self.mode {
            JsonInputMode::Ndjson  => self.read_ndjson(input, &mut on_row),
            JsonInputMode::Array   => self.read_array(input, &mut on_row),
            JsonInputMode::Object  => self.read_single(input.trim(), &mut on_row),
        }
    }

    /// Parse rows from a buffered reader.
    ///
    /// NDJSON mode is truly streaming (O(1) memory per row).
    /// Array and Object modes buffer the full input before parsing.
    pub fn read_streaming<R: BufRead>(
        &self,
        mut reader: R,
        mut on_row: impl FnMut(JsonTRow),
    ) -> Result<usize, JsonTError> {
        match self.mode {
            JsonInputMode::Ndjson => {
                let mut count = 0usize;
                let mut line  = String::new();
                loop {
                    line.clear();
                    let n = reader.read_line(&mut line)
                        .map_err(|e| JsonTError::Parse(ParseError::Pest(e.to_string())))?;
                    if n == 0 { break; }
                    let trimmed = line.trim();
                    if trimmed.is_empty() { continue; }
                    let row = self.object_str_to_row(trimmed)?;
                    on_row(row);
                    count += 1;
                }
                Ok(count)
            }
            JsonInputMode::Array | JsonInputMode::Object => {
                let mut buf = String::new();
                reader.read_to_string(&mut buf)
                    .map_err(|e| JsonTError::Parse(ParseError::Pest(e.to_string())))?;
                self.read(&buf, on_row)
            }
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    fn read_ndjson(
        &self,
        input: &str,
        on_row: &mut impl FnMut(JsonTRow),
    ) -> Result<usize, JsonTError> {
        let mut count = 0;
        for line in input.lines() {
            let trimmed = line.trim();
            if trimmed.is_empty() { continue; }
            let row = self.object_str_to_row(trimmed)?;
            on_row(row);
            count += 1;
        }
        Ok(count)
    }

    fn read_array(
        &self,
        input: &str,
        on_row: &mut impl FnMut(JsonTRow),
    ) -> Result<usize, JsonTError> {
        let mut parser = JsonParser::new(input);
        let node = parser.parse_value()
            .map_err(|e| JsonTError::Parse(ParseError::Pest(format!("JSON: {e}"))))?;
        match node {
            JsonNode::Array(items) => {
                let mut count = 0;
                for item in items {
                    let row = self.node_to_row(item)?;
                    on_row(row);
                    count += 1;
                }
                Ok(count)
            }
            _ => Err(JsonTError::Parse(ParseError::Unexpected(
                "expected a JSON array at top level for Array mode".to_string(),
            ))),
        }
    }

    fn read_single(
        &self,
        input: &str,
        on_row: &mut impl FnMut(JsonTRow),
    ) -> Result<usize, JsonTError> {
        let row = self.object_str_to_row(input)?;
        on_row(row);
        Ok(1)
    }

    fn object_str_to_row(&self, input: &str) -> Result<JsonTRow, JsonTError> {
        let mut parser = JsonParser::new(input);
        let node = parser.parse_value()
            .map_err(|e| JsonTError::Parse(ParseError::Pest(format!("JSON: {e}"))))?;
        self.node_to_row(node)
    }

    fn node_to_row(&self, node: JsonNode) -> Result<JsonTRow, JsonTError> {
        let pairs = match node {
            JsonNode::Object(pairs) => pairs,
            _ => return Err(JsonTError::Parse(ParseError::Unexpected(
                "expected a JSON object for row conversion".to_string(),
            ))),
        };

        let fields = match &self.schema.kind {
            SchemaKind::Straight { fields } => fields,
            SchemaKind::Derived { .. } => return Err(JsonTError::Stringify(
                StringifyError::UnresolvedSchemaRef(
                    "JsonReader requires a straight schema; resolve derived schemas first".to_string(),
                ),
            )),
        };

        // Build a temporary key → node map (borrows from pairs).
        let map: HashMap<&str, &JsonNode> = pairs.iter()
            .map(|(k, v)| (k.as_str(), v))
            .collect();

        // Check for unknown fields when policy is Reject.
        if matches!(self.unknown, UnknownFieldPolicy::Reject) {
            for (key, _) in &pairs {
                if !fields.iter().any(|f| f.name == *key) {
                    return Err(JsonTError::Parse(ParseError::Unexpected(format!(
                        "unknown JSON field '{}' (policy: Reject)",
                        key
                    ))));
                }
            }
        }

        // Build positional row.
        let mut values: Vec<JsonTValue> = Vec::with_capacity(fields.len());
        for field in fields {
            match map.get(field.name.as_str()) {
                Some(node) => values.push(node_to_value(node, field)?),
                None => {
                    if matches!(self.missing, MissingFieldPolicy::Reject) {
                        return Err(JsonTError::Parse(ParseError::Unexpected(format!(
                            "missing JSON field '{}' (policy: Reject)",
                            field.name
                        ))));
                    }
                    // UseDefault: take declared default or Null.
                    let default_val = match &field.kind {
                        JsonTFieldKind::Scalar { default, .. } => {
                            default.clone().unwrap_or(JsonTValue::Null)
                        }
                        JsonTFieldKind::Object { .. } => JsonTValue::Null,
                    };
                    values.push(default_val);
                }
            }
        }

        Ok(JsonTRow::new(values))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Node → JsonTValue conversion
// ─────────────────────────────────────────────────────────────────────────────

fn node_to_value(node: &JsonNode, field: &JsonTField) -> Result<JsonTValue, JsonTError> {
    if matches!(node, JsonNode::Null) {
        return Ok(JsonTValue::Null);
    }

    match &field.kind {
        JsonTFieldKind::Scalar { field_type, .. } => {
            coerce_scalar_or_array(node, &field_type.scalar, field_type.is_array, &field.name)
        }
        JsonTFieldKind::Object { is_array, .. } => {
            // Nested object fields require schema resolution; emit Null placeholder.
            if *is_array {
                match node {
                    JsonNode::Array(items) => {
                        let converted: Result<Vec<JsonTValue>, _> = items.iter()
                            .map(|item| {
                                if matches!(item, JsonNode::Null) {
                                    Ok(JsonTValue::Null)
                                } else {
                                    Err(JsonTError::Parse(ParseError::Unexpected(format!(
                                        "field '{}': nested object arrays require a resolved schema; \
                                         pass a flat straight schema or pre-resolve nested schemas",
                                        field.name
                                    ))))
                                }
                            })
                            .collect();
                        Ok(JsonTValue::Array(JsonTArray::new(converted?)))
                    }
                    _ => Err(type_err(&field.name, "array", node)),
                }
            } else {
                Err(JsonTError::Parse(ParseError::Unexpected(format!(
                    "field '{}': nested object fields require schema resolution; \
                     use a flat straight schema with scalar fields",
                    field.name
                ))))
            }
        }
    }
}

fn coerce_scalar_or_array(
    node:     &JsonNode,
    scalar:   &ScalarType,
    is_array: bool,
    fname:    &str,
) -> Result<JsonTValue, JsonTError> {
    if is_array {
        match node {
            JsonNode::Array(items) => {
                let converted: Result<Vec<JsonTValue>, _> = items.iter()
                    .map(|item| {
                        if matches!(item, JsonNode::Null) {
                            Ok(JsonTValue::Null)
                        } else {
                            coerce_scalar(item, scalar, fname)
                        }
                    })
                    .collect();
                Ok(JsonTValue::Array(JsonTArray::new(converted?)))
            }
            _ => Err(type_err(fname, "JSON array", node)),
        }
    } else {
        coerce_scalar(node, scalar, fname)
    }
}

fn coerce_scalar(
    node:   &JsonNode,
    scalar: &ScalarType,
    fname:  &str,
) -> Result<JsonTValue, JsonTError> {
    match scalar {
        // ── Boolean ──────────────────────────────────────────────────────────
        ScalarType::Bool => match node {
            JsonNode::Bool(b) => Ok(JsonTValue::Bool(*b)),
            _ => Err(type_err(fname, "bool", node)),
        },

        // ── Signed integers ───────────────────────────────────────────────────
        ScalarType::I16 => match node {
            JsonNode::Integer(n) => Ok(JsonTValue::Number(JsonTNumber::I16(*n as i16))),
            JsonNode::Float(f)   => Ok(JsonTValue::Number(JsonTNumber::I16(*f as i16))),
            _ => Err(type_err(fname, "i16", node)),
        },
        ScalarType::I32 => match node {
            JsonNode::Integer(n) => Ok(JsonTValue::Number(JsonTNumber::I32(*n as i32))),
            JsonNode::Float(f)   => Ok(JsonTValue::Number(JsonTNumber::I32(*f as i32))),
            _ => Err(type_err(fname, "i32", node)),
        },
        ScalarType::I64 => match node {
            JsonNode::Integer(n) => Ok(JsonTValue::Number(JsonTNumber::I64(*n))),
            JsonNode::Float(f)   => Ok(JsonTValue::Number(JsonTNumber::I64(*f as i64))),
            _ => Err(type_err(fname, "i64", node)),
        },

        // ── Unsigned integers ─────────────────────────────────────────────────
        ScalarType::U16 => match node {
            JsonNode::Integer(n) => Ok(JsonTValue::Number(JsonTNumber::U16(*n as u16))),
            JsonNode::Float(f)   => Ok(JsonTValue::Number(JsonTNumber::U16(*f as u16))),
            _ => Err(type_err(fname, "u16", node)),
        },
        ScalarType::U32 => match node {
            JsonNode::Integer(n) => Ok(JsonTValue::Number(JsonTNumber::U32(*n as u32))),
            JsonNode::Float(f)   => Ok(JsonTValue::Number(JsonTNumber::U32(*f as u32))),
            _ => Err(type_err(fname, "u32", node)),
        },
        ScalarType::U64 => match node {
            JsonNode::Integer(n) => Ok(JsonTValue::Number(JsonTNumber::U64(*n as u64))),
            JsonNode::Float(f)   => Ok(JsonTValue::Number(JsonTNumber::U64(*f as u64))),
            _ => Err(type_err(fname, "u64", node)),
        },

        // ── Decimal ───────────────────────────────────────────────────────────
        ScalarType::D32 => match node {
            JsonNode::Integer(n) => Ok(JsonTValue::Number(JsonTNumber::D32(*n as f32))),
            JsonNode::Float(f)   => Ok(JsonTValue::Number(JsonTNumber::D32(*f as f32))),
            _ => Err(type_err(fname, "d32", node)),
        },
        ScalarType::D64 => match node {
            JsonNode::Integer(n) => Ok(JsonTValue::Number(JsonTNumber::D64(*n as f64))),
            JsonNode::Float(f)   => Ok(JsonTValue::Number(JsonTNumber::D64(*f))),
            _ => Err(type_err(fname, "d64", node)),
        },
        ScalarType::D128 => {
            use rust_decimal::Decimal;
            match node {
                JsonNode::Integer(n) => Ok(JsonTValue::Number(JsonTNumber::D128(Decimal::from(*n)))),
                JsonNode::Float(f) => Decimal::try_from(*f)
                    .map(|d| JsonTValue::Number(JsonTNumber::D128(d)))
                    .map_err(|e| JsonTError::Parse(ParseError::Pest(e.to_string()))),
                JsonNode::Str(s) => s.parse::<Decimal>()
                    .map(|d| JsonTValue::Number(JsonTNumber::D128(d)))
                    .map_err(|e| JsonTError::Parse(ParseError::Pest(e.to_string()))),
                _ => Err(type_err(fname, "d128", node)),
            }
        }

        // ── Temporal integers (integer on wire) ───────────────────────────────
        ScalarType::Date => match node {
            JsonNode::Integer(n) => Ok(JsonTValue::Number(JsonTNumber::Date(*n as u32))),
            _ => Err(type_err(fname, "date (YYYYMMDD integer)", node)),
        },
        ScalarType::Time => match node {
            JsonNode::Integer(n) => Ok(JsonTValue::Number(JsonTNumber::Time(*n as u32))),
            _ => Err(type_err(fname, "time (HHmmss integer)", node)),
        },
        ScalarType::DateTime => match node {
            JsonNode::Integer(n) => Ok(JsonTValue::Number(JsonTNumber::DateTime(*n as u64))),
            _ => Err(type_err(fname, "datetime (YYYYMMDDHHmmss integer)", node)),
        },
        ScalarType::Timestamp => match node {
            JsonNode::Integer(n) => Ok(JsonTValue::Number(JsonTNumber::Timestamp(*n))),
            JsonNode::Float(f)   => Ok(JsonTValue::Number(JsonTNumber::Timestamp(*f as i64))),
            _ => Err(type_err(fname, "timestamp (epoch integer)", node)),
        },

        // ── Plain / semantic strings ──────────────────────────────────────────
        ScalarType::Str => match node {
            JsonNode::Str(s)     => Ok(JsonTValue::Str(JsonTString::Plain(s.clone()))),
            JsonNode::Integer(n) => Ok(JsonTValue::Str(JsonTString::Plain(n.to_string()))),
            JsonNode::Float(f)   => Ok(JsonTValue::Str(JsonTString::Plain(f.to_string()))),
            _ => Err(type_err(fname, "str", node)),
        },
        ScalarType::NStr => match node {
            JsonNode::Str(s) => Ok(JsonTValue::Str(JsonTString::Nstr(s.clone()))),
            _ => Err(type_err(fname, "nstr", node)),
        },
        ScalarType::Uri => match node {
            JsonNode::Str(s) => Ok(JsonTValue::Str(JsonTString::Uri(s.clone()))),
            _ => Err(type_err(fname, "uri", node)),
        },
        ScalarType::Uuid => match node {
            JsonNode::Str(s) => Ok(JsonTValue::Str(JsonTString::Uuid(s.clone()))),
            _ => Err(type_err(fname, "uuid", node)),
        },
        ScalarType::Email => match node {
            JsonNode::Str(s) => Ok(JsonTValue::Str(JsonTString::Email(s.clone()))),
            _ => Err(type_err(fname, "email", node)),
        },
        ScalarType::Hostname => match node {
            JsonNode::Str(s) => Ok(JsonTValue::Str(JsonTString::Hostname(s.clone()))),
            _ => Err(type_err(fname, "hostname", node)),
        },
        ScalarType::Ipv4 => match node {
            JsonNode::Str(s) => Ok(JsonTValue::Str(JsonTString::Ipv4(s.clone()))),
            _ => Err(type_err(fname, "ipv4", node)),
        },
        ScalarType::Ipv6 => match node {
            JsonNode::Str(s) => Ok(JsonTValue::Str(JsonTString::Ipv6(s.clone()))),
            _ => Err(type_err(fname, "ipv6", node)),
        },

        // ── Temporal strings ──────────────────────────────────────────────────
        ScalarType::Tsz => match node {
            JsonNode::Str(s) => Ok(JsonTValue::Str(JsonTString::Tsz(s.clone()))),
            _ => Err(type_err(fname, "tsz", node)),
        },
        ScalarType::Inst => match node {
            JsonNode::Str(s) => Ok(JsonTValue::Str(JsonTString::Inst(s.clone()))),
            _ => Err(type_err(fname, "inst", node)),
        },
        ScalarType::Duration => match node {
            JsonNode::Str(s) => Ok(JsonTValue::Str(JsonTString::Duration(s.clone()))),
            _ => Err(type_err(fname, "duration", node)),
        },

        // ── Binary / encoded strings ──────────────────────────────────────────
        ScalarType::Base64 => match node {
            JsonNode::Str(s) => Ok(JsonTValue::Str(JsonTString::Base64(s.clone()))),
            _ => Err(type_err(fname, "base64", node)),
        },
        ScalarType::Hex => match node {
            JsonNode::Str(s) => Ok(JsonTValue::Str(JsonTString::Hex(s.clone()))),
            _ => Err(type_err(fname, "hex", node)),
        },
        ScalarType::Oid => match node {
            JsonNode::Str(s) => Ok(JsonTValue::Str(JsonTString::Oid(s.clone()))),
            _ => Err(type_err(fname, "oid", node)),
        },
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Error helpers
// ─────────────────────────────────────────────────────────────────────────────

fn node_kind(node: &JsonNode) -> &'static str {
    match node {
        JsonNode::Null       => "null",
        JsonNode::Bool(_)    => "bool",
        JsonNode::Integer(_) => "integer",
        JsonNode::Float(_)   => "float",
        JsonNode::Str(_)     => "string",
        JsonNode::Array(_)   => "array",
        JsonNode::Object(_)  => "object",
    }
}

fn type_err(fname: &str, expected: &str, node: &JsonNode) -> JsonTError {
    JsonTError::Parse(ParseError::Unexpected(format!(
        "field '{}': expected {}, got {}",
        fname, expected, node_kind(node)
    )))
}
