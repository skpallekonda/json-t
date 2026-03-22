// =============================================================================
// builder/infer.rs — SchemaInferrer
// =============================================================================
// Infers a JsonTSchema from a sample of JsonTRows.
// The result is a best-effort straight schema:
//   - Field names default to field_0, field_1, ... unless hints are supplied.
//   - Types are inferred by sampling; conflicts fall back to Str.
//   - Fields with any Null in the sample are marked optional.
//   - No constraints or validation blocks are generated.
// =============================================================================

use crate::error::{BuildError, JsonTError};
use crate::model::schema::{JsonTSchema, SchemaKind};
use crate::model::field::{JsonTField, JsonTFieldKind, JsonTFieldType, ScalarType};
use crate::model::data::{JsonTRow, JsonTValue, JsonTNumber};

/// Configuration for schema inference from a set of data rows.
#[derive(Debug, Clone)]
pub struct SchemaInferrer {
    /// How many rows to sample (0 = all rows).
    pub sample_size: usize,

    /// If the fraction of null/unspecified values at a position exceeds this
    /// threshold the field is marked optional.  Range: 0.0 – 1.0.
    pub nullable_threshold: f64,

    /// Name for the inferred schema.
    pub schema_name: String,
}

impl Default for SchemaInferrer {
    fn default() -> Self {
        Self {
            sample_size:        0,
            nullable_threshold: 0.0, // any null → optional
            schema_name:        "Inferred".to_string(),
        }
    }
}

impl SchemaInferrer {
    pub fn new() -> Self {
        Self::default()
    }

    pub fn sample_size(mut self, n: usize) -> Self {
        self.sample_size = n;
        self
    }

    pub fn nullable_threshold(mut self, t: f64) -> Self {
        self.nullable_threshold = t.clamp(0.0, 1.0);
        self
    }

    pub fn schema_name(mut self, name: impl Into<String>) -> Self {
        self.schema_name = name.into();
        self
    }

    // ── Public entry points ───────────────────────────────────────────────

    /// Infer a schema from rows, using auto-generated field names (field_0, field_1, …).
    pub fn infer(&self, rows: &[JsonTRow]) -> Result<JsonTSchema, JsonTError> {
        let width = self.row_width(rows)?;
        let names: Vec<String> = (0..width).map(|i| format!("field_{}", i)).collect();
        self.infer_inner(rows, &names)
    }

    /// Infer a schema from rows, using the supplied field name hints.
    ///
    /// # Errors
    /// [`BuildError::NameHintMismatch`] if the hint count differs from row width.
    pub fn infer_with_names(
        &self,
        rows: &[JsonTRow],
        names: &[&str],
    ) -> Result<JsonTSchema, JsonTError> {
        let width = self.row_width(rows)?;
        if names.len() != width {
            return Err(BuildError::NameHintMismatch {
                hint: names.len(),
                row:  width,
            }.into());
        }
        let owned: Vec<String> = names.iter().map(|s| s.to_string()).collect();
        self.infer_inner(rows, &owned)
    }

    // ── Internal ──────────────────────────────────────────────────────────

    fn sample<'r>(&self, rows: &'r [JsonTRow]) -> &'r [JsonTRow] {
        if self.sample_size == 0 || self.sample_size >= rows.len() {
            rows
        } else {
            &rows[..self.sample_size]
        }
    }

    fn row_width(&self, rows: &[JsonTRow]) -> Result<usize, JsonTError> {
        let sample = self.sample(rows);
        if sample.is_empty() {
            return Ok(0);
        }
        // Use the first row's width as the reference.
        Ok(sample[0].len())
    }

    fn infer_inner(
        &self,
        rows: &[JsonTRow],
        names: &[String],
    ) -> Result<JsonTSchema, JsonTError> {
        let sample = self.sample(rows);
        let width  = names.len();

        let mut fields = Vec::with_capacity(width);

        for col in 0..width {
            let col_values: Vec<&JsonTValue> = sample
                .iter()
                .filter_map(|row| row.get(col))
                .collect();

            let null_count = col_values.iter()
                .filter(|v| matches!(v, JsonTValue::Null | JsonTValue::Unspecified))
                .count();

            let null_fraction = if col_values.is_empty() {
                0.0
            } else {
                null_count as f64 / col_values.len() as f64
            };

            let optional = null_fraction > self.nullable_threshold;

            // Collect non-null values for type inference.
            let non_null: Vec<&JsonTValue> = col_values.iter()
                .copied()
                .filter(|v| !matches!(v, JsonTValue::Null | JsonTValue::Unspecified))
                .collect();

            let scalar_type = infer_type(&non_null);

            fields.push(JsonTField {
                name: names[col].clone(),
                kind: JsonTFieldKind::Scalar {
                    field_type:  JsonTFieldType::simple(scalar_type),
                    optional,
                    default:     None,
                    constant:    None,
                    constraints: Vec::new(),
                },
            });
        }

        Ok(JsonTSchema {
            name:       self.schema_name.clone(),
            kind:       SchemaKind::Straight { fields },
            validation: None,
        })
    }
}

/// Infer the best scalar type for a column of non-null values.
///
/// Strategy (most-specific wins; conflicts fall back):
///   all bool             → Bool
///   all i16              → I16
///   all i16/i32          → I32
///   all i16/i32/i64      → I64
///   any float + all i*   → D64
///   all d32              → D32
///   all d64/mixed float  → D64
///   anything else / mixed → Str
fn infer_type(values: &[&JsonTValue]) -> ScalarType {
    if values.is_empty() {
        return ScalarType::Str; // no information — safest default
    }

    // Fast path: all booleans.
    if values.iter().all(|v| matches!(v, JsonTValue::Bool(_))) {
        return ScalarType::Bool;
    }

    // Fast path: all strings / temporal / binary (stored as Str at data layer).
    if values.iter().all(|v| matches!(v, JsonTValue::Str(_))) {
        return ScalarType::Str;
    }

    // Numeric inference.
    if values.iter().all(|v| matches!(v, JsonTValue::Number(_))) {
        return infer_numeric_type(values);
    }

    // Mixed or unknown — fall back to Str.
    ScalarType::Str
}

fn infer_numeric_type(values: &[&JsonTValue]) -> ScalarType {
    // Classify each number into a "width category".
    #[derive(PartialEq, Eq, PartialOrd, Ord, Clone, Copy)]
    enum NumKind { I16, I32, I64, D32, D64, D128 }

    let kinds: Vec<NumKind> = values.iter().filter_map(|v| {
        if let JsonTValue::Number(n) = v {
            Some(match n {
                JsonTNumber::I16(_)  => NumKind::I16,
                JsonTNumber::I32(_)  => NumKind::I32,
                JsonTNumber::I64(_)  => NumKind::I64,
                JsonTNumber::U16(_)  => NumKind::I32,  // widen unsigned to next signed
                JsonTNumber::U32(_)  => NumKind::I64,
                JsonTNumber::U64(_)  => NumKind::D128, // u64 max > i64 max
                JsonTNumber::D32(_)  => NumKind::D32,
                JsonTNumber::D64(_)  => NumKind::D64,
                JsonTNumber::D128(_) => NumKind::D128,
            })
        } else {
            None
        }
    }).collect();

    // The widest kind seen wins.
    match kinds.iter().max() {
        Some(NumKind::I16)  => ScalarType::I32, // promote to i32 for safety
        Some(NumKind::I32)  => ScalarType::I32,
        Some(NumKind::I64)  => ScalarType::I64,
        Some(NumKind::D32)  => ScalarType::D64, // promote d32 → d64 for safety
        Some(NumKind::D64)  => ScalarType::D64,
        Some(NumKind::D128) => ScalarType::D128,
        None                => ScalarType::Str,
    }
}
