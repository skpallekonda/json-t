// =============================================================================
// validate/constraint.rs — Field-level constraint checking
// =============================================================================
//
// Each `check_field` call inspects one (field, value) pair and returns
// zero or more `DiagnosticEvent`s:
//
//   Fatal   — Required field absent, or constant field mismatches.
//             The containing row must be rejected.
//   Warning — Soft constraint violated (value/length/regex/array items).
//             The row is still accepted but flagged.
//
// Constant checking is Fatal because the value cannot be trusted for any
// downstream consumer that relies on the constant invariant.
// =============================================================================

use crate::diagnostic::{DiagnosticEvent, EventKind};
use crate::model::constraint::{
    ArrayConstraintBool, ArrayConstraintNbr, ArrayItemsConstraint, JsonTConstraint,
    LengthConstraintKey, ValueConstraintKey,
};
use crate::model::data::JsonTValue;
use crate::model::field::{JsonTField, JsonTFieldKind};

// =============================================================================
// Public API
// =============================================================================

/// Check all field-level constraints for one `(field, value)` pair.
///
/// Returns zero or more diagnostic events.  Callers should inspect each event's
/// severity — Fatal events mean the row must be rejected.
pub fn check_field(
    field:     &JsonTField,
    value:     &JsonTValue,
    row_index: usize,
) -> Vec<DiagnosticEvent> {
    let mut events = Vec::new();

    let (optional, constant, constraints) = match &field.kind {
        JsonTFieldKind::Scalar {
            optional,
            constant,
            constraints,
            ..
        } => (*optional, constant.as_ref(), constraints.as_slice()),

        JsonTFieldKind::Object {
            optional,
            constraints,
            ..
        } => (*optional, None, constraints.as_slice()),

        JsonTFieldKind::AnyOf {
            optional,
            constraints,
            ..
        } => (*optional, None, constraints.as_slice()),
    };

    // Explicit `required = true/false` constraint overrides the `optional` flag.
    let mut is_required = !optional;
    for c in constraints {
        if let JsonTConstraint::Required(r) = c {
            is_required = *r;
        }
    }

    let is_absent = matches!(value, JsonTValue::Null | JsonTValue::Unspecified);

    // ── Required / absent ──────────────────────────────────────────────────
    if is_required && is_absent {
        events.push(
            DiagnosticEvent::fatal(EventKind::RequiredFieldMissing {
                field: field.name.clone(),
            })
            .at_row(row_index),
        );
        return events; // No further checks on an absent required field.
    }

    // Skip remaining checks for absent optional fields.
    if is_absent {
        return events;
    }

    // Encrypted values are opaque until decrypted — skip all value constraints.
    // The required/absent check above still applies (Encrypted counts as present).
    if matches!(value, JsonTValue::Encrypted(_)) {
        return events;
    }

    // ── Constant mismatch (Fatal) ──────────────────────────────────────────
    if let Some(expected) = constant {
        if !values_equal(value, expected) {
            events.push(
                DiagnosticEvent::fatal(EventKind::ConstraintViolation {
                    field:      field.name.clone(),
                    constraint: format!("constant = {}", describe_value(expected)),
                    reason:     format!(
                        "value {} does not match constant {}",
                        describe_value(value),
                        describe_value(expected)
                    ),
                })
                .at_row(row_index),
            );
            return events;
        }
    }

    // ── Per-constraint checks (Warning) ────────────────────────────────────
    for constraint in constraints {
        match constraint {
            JsonTConstraint::Required(_) => {} // already handled above

            JsonTConstraint::Value { key, value: bound } => {
                if let Some(n) = value.as_f64() {
                    let violated = match key {
                        ValueConstraintKey::MinValue => n < *bound,
                        ValueConstraintKey::MaxValue => n > *bound,
                        // Precision constraints require decimal-aware parsing;
                        // the streaming scanner emits all numbers as f64, so skip.
                        ValueConstraintKey::MinPrecision | ValueConstraintKey::MaxPrecision => {
                            false
                        }
                    };
                    if violated {
                        events.push(
                            DiagnosticEvent::warning(EventKind::ConstraintViolation {
                                field:      field.name.clone(),
                                constraint: format!("{} = {}", key.keyword(), bound),
                                reason:     format!(
                                    "value {} violates {} = {}",
                                    n,
                                    key.keyword(),
                                    bound
                                ),
                            })
                            .at_row(row_index),
                        );
                    }
                }
            }

            JsonTConstraint::Length { key, value: bound } => {
                let len = match value {
                    JsonTValue::Str(js) => Some(js.as_raw_str().chars().count() as u64),
                    JsonTValue::Array(a) => Some(a.len() as u64),
                    _                   => None,
                };
                if let Some(l) = len {
                    let violated = match key {
                        LengthConstraintKey::MinLength => l < *bound,
                        LengthConstraintKey::MaxLength => l > *bound,
                    };
                    if violated {
                        events.push(
                            DiagnosticEvent::warning(EventKind::ConstraintViolation {
                                field:      field.name.clone(),
                                constraint: format!("{} = {}", key.keyword(), bound),
                                reason:     format!(
                                    "length {} violates {} = {}",
                                    l,
                                    key.keyword(),
                                    bound
                                ),
                            })
                            .at_row(row_index),
                        );
                    }
                }
            }

            JsonTConstraint::Regex(pattern) => {
                // Apply regex to any string-typed variant — all carry a bare string internally.
                if let Some(s) = value.as_str() {
                    let s = s.to_owned();
                    match regex::Regex::new(pattern) {
                        Ok(re) if !re.is_match(&s) => {
                            events.push(
                                DiagnosticEvent::warning(EventKind::ConstraintViolation {
                                    field:      field.name.clone(),
                                    constraint: format!("regex = {:?}", pattern),
                                    reason:     format!("value {:?} does not match pattern", s),
                                })
                                .at_row(row_index),
                            );
                        }
                        Err(e) => {
                            events.push(
                                DiagnosticEvent::warning(EventKind::ConstraintViolation {
                                    field:      field.name.clone(),
                                    constraint: format!("regex = {:?}", pattern),
                                    reason:     format!("invalid regex pattern: {}", e),
                                })
                                .at_row(row_index),
                            );
                        }
                        _ => {}
                    }
                }
            }

            JsonTConstraint::ArrayItems(arr_constraint) => {
                if let JsonTValue::Array(arr) = value {
                    check_array_items(field, arr, arr_constraint, row_index, &mut events);
                }
            }
        }
    }

    events
}

/// Human-readable value description used in diagnostic messages.
pub fn describe_value(v: &JsonTValue) -> String {
    match v {
        JsonTValue::Null         => "null".into(),
        JsonTValue::Unspecified  => "_".into(),
        JsonTValue::Bool(b)      => b.to_string(),
        JsonTValue::Str(js)      => format!("{:?}", js.as_raw_str()),
        JsonTValue::Enum(e)      => e.clone(),
        JsonTValue::Number(n)    => n.as_f64().to_string(),
        JsonTValue::Object(_)    => "{...}".into(),
        JsonTValue::Array(_)     => "[...]".into(),
        JsonTValue::Encrypted(_) => "<encrypted>".into(),
    }
}

// =============================================================================
// Private helpers
// =============================================================================

fn check_array_items(
    field:          &JsonTField,
    arr:            &crate::model::data::JsonTArray,
    arr_constraint: &ArrayItemsConstraint,
    row_index:      usize,
    events:         &mut Vec<DiagnosticEvent>,
) {
    match arr_constraint {
        ArrayItemsConstraint::Numeric { key, value: bound } => {
            let count = arr.len() as u64;
            let null_count = arr
                .items
                .iter()
                .filter(|v| matches!(v, JsonTValue::Null | JsonTValue::Unspecified))
                .count() as u64;

            let (actual, violated) = match key {
                ArrayConstraintNbr::MinItems     => (count, count < *bound),
                ArrayConstraintNbr::MaxItems     => (count, count > *bound),
                ArrayConstraintNbr::MaxNullItems => (null_count, null_count > *bound),
            };
            if violated {
                events.push(
                    DiagnosticEvent::warning(EventKind::ConstraintViolation {
                        field:      field.name.clone(),
                        constraint: format!("{} = {}", key.keyword(), bound),
                        reason:     format!(
                            "array count {} violates {} = {}",
                            actual,
                            key.keyword(),
                            bound
                        ),
                    })
                    .at_row(row_index),
                );
            }
        }

        ArrayItemsConstraint::Boolean {
            key: ArrayConstraintBool::AllowNullItems,
            value: allow,
        } => {
            if !allow {
                let has_null = arr
                    .items
                    .iter()
                    .any(|v| matches!(v, JsonTValue::Null | JsonTValue::Unspecified));
                if has_null {
                    events.push(
                        DiagnosticEvent::warning(EventKind::ConstraintViolation {
                            field:      field.name.clone(),
                            constraint: "allowNullItems = false".into(),
                            reason:     "array contains null or unspecified items".into(),
                        })
                        .at_row(row_index),
                    );
                }
            }
        }
    }
}

fn values_equal(a: &JsonTValue, b: &JsonTValue) -> bool {
    // Delegate to PartialEq for all variants — the derived impl is exact.
    // Semantic string variants (Email, Uuid, …) only equal themselves, not Str,
    // which is the correct invariant (a promoted constant stays promoted).
    a == b
}
