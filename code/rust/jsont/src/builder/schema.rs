// =============================================================================
// builder/schema.rs — JsonTSchemaBuilder
// =============================================================================

use crate::error::{BuildError, JsonTError};
use crate::model::schema::{JsonTSchema, SchemaKind, SchemaOperation};
use crate::model::field::JsonTField;
use crate::model::validation::JsonTValidationBlock;
use crate::builder::field::JsonTFieldBuilder;
use crate::builder::validation::JsonTValidationBlockBuilder;

/// Builder for [`JsonTSchema`].
///
/// Use [`JsonTSchemaBuilder::straight`] for a field-declaring schema, or
/// [`JsonTSchemaBuilder::derived`] for a schema that transforms a parent.
#[derive(Debug)]
pub struct JsonTSchemaBuilder {
    name:       Option<String>,
    kind:       SchemaBuildKind,
    validation: Option<JsonTValidationBlock>,
}

#[derive(Debug)]
enum SchemaBuildKind {
    Straight { fields: Vec<JsonTField> },
    Derived  { from: String, operations: Vec<SchemaOperation> },
}

impl JsonTSchemaBuilder {
    /// Start a straight (field-declaring) schema with the given name.
    pub fn straight(name: impl Into<String>) -> Self {
        Self {
            name: Some(name.into()),
            kind: SchemaBuildKind::Straight { fields: Vec::new() },
            validation: None,
        }
    }

    /// Start a derived schema that transforms `parent_schema`.
    pub fn derived(name: impl Into<String>, from: impl Into<String>) -> Self {
        Self {
            name: Some(name.into()),
            kind: SchemaBuildKind::Derived {
                from: from.into(),
                operations: Vec::new(),
            },
            validation: None,
        }
    }

    // ── Straight schema helpers ───────────────────────────────────────────

    /// Append a fully-built field to a straight schema.
    /// Returns an error if a field with the same name already exists.
    pub fn field(mut self, field: JsonTField) -> Result<Self, JsonTError> {
        match &mut self.kind {
            SchemaBuildKind::Straight { fields } => {
                if fields.iter().any(|f| f.name == field.name) {
                    return Err(BuildError::DuplicateFieldName(field.name).into());
                }
                fields.push(field);
            }
            SchemaBuildKind::Derived { .. } => {
                return Err(BuildError::MissingField(
                    "fields cannot be added to a derived schema".into()
                ).into());
            }
        }
        Ok(self)
    }

    /// Append a field from its builder.
    pub fn field_from(self, builder: JsonTFieldBuilder) -> Result<Self, JsonTError> {
        self.field(builder.build()?)
    }

    // ── Derived schema helpers ────────────────────────────────────────────

    /// Append one operation to a derived schema's pipeline.
    pub fn operation(mut self, op: SchemaOperation) -> Result<Self, JsonTError> {
        match &mut self.kind {
            SchemaBuildKind::Derived { operations, .. } => {
                operations.push(op);
            }
            SchemaBuildKind::Straight { .. } => {
                return Err(BuildError::MissingField(
                    "operations cannot be added to a straight schema".into()
                ).into());
            }
        }
        Ok(self)
    }

    // ── Shared ────────────────────────────────────────────────────────────

    pub fn validation(mut self, block: JsonTValidationBlock) -> Self {
        self.validation = Some(block);
        self
    }

    pub fn validation_from(mut self, builder: JsonTValidationBlockBuilder) -> Result<Self, JsonTError> {
        self.validation = Some(builder.build()?);
        Ok(self)
    }

    pub fn build(self) -> Result<JsonTSchema, JsonTError> {
        let name = self.name
            .ok_or_else(|| BuildError::MissingField("schema name".into()))?;

        let kind = match self.kind {
            SchemaBuildKind::Straight { fields } => SchemaKind::Straight { fields },
            SchemaBuildKind::Derived { from, operations } => {
                if operations.is_empty() {
                    return Err(BuildError::MissingField(
                        "derived schema must have at least one operation".into()
                    ).into());
                }
                SchemaKind::Derived { from, operations }
            }
        };

        Ok(JsonTSchema { name, kind, validation: self.validation })
    }
}
