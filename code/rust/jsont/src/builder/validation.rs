// =============================================================================
// builder/validation.rs — JsonTValidationBlockBuilder
// =============================================================================

use crate::error::{BuildError, JsonTError};
use crate::model::validation::{JsonTValidationBlock, JsonTRule, JsonTExpression};
use crate::model::schema::FieldPath;

/// Builder for [`JsonTValidationBlock`].
///
/// All three sub-blocks (rules, unique, dataset) are optional — an empty
/// validation block is valid (though unusual).
#[derive(Debug, Default)]
pub struct JsonTValidationBlockBuilder {
    rules:   Vec<JsonTRule>,
    unique:  Vec<Vec<FieldPath>>,
    dataset: Vec<JsonTExpression>,
}

impl JsonTValidationBlockBuilder {
    pub fn new() -> Self {
        Self::default()
    }

    // ── Rules ─────────────────────────────────────────────────────────────

    /// Append a plain expression rule.
    pub fn rule_expr(mut self, expr: JsonTExpression) -> Self {
        self.rules.push(JsonTRule::Expression(expr));
        self
    }

    /// Append a conditional requirement rule:
    /// `condition -> required field1, field2, ...`
    pub fn rule_conditional(
        mut self,
        condition: JsonTExpression,
        required_fields: Vec<FieldPath>,
    ) -> Result<Self, JsonTError> {
        if required_fields.is_empty() {
            return Err(BuildError::MissingField(
                "conditional requirement must list at least one required field".into()
            ).into());
        }
        self.rules.push(JsonTRule::ConditionalRequirement {
            condition,
            required_fields,
        });
        Ok(self)
    }

    // ── Unique ────────────────────────────────────────────────────────────

    /// Add a uniqueness constraint over a set of field paths.
    /// e.g. unique { (id), (email), (first_name, last_name) }
    pub fn unique_fields(mut self, field_paths: Vec<FieldPath>) -> Result<Self, JsonTError> {
        if field_paths.is_empty() {
            return Err(BuildError::MissingField(
                "unique entry must contain at least one field path".into()
            ).into());
        }
        self.unique.push(field_paths);
        Ok(self)
    }

    // ── Dataset ───────────────────────────────────────────────────────────

    /// Append a dataset-level expression (evaluated across the whole dataset).
    pub fn dataset_expr(mut self, expr: JsonTExpression) -> Self {
        self.dataset.push(expr);
        self
    }

    pub fn build(self) -> Result<JsonTValidationBlock, JsonTError> {
        Ok(JsonTValidationBlock {
            rules:   self.rules,
            unique:  self.unique,
            dataset: self.dataset,
        })
    }
}
