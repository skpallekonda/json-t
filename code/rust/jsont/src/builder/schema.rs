// =============================================================================
// builder/schema.rs — JsonTSchemaBuilder
// =============================================================================

use std::collections::HashSet;

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

    /// Append a `Decrypt` operation to a derived schema's pipeline.
    ///
    /// The named fields are decrypted in-place at transform time.
    /// Returns an error if called on a straight schema.
    pub fn decrypt(self, fields: Vec<String>) -> Result<Self, JsonTError> {
        self.operation(SchemaOperation::Decrypt { fields })
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
                // Build-time dataflow check: detect Transform/Filter on a field
                // that appears in a Decrypt operation but before that Decrypt runs.
                check_operation_dataflow(&operations)?;
                SchemaKind::Derived { from, operations }
            }
        };

        Ok(JsonTSchema { name, kind, validation: self.validation, resolved: None })
    }
}

// =============================================================================
// Build-time dataflow analysis
// =============================================================================

/// Validates that Transform or Filter don't use encrypted fields before they're decrypted. 
/// This check is self-contained; for parent schema checks, use validate_with_parent.
fn check_operation_dataflow(ops: &[SchemaOperation]) -> Result<(), JsonTError> {
    use crate::transform::collect_field_refs;

    // Collect all field names that appear in *any* Decrypt op in the list.
    // These are presumed to start as encrypted in the parent schema.
    let known_sensitive: HashSet<String> = ops
        .iter()
        .flat_map(|op| match op {
            SchemaOperation::Decrypt { fields } => fields.clone(),
            _ => vec![],
        })
        .collect();

    if known_sensitive.is_empty() {
        return Ok(()); // No Decrypt ops at all — nothing to check.
    }

    let mut decrypted: HashSet<String> = HashSet::new();

    for op in ops {
        match op {
            SchemaOperation::Decrypt { fields } => {
                for f in fields {
                    decrypted.insert(f.clone());
                }
            }

            SchemaOperation::Transform { target, expr, refs } => {
                // Check expression field references.
                let all_refs: Vec<String> = if refs.is_empty() {
                    collect_field_refs(expr)
                } else {
                    refs.clone()
                };
                for r in &all_refs {
                    if known_sensitive.contains(r) && !decrypted.contains(r) {
                        return Err(BuildError::InvalidField {
                            field: r.clone(),
                            reason: format!(
                                "field '{}' is encrypted; add decrypt({}) before this transform",
                                r, r
                            ),
                        }
                        .into());
                    }
                }
                // Also check the target field itself.
                let tgt = target.join();
                if known_sensitive.contains(&tgt) && !decrypted.contains(&tgt) {
                    return Err(BuildError::InvalidField {
                        field: tgt.clone(),
                        reason: format!(
                            "field '{}' is encrypted; add decrypt({}) before this transform",
                            tgt, tgt
                        ),
                    }
                    .into());
                }
            }

            SchemaOperation::Filter { expr, refs } => {
                let all_refs: Vec<String> = if refs.is_empty() {
                    collect_field_refs(expr)
                } else {
                    refs.clone()
                };
                for r in &all_refs {
                    if known_sensitive.contains(r) && !decrypted.contains(r) {
                        return Err(BuildError::InvalidField {
                            field: r.clone(),
                            reason: format!(
                                "field '{}' is encrypted; add decrypt({}) before this filter",
                                r, r
                            ),
                        }
                        .into());
                    }
                }
            }

            // Rename/Exclude/Project operate on field identity, not values — OK on encrypted.
            _ => {}
        }
    }

    Ok(())
}

impl JsonTSchema {
    /// Checks the transformation pipeline against a parent schema. It ensures fields exist, 
    /// renames are valid, and encryption states are handled properly.
    pub fn validate_with_parent(&self, parent: &JsonTSchema) -> Result<(), JsonTError> {
        use crate::model::field::JsonTFieldKind;
        use crate::transform::collect_field_refs;

        let SchemaKind::Derived { operations, .. } = &self.kind else {
            return Ok(()); // straight schemas have no operations to validate
        };

        let parent_fields = match &parent.kind {
            SchemaKind::Straight { fields } => fields,
            SchemaKind::Derived { .. } => return Ok(()), // nested derived: skip for now
        };

        // ── Initial field state from parent ────────────────────────────────
        // Each entry: (name, is_encrypted).
        // Fields marked sensitive (~) start encrypted; all others start plain.
        let mut scope: Vec<(String, bool)> = parent_fields
            .iter()
            .map(|f| {
                let encrypted =
                    matches!(&f.kind, JsonTFieldKind::Scalar { sensitive: true, .. });
                (f.name.clone(), encrypted)
            })
            .collect();

        // ── Helper closures ────────────────────────────────────────────────
        let find = |scope: &Vec<(String, bool)>, name: &str| -> Option<bool> {
            scope.iter().find(|(n, _)| n == name).map(|(_, enc)| *enc)
        };

        // ── Walk operations, updating scope at each step ───────────────────
        for op in operations {
            match op {
                // ── Decrypt ───────────────────────────────────────────────
                SchemaOperation::Decrypt { fields } => {
                    for fname in fields {
                        match find(&scope, fname) {
                            None => {
                                return Err(BuildError::InvalidField {
                                    field: fname.clone(),
                                    reason: format!(
                                        "decrypt references field '{}' which is not in scope at this point",
                                        fname
                                    ),
                                }.into());
                            }
                            Some(false) => {
                                return Err(BuildError::InvalidField {
                                    field: fname.clone(),
                                    reason: format!(
                                        "decrypt references field '{}' which is not marked sensitive (~) in parent '{}'",
                                        fname, parent.name
                                    ),
                                }.into());
                            }
                            Some(true) => {
                                // Mark as decrypted in scope.
                                if let Some(entry) = scope.iter_mut().find(|(n, _)| n == fname) {
                                    entry.1 = false;
                                }
                            }
                        }
                    }
                }

                // ── Project ───────────────────────────────────────────────
                SchemaOperation::Project(paths) => {
                    for path in paths.iter() {
                        let name = path.join();
                        if name.contains('.') { continue; } // nested: skip
                        if find(&scope, &name).is_none() {
                            return Err(BuildError::InvalidField {
                                field: name.clone(),
                                reason: format!(
                                    "project references field '{}' which is not in scope at this point",
                                    name
                                ),
                            }.into());
                        }
                    }
                    let keep: HashSet<String> = paths.iter()
                        .map(|p| p.join())
                        .filter(|n| !n.contains('.'))
                        .collect();
                    scope.retain(|(n, _)| keep.contains(n));
                }

                // ── Exclude ───────────────────────────────────────────────
                SchemaOperation::Exclude(paths) => {
                    for path in paths.iter() {
                        let name = path.join();
                        if name.contains('.') { continue; }
                        if find(&scope, &name).is_none() {
                            return Err(BuildError::InvalidField {
                                field: name.clone(),
                                reason: format!(
                                    "exclude references field '{}' which is not in scope at this point",
                                    name
                                ),
                            }.into());
                        }
                        scope.retain(|(n, _)| n != &name);
                    }
                }

                // ── Rename ────────────────────────────────────────────────
                SchemaOperation::Rename(pairs) => {
                    for pair in pairs.iter() {
                        let from = pair.from.join();
                        if from.contains('.') { continue; }
                        let to = pair.to.clone();
                        match find(&scope, &from) {
                            None => {
                                return Err(BuildError::InvalidField {
                                    field: from.clone(),
                                    reason: format!(
                                        "rename references field '{}' which is not in scope at this point",
                                        from
                                    ),
                                }.into());
                            }
                            Some(encrypted) => {
                                if find(&scope, &to).is_some() {
                                    return Err(BuildError::InvalidField {
                                        field: to.clone(),
                                        reason: format!(
                                            "rename to '{}' conflicts with an existing field in scope",
                                            to
                                        ),
                                    }.into());
                                }
                                // Replace old name with new name, preserving encrypted state.
                                if let Some(entry) = scope.iter_mut().find(|(n, _)| n == &from) {
                                    entry.0 = to;
                                    entry.1 = encrypted;
                                }
                            }
                        }
                    }
                }

                // ── Transform ─────────────────────────────────────────────
                SchemaOperation::Transform { target, expr, refs } => {
                    let all_refs: Vec<String> = if refs.is_empty() {
                        collect_field_refs(expr)
                    } else {
                        refs.clone()
                    };
                    for r in &all_refs {
                        match find(&scope, r) {
                            None => {
                                return Err(BuildError::InvalidField {
                                    field: r.clone(),
                                    reason: format!(
                                        "transform expression references field '{}' which is not in scope at this point",
                                        r
                                    ),
                                }.into());
                            }
                            Some(true) => {
                                return Err(BuildError::InvalidField {
                                    field: r.clone(),
                                    reason: format!(
                                        "transform expression references encrypted field '{}'; add decrypt({}) first",
                                        r, r
                                    ),
                                }.into());
                            }
                            Some(false) => {}
                        }
                    }
                    let tgt = target.join();
                    if !tgt.contains('.') {
                        match find(&scope, &tgt) {
                            None => {
                                return Err(BuildError::InvalidField {
                                    field: tgt.clone(),
                                    reason: format!(
                                        "transform target '{}' is not in scope at this point",
                                        tgt
                                    ),
                                }.into());
                            }
                            Some(true) => {
                                return Err(BuildError::InvalidField {
                                    field: tgt.clone(),
                                    reason: format!(
                                        "transform target '{}' is encrypted; add decrypt({}) first",
                                        tgt, tgt
                                    ),
                                }.into());
                            }
                            Some(false) => {}
                        }
                    }
                }

                // ── Filter ────────────────────────────────────────────────
                SchemaOperation::Filter { expr, refs } => {
                    let all_refs: Vec<String> = if refs.is_empty() {
                        collect_field_refs(expr)
                    } else {
                        refs.clone()
                    };
                    for r in &all_refs {
                        match find(&scope, r) {
                            None => {
                                return Err(BuildError::InvalidField {
                                    field: r.clone(),
                                    reason: format!(
                                        "filter expression references field '{}' which is not in scope at this point",
                                        r
                                    ),
                                }.into());
                            }
                            Some(true) => {
                                return Err(BuildError::InvalidField {
                                    field: r.clone(),
                                    reason: format!(
                                        "filter expression references encrypted field '{}'; add decrypt({}) first",
                                        r, r
                                    ),
                                }.into());
                            }
                            Some(false) => {}
                        }
                    }
                }
            }
        }

        Ok(())
    }
}
