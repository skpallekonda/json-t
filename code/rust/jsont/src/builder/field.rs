// =============================================================================
// builder/field.rs — JsonTFieldBuilder
// =============================================================================
// Constraint validity is checked at build() time (option B):
// invalid constraints are accumulated and all reported together.
// =============================================================================

use crate::error::{BuildError, JsonTError};
use crate::model::field::{AnyOfVariant, JsonTField, JsonTFieldKind, JsonTFieldType, ScalarType};
use crate::model::constraint::{
    JsonTConstraint, ValueConstraintKey, LengthConstraintKey,
    ArrayConstraintNbr, ArrayConstraintBool, ArrayItemsConstraint,
};
use crate::model::data::JsonTValue;

/// Builder for [`JsonTField`].
///
/// Two entry points:
/// - `JsonTFieldBuilder::scalar(name, type)` — for directly-typed fields
/// - `JsonTFieldBuilder::object(name, schema_ref)` — for schema-referenced fields
#[derive(Debug, Clone)]
pub struct JsonTFieldBuilder {
    name:        String,
    kind:        FieldBuildKind,
    optional:    bool,
    sensitive:   bool,
    constraints: Vec<JsonTConstraint>,
}

#[derive(Debug, Clone)]
enum FieldBuildKind {
    Scalar {
        scalar_type: ScalarType,
        is_array:    bool,
        default:     Option<JsonTValue>,
        constant:    Option<JsonTValue>,
    },
    Object {
        schema_ref: String,
        is_array:   bool,
    },
    AnyOf {
        variants:      Vec<AnyOfVariant>,
        is_array:      bool,
        discriminator: Option<String>,
    },
}

impl JsonTFieldBuilder {
    // ── Entry points ──────────────────────────────────────────────────────

    pub fn scalar(name: impl Into<String>, scalar_type: ScalarType) -> Self {
        Self {
            name: name.into(),
            kind: FieldBuildKind::Scalar {
                scalar_type,
                is_array: false,
                default:  None,
                constant: None,
            },
            optional:    false,
            sensitive:   false,
            constraints: Vec::new(),
        }
    }

    pub fn object(name: impl Into<String>, schema_ref: impl Into<String>) -> Self {
        Self {
            name: name.into(),
            kind: FieldBuildKind::Object {
                schema_ref: schema_ref.into(),
                is_array:   false,
            },
            optional:    false,
            sensitive:   false,
            constraints: Vec::new(),
        }
    }

    /// Create a union field that accepts one of the given variants (first-match-wins).
    pub fn any_of(name: impl Into<String>, variants: Vec<AnyOfVariant>) -> Self {
        Self {
            name: name.into(),
            kind: FieldBuildKind::AnyOf {
                variants,
                is_array:      false,
                discriminator: None,
            },
            optional:    false,
            sensitive:   false,
            constraints: Vec::new(),
        }
    }

    // ── Common modifiers ──────────────────────────────────────────────────

    pub fn optional(mut self) -> Self {
        self.optional = true;
        self
    }

    /// Mark this field as sensitive (encrypted on the wire).
    ///
    /// Only valid on scalar fields. Calling this on an `object` or `anyOf`
    /// field causes [`build`] to return a [`BuildError`].
    pub fn sensitive(mut self) -> Self {
        self.sensitive = true;
        self
    }

    pub fn as_array(mut self) -> Self {
        match &mut self.kind {
            FieldBuildKind::Scalar { is_array, .. } => *is_array = true,
            FieldBuildKind::Object { is_array, .. } => *is_array = true,
            FieldBuildKind::AnyOf  { is_array, .. } => *is_array = true,
        }
        self
    }

    /// Set the discriminator field name for `anyOf` fields with multiple object variants.
    pub fn discriminator(mut self, field: impl Into<String>) -> Self {
        if let FieldBuildKind::AnyOf { discriminator, .. } = &mut self.kind {
            *discriminator = Some(field.into());
        }
        self
    }

    pub fn required(mut self, value: bool) -> Self {
        self.constraints.push(JsonTConstraint::Required(value));
        self
    }

    pub fn array_items_count(mut self, key: ArrayConstraintNbr, value: u64) -> Self {
        self.constraints.push(JsonTConstraint::ArrayItems(
            ArrayItemsConstraint::Numeric { key, value }
        ));
        self
    }

    pub fn allow_null_items(mut self, value: bool) -> Self {
        self.constraints.push(JsonTConstraint::ArrayItems(
            ArrayItemsConstraint::Boolean {
                key: ArrayConstraintBool::AllowNullItems,
                value,
            }
        ));
        self
    }

    pub fn constraint(mut self, c: JsonTConstraint) -> Self {
        self.constraints.push(c);
        self
    }

    // ── Scalar-only modifiers (checked at build() time) ───────────────────

    pub fn default_value(mut self, value: JsonTValue) -> Self {
        if let FieldBuildKind::Scalar { default, .. } = &mut self.kind {
            *default = Some(value);
        }
        // silently ignored on object fields — build() will not error for this
        self
    }

    pub fn constant_value(mut self, value: JsonTValue) -> Self {
        if let FieldBuildKind::Scalar { constant, .. } = &mut self.kind {
            *constant = Some(value);
        }
        self
    }

    pub fn min_value(mut self, v: f64) -> Self {
        self.constraints.push(JsonTConstraint::Value {
            key: ValueConstraintKey::MinValue, value: v,
        });
        self
    }

    pub fn max_value(mut self, v: f64) -> Self {
        self.constraints.push(JsonTConstraint::Value {
            key: ValueConstraintKey::MaxValue, value: v,
        });
        self
    }

    pub fn min_precision(mut self, v: f64) -> Self {
        self.constraints.push(JsonTConstraint::Value {
            key: ValueConstraintKey::MinPrecision, value: v,
        });
        self
    }

    pub fn max_precision(mut self, v: f64) -> Self {
        self.constraints.push(JsonTConstraint::Value {
            key: ValueConstraintKey::MaxPrecision, value: v,
        });
        self
    }

    pub fn min_length(mut self, v: u64) -> Self {
        self.constraints.push(JsonTConstraint::Length {
            key: LengthConstraintKey::MinLength, value: v,
        });
        self
    }

    pub fn max_length(mut self, v: u64) -> Self {
        self.constraints.push(JsonTConstraint::Length {
            key: LengthConstraintKey::MaxLength, value: v,
        });
        self
    }

    pub fn regex(mut self, pattern: impl Into<String>) -> Self {
        self.constraints.push(JsonTConstraint::Regex(pattern.into()));
        self
    }

    // ── Build ─────────────────────────────────────────────────────────────

    /// Consume the builder and validate all accumulated constraints.
    ///
    /// Constraint–type mismatches are collected and reported together
    /// (option B — build-time, not call-site).
    pub fn build(self) -> Result<JsonTField, JsonTError> {
        let mut errors: Vec<BuildError> = Vec::new();

        let kind = match self.kind {
            FieldBuildKind::Scalar { scalar_type, is_array, default, constant } => {
                // Validate each constraint against the scalar type.
                for c in &self.constraints {
                    if let Some(err) = check_scalar_constraint(&self.name, &scalar_type, c) {
                        errors.push(err);
                    }
                }

                if !errors.is_empty() {
                    // Report the first accumulated error (wrap in ParseError).
                    return Err(errors.remove(0).into());
                }

                JsonTFieldKind::Scalar {
                    field_type: JsonTFieldType::new(scalar_type, is_array),
                    optional: self.optional,
                    default,
                    constant,
                    constraints: self.constraints,
                    sensitive: self.sensitive,
                }
            }

            FieldBuildKind::Object { schema_ref, is_array } => {
                if self.sensitive {
                    return Err(BuildError::InvalidField {
                        field: self.name.clone(),
                        reason: "sensitive() is only valid on scalar fields, not object fields".into(),
                    }.into());
                }
                // Only Required and ArrayItems are valid on object fields.
                for c in &self.constraints {
                    match c {
                        JsonTConstraint::Required(_) => {}
                        JsonTConstraint::ArrayItems(_) => {}
                        other => {
                            errors.push(BuildError::InvalidConstraintForType {
                                field: self.name.clone(),
                                field_type: format!("<{}>", schema_ref),
                                constraint: format!("{:?}", other),
                            });
                        }
                    }
                }

                if !errors.is_empty() {
                    return Err(errors.remove(0).into());
                }

                JsonTFieldKind::Object {
                    schema_ref,
                    is_array,
                    optional: self.optional,
                    constraints: self.constraints,
                }
            }

            FieldBuildKind::AnyOf { variants, is_array, discriminator } => {
                if self.sensitive {
                    return Err(BuildError::InvalidField {
                        field: self.name.clone(),
                        reason: "sensitive() is only valid on scalar fields, not anyOf fields".into(),
                    }.into());
                }
                if variants.len() < 2 {
                    return Err(BuildError::InvalidField {
                        field: self.name.clone(),
                        reason: "anyOf requires at least two variants".into(),
                    }.into());
                }
                // Only Required and ArrayItems are valid on anyOf fields.
                for c in &self.constraints {
                    match c {
                        JsonTConstraint::Required(_) => {}
                        JsonTConstraint::ArrayItems(_) => {}
                        other => {
                            errors.push(BuildError::InvalidConstraintForType {
                                field: self.name.clone(),
                                field_type: "anyOf".into(),
                                constraint: format!("{:?}", other),
                            });
                        }
                    }
                }

                if !errors.is_empty() {
                    return Err(errors.remove(0).into());
                }

                JsonTFieldKind::AnyOf {
                    variants,
                    is_array,
                    optional: self.optional,
                    discriminator,
                    constraints: self.constraints,
                }
            }
        };

        Ok(JsonTField { name: self.name, kind })
    }
}

/// Check a single constraint against the scalar type.
/// Returns `Some(BuildError)` if the constraint is invalid for the type.
fn check_scalar_constraint(
    field_name: &str,
    scalar_type: &ScalarType,
    constraint: &JsonTConstraint,
) -> Option<BuildError> {
    match constraint {
        JsonTConstraint::Value { key, .. } => {
            if !scalar_type.supports_value_constraints() {
                Some(BuildError::InvalidConstraintForType {
                    field: field_name.to_string(),
                    field_type: scalar_type.keyword().to_string(),
                    constraint: key.keyword().to_string(),
                })
            } else {
                None
            }
        }
        JsonTConstraint::Length { key, .. } => {
            if !scalar_type.supports_length_constraints() {
                Some(BuildError::InvalidConstraintForType {
                    field: field_name.to_string(),
                    field_type: scalar_type.keyword().to_string(),
                    constraint: key.keyword().to_string(),
                })
            } else {
                None
            }
        }
        JsonTConstraint::Regex(_) => {
            if !scalar_type.supports_regex_constraints() {
                Some(BuildError::InvalidConstraintForType {
                    field: field_name.to_string(),
                    field_type: scalar_type.keyword().to_string(),
                    constraint: "regex".to_string(),
                })
            } else {
                None
            }
        }
        // Required and ArrayItems are always valid on scalar fields.
        JsonTConstraint::Required(_) => None,
        JsonTConstraint::ArrayItems(_) => None,
    }
}
