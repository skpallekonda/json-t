// =============================================================================
// builder/data.rs — JsonTRowBuilder, JsonTArrayBuilder
// =============================================================================

use crate::error::{BuildError, JsonTError};
use crate::model::data::{JsonTValue, JsonTRow, JsonTArray};
use crate::model::schema::JsonTSchema;
use crate::model::field::JsonTFieldKind;

// =============================================================================
// JsonTRowBuilder
// =============================================================================

/// Builder for [`JsonTRow`].
///
/// Two modes:
/// - **Untyped** (`JsonTRowBuilder::new()`) — infallible `.push()`, no
///   schema validation. Fast path for trusted data.
/// - **Schema-aware** (`JsonTRowBuilder::with_schema(&schema)`) — each
///   `.push_checked()` validates type compatibility against the schema field
///   at that position.
#[derive(Debug)]
pub struct JsonTRowBuilder<'s> {
    values: Vec<JsonTValue>,
    schema: Option<&'s JsonTSchema>,
}

impl JsonTRowBuilder<'static> {
    /// Create an untyped builder — no schema validation.
    pub fn new() -> Self {
        Self { values: Vec::new(), schema: None }
    }

    /// Append a value without any type checking.
    pub fn push(mut self, value: JsonTValue) -> Self {
        self.values.push(value);
        self
    }

    /// Build the row — always succeeds in untyped mode.
    pub fn build(self) -> JsonTRow {
        JsonTRow::new(self.values)
    }
}

impl<'s> JsonTRowBuilder<'s> {
    /// Create a schema-aware builder.
    pub fn with_schema(schema: &'s JsonTSchema) -> Self {
        Self { values: Vec::new(), schema: Some(schema) }
    }

    /// Append a value, validating it against the schema field at the current
    /// position. Returns `Err` on type mismatch or if too many values are pushed.
    pub fn push_checked(mut self, value: JsonTValue) -> Result<Self, JsonTError> {
        if let Some(schema) = self.schema {
            let fields = schema_fields(schema);

            let idx = self.values.len();

            if idx >= fields.len() {
                return Err(BuildError::TooManyValues {
                    schema_fields: fields.len(),
                    pushed: idx + 1,
                }.into());
            }

            let field = &fields[idx];
            validate_value_for_field(&field.name, &field.kind, &value)?;
        }
        self.values.push(value);
        Ok(self)
    }

    /// Build the row — validates arity if schema-aware.
    pub fn build_checked(self) -> Result<JsonTRow, JsonTError> {
        if let Some(schema) = self.schema {
            let expected = schema_fields(schema).len();
            let got = self.values.len();
            if got < expected {
                return Err(BuildError::MissingField(
                    format!("row has {got} values but schema '{name}' expects {expected}",
                        name = schema.name)
                ).into());
            }
        }
        Ok(JsonTRow::new(self.values))
    }
}

/// Extract the fields list from a straight schema.
/// Derived schemas have no direct field list — returns empty slice.
fn schema_fields(schema: &JsonTSchema) -> &[crate::model::field::JsonTField] {
    match &schema.kind {
        crate::model::schema::SchemaKind::Straight { fields } => fields,
        crate::model::schema::SchemaKind::Derived { .. } => &[],
    }
}

/// Lightweight type compatibility check — coarse-grained, not exhaustive.
/// Reports the field name in errors so callers can identify the problem.
fn validate_value_for_field(
    field_name: &str,
    kind: &JsonTFieldKind,
    value: &JsonTValue,
) -> Result<(), JsonTError> {
    use crate::model::data::JsonTValue::*;
    use crate::model::field::ScalarType;

    // Null is always acceptable for optional fields; we don't have the optional
    // flag here without full schema context, so we allow Null universally at
    // this layer and leave strict null-checking to a separate validation pass.
    if matches!(value, Null | Unspecified) {
        return Ok(());
    }

    match kind {
        JsonTFieldKind::Scalar { field_type, .. } => {
            let ok = match (&field_type.scalar, value) {
                (ScalarType::Bool, Bool(_))          => true,
                (ScalarType::Str | ScalarType::NStr
                 | ScalarType::Uri | ScalarType::Uuid
                 | ScalarType::Email | ScalarType::Hostname
                 | ScalarType::Ipv4 | ScalarType::Ipv6
                 | ScalarType::Base64 | ScalarType::Hex
                 | ScalarType::Oid
                 | ScalarType::Date | ScalarType::Time
                 | ScalarType::DateTime | ScalarType::Timestamp
                 | ScalarType::Tsz | ScalarType::Duration
                 | ScalarType::Inst,
                 Str(_))                             => true,
                (ScalarType::I16, Number(n))         => matches!(n, crate::model::data::JsonTNumber::I16(_)),
                (ScalarType::I32, Number(n))         => matches!(n, crate::model::data::JsonTNumber::I32(_)),
                (ScalarType::I64, Number(n))         => matches!(n, crate::model::data::JsonTNumber::I64(_)),
                (ScalarType::U16, Number(n))         => matches!(n, crate::model::data::JsonTNumber::U16(_)),
                (ScalarType::U32, Number(n))         => matches!(n, crate::model::data::JsonTNumber::U32(_)),
                (ScalarType::U64, Number(n))         => matches!(n, crate::model::data::JsonTNumber::U64(_)),
                (ScalarType::D32, Number(n))         => matches!(n, crate::model::data::JsonTNumber::D32(_)),
                (ScalarType::D64, Number(n))         => matches!(n, crate::model::data::JsonTNumber::D64(_)),
                (ScalarType::D128, Number(n))        => matches!(n, crate::model::data::JsonTNumber::D128(_)),
                _                                    => false,
            };

            if !ok {
                return Err(BuildError::RowTypeMismatch {
                    field: field_name.to_string(),
                    expected: field_type.scalar.keyword().to_string(),
                    actual: value.type_name().to_string(),
                }.into());
            }
        }

        JsonTFieldKind::Object { .. } => {
            if !matches!(value, Object(_)) {
                return Err(BuildError::RowTypeMismatch {
                    field: field_name.to_string(),
                    expected: "object".to_string(),
                    actual: value.type_name().to_string(),
                }.into());
            }
        }
    }

    Ok(())
}

// =============================================================================
// JsonTArrayBuilder
// =============================================================================

/// Builder for [`JsonTArray`].
///
/// Always infallible — arrays in JsonT are heterogeneous at the data layer.
#[derive(Debug, Default)]
pub struct JsonTArrayBuilder {
    items: Vec<JsonTValue>,
}

impl JsonTArrayBuilder {
    pub fn new() -> Self {
        Self::default()
    }

    pub fn push(mut self, value: JsonTValue) -> Self {
        self.items.push(value);
        self
    }

    /// Build — always succeeds.
    pub fn build(self) -> JsonTArray {
        JsonTArray::new(self.items)
    }
}
