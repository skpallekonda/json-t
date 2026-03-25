// =============================================================================
// validate/rule.rs — Row-level validation rule evaluation
// =============================================================================
//
// Evaluates the `rules` block of a `JsonTValidationBlock` against a single
// parsed row.  Results:
//
//   Fatal   — ConditionalRequirement triggered and a required field is absent.
//   Warning — Plain expression rule evaluated to false, or evaluation failed.
//
// Dataset-level rules (`validation.dataset`) span multiple rows and are
// evaluated separately — not handled here.
// =============================================================================

use crate::{EvalContext, Evaluatable};
use crate::diagnostic::{DiagnosticEvent, EventKind};
use crate::model::data::JsonTValue;
use crate::model::field::JsonTField;
use crate::model::validation::{JsonTExpression, JsonTRule, JsonTValidationBlock};

// =============================================================================
// Public API
// =============================================================================

/// Evaluate all `rules` in a `JsonTValidationBlock` against one parsed row.
///
/// Returns diagnostic events for any violations.
/// `fields` and `row_values` must be the same length (caller responsibility).
pub fn check_rules(
    fields:     &[JsonTField],
    validation: &JsonTValidationBlock,
    row_values: &[JsonTValue],
    row_index:  usize,
) -> Vec<DiagnosticEvent> {
    let mut events = Vec::new();
    let ctx = build_eval_context(fields, row_values);

    for rule in &validation.rules {
        match rule {
            // ── Plain boolean expression ───────────────────────────────────
            JsonTRule::Expression(expr) => {
                match expr.evaluate(&ctx) {
                    Ok(JsonTValue::Bool(true)) => {}

                    Ok(JsonTValue::Bool(false)) => {
                        events.push(
                            DiagnosticEvent::warning(EventKind::RuleViolation {
                                rule:   stringify_expr(expr),
                                reason: "expression evaluated to false".into(),
                            })
                            .at_row(row_index),
                        );
                    }

                    Ok(other) => {
                        events.push(
                            DiagnosticEvent::warning(EventKind::RuleViolation {
                                rule:   stringify_expr(expr),
                                reason: format!("non-boolean result: {}", other.type_name()),
                            })
                            .at_row(row_index),
                        );
                    }

                    Err(e) => {
                        events.push(
                            DiagnosticEvent::warning(EventKind::RuleViolation {
                                rule:   stringify_expr(expr),
                                reason: format!("evaluation error: {}", e),
                            })
                            .at_row(row_index),
                        );
                    }
                }
            }

            // ── Conditional requirement ────────────────────────────────────
            // When `condition` is true, all `required_fields` must be present.
            JsonTRule::ConditionalRequirement {
                condition,
                required_fields,
            } => match condition.evaluate(&ctx) {
                Ok(JsonTValue::Bool(true)) => {
                    let missing: Vec<String> = required_fields
                        .iter()
                        .filter(|fp| {
                            let key = fp.join();
                            matches!(
                                ctx.get(&key),
                                None | Some(JsonTValue::Null) | Some(JsonTValue::Unspecified)
                            )
                        })
                        .map(|fp| fp.join())
                        .collect();

                    if !missing.is_empty() {
                        events.push(
                            DiagnosticEvent::fatal(EventKind::ConditionalRequirementViolation {
                                condition:      stringify_expr(condition),
                                missing_fields: missing,
                            })
                            .at_row(row_index),
                        );
                    }
                }

                // Condition false, null, or non-boolean: requirement not triggered.
                Ok(_) => {}

                Err(e) => {
                    events.push(
                        DiagnosticEvent::warning(EventKind::RuleViolation {
                            rule:   stringify_expr(condition),
                            reason: format!("condition evaluation error: {}", e),
                        })
                        .at_row(row_index),
                    );
                }
            },
        }
    }

    events
}

// =============================================================================
// Private helpers
// =============================================================================

fn build_eval_context(fields: &[JsonTField], values: &[JsonTValue]) -> EvalContext {
    fields
        .iter()
        .zip(values.iter())
        .fold(EvalContext::new(), |ctx, (field, value)| {
            ctx.bind(field.name.clone(), value.clone())
        })
}

/// Produce a compact, human-readable representation of an expression for use
/// in diagnostic messages.
fn stringify_expr(expr: &JsonTExpression) -> String {
    match expr {
        JsonTExpression::Literal(v)  => format!("{:?}", v),
        JsonTExpression::FieldRef(fp) => fp.join(),

        JsonTExpression::FunctionCall { name, args } => {
            let args_str: Vec<_> = args.iter().map(stringify_expr).collect();
            format!("{}({})", name, args_str.join(", "))
        }

        JsonTExpression::UnaryOp { op, operand } => {
            format!("{}{}", op.symbol(), stringify_expr(operand))
        }

        JsonTExpression::BinaryOp { op, left, right } => {
            format!(
                "({} {} {})",
                stringify_expr(left),
                op.symbol(),
                stringify_expr(right)
            )
        }
    }
}
