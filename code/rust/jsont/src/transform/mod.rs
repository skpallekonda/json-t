// =============================================================================
// transform/mod.rs — RowTransformer + schema-level static validation
// =============================================================================
//
// # RowTransformer
//   Implements the RowTransformer trait on JsonTSchema.
//
//   Straight schema: row returned as-is (no transformation needed).
//   Derived schema:  parent schema's output field names are resolved via the
//                    registry, zipped with the input row values to form a named
//                    working state, and each SchemaOperation is applied in order.
//
// # validate_schema (static analysis)
//   A single entry point for all schema-level consistency checks, covering
//   both straight and derived schema kinds:
//
//   Straight schemas
//   ────────────────
//   1. Object field references  — every `Object { schema_ref }` field must
//      name a schema that exists in the registry.
//   2. Validation rule FieldRefs — every field name referenced inside a
//      `rules { ... }` expression or conditional requirement must be declared
//      in this schema's own field list.
//   3. Unique constraint paths  — every FieldPath in a `unique { ... }` group
//      must be declared in this schema's own field list.
//   4. Dataset expression FieldRefs — same check for `dataset { ... }` exprs.
//
//   Derived schemas
//   ───────────────
//   5. Parent schema exists in the registry.
//   6. No cyclic derivation chain.
//   7. Operation FieldPaths — every path in Rename/Exclude/Project must exist
//      in the evolving available field set at the point the operation runs.
//   8. Expression FieldRefs in Filter and Transform — every FieldRef must be
//      available at that point in the pipeline (not excluded/projected away).
//   9. Validation block on the derived schema (same rules 2-4 above, but
//      checked against the final output field set after all operations).
//
// Call validate_schema once after building your registry, before processing
// any rows.  It catches mistakes early and produces a descriptive error
// that names the specific field, rule index, and operation.
// =============================================================================

use std::collections::HashSet;

use crate::{EvalContext, Evaluatable, RowTransformer, SchemaRegistry};
use crate::error::{EvalError, JsonTError, TransformError};
use crate::model::data::{JsonTRow, JsonTValue};
use crate::model::field::JsonTFieldKind;
use crate::model::schema::{FieldPath, JsonTSchema, SchemaKind, SchemaOperation};
use crate::model::validation::{JsonTExpression, JsonTRule, JsonTValidationBlock};

// =============================================================================
// RowTransformer impl
// =============================================================================

impl RowTransformer for JsonTSchema {
    /// Transform one row according to this schema's derivation pipeline.
    ///
    /// For a straight schema the row is returned unchanged.
    /// For a derived schema:
    ///   - the input row must be in the parent schema's **output** layout
    ///   - each operation is applied in declaration order
    ///   - returns `Err(TransformError::Filtered)` when a filter predicate is
    ///     false — this signals "skip this row" and is not a hard failure
    fn transform(
        &self,
        row: JsonTRow,
        registry: &SchemaRegistry,
    ) -> Result<JsonTRow, JsonTError> {
        match &self.kind {
            SchemaKind::Straight { .. } => Ok(row),

            SchemaKind::Derived { from, operations } => {
                let parent = registry
                    .get(from)
                    .ok_or_else(|| TransformError::UnknownSchema(from.clone()))?;

                let mut chain = vec![self.name.clone()];
                let parent_fields = resolve_effective_fields(parent, registry, &mut chain)?;

                if parent_fields.len() != row.len() {
                    return Err(TransformError::FieldNotFound(format!(
                        "row has {} values but parent schema '{}' has {} output fields",
                        row.len(),
                        from,
                        parent_fields.len()
                    ))
                    .into());
                }

                let mut working: Vec<(String, JsonTValue)> = parent_fields
                    .into_iter()
                    .zip(row.fields)
                    .collect();

                for op in operations {
                    working = apply_operation(op, working)?;
                }

                Ok(JsonTRow::new(working.into_iter().map(|(_, v)| v).collect()))
            }
        }
    }
}

// =============================================================================
// validate_schema — unified schema-level static analysis
// =============================================================================

impl JsonTSchema {
    /// Validate this schema's internal consistency against the registry.
    ///
    /// This is a **static analysis** pass — no row data is required.
    /// Call it once after building your registry, before processing any rows.
    ///
    /// ## Straight schemas
    ///
    /// - Every `Object { schema_ref }` field must name a schema present in
    ///   the registry.
    /// - Every `FieldRef` inside a validation `rules` expression or
    ///   conditional requirement must match a declared field in this schema.
    /// - Every field path in a `unique` constraint group must match a
    ///   declared field.
    /// - Every `FieldRef` in a `dataset` expression must match a declared
    ///   field.
    ///
    /// ## Derived schemas
    ///
    /// - The parent schema named in `FROM` must exist in the registry.
    /// - The derivation chain must be cycle-free.
    /// - Every field path and expression `FieldRef` in each operation must
    ///   refer to a field that is still available at that point in the
    ///   pipeline (not yet excluded or projected away).
    /// - If the derived schema itself carries a `validations` block, the
    ///   same expression checks above are applied against the **output**
    ///   field set (the fields that remain after all operations).
    pub fn validate_schema(&self, registry: &SchemaRegistry) -> Result<(), JsonTError> {
        match &self.kind {
            // ── Straight schema ──────────────────────────────────────────────
            SchemaKind::Straight { fields } => {
                let own_field_names: Vec<String> =
                    fields.iter().map(|f| f.name.clone()).collect();

                // Check 1: object field schema_ref must exist in the registry.
                for field in fields {
                    if let JsonTFieldKind::Object { schema_ref, .. } = &field.kind {
                        if registry.get(schema_ref).is_none() {
                            return Err(TransformError::UnknownSchema(format!(
                                "field '{}' references unknown schema '{}'",
                                field.name, schema_ref
                            ))
                            .into());
                        }
                    }
                }

                // Checks 2-4: validation block expressions and paths.
                if let Some(validation) = &self.validation {
                    validate_validation_block(
                        validation,
                        &own_field_names,
                        &self.name,
                    )?;
                }

                Ok(())
            }

            // ── Derived schema ───────────────────────────────────────────────
            SchemaKind::Derived { from, operations } => {
                let parent = registry
                    .get(from)
                    .ok_or_else(|| TransformError::UnknownSchema(from.clone()))?;

                // Check 5 + 6: parent exists and no cycle.
                let mut chain = vec![self.name.clone()];
                let parent_fields = resolve_effective_fields(parent, registry, &mut chain)?;

                // Checks 7 + 8: operation FieldPaths + expression FieldRefs.
                // Returns the final available field set after all operations.
                let output_fields = validate_pipeline(operations, parent_fields)?;

                // Check 9: validation block on the derived schema itself,
                // validated against the output field set.
                if let Some(validation) = &self.validation {
                    validate_validation_block(
                        validation,
                        &output_fields,
                        &self.name,
                    )?;
                }

                Ok(())
            }
        }
    }
}

// =============================================================================
// validate_pipeline
// =============================================================================

/// Walk `operations` while tracking the evolving available field set.
///
/// Returns the **final** available field names after all operations have been
/// simulated — used to validate any validation block attached to the derived
/// schema itself.
///
/// Returns an error the moment any operation references a field that is not
/// available at that point in the pipeline.
fn validate_pipeline(
    operations: &[SchemaOperation],
    parent_fields: Vec<String>,
) -> Result<Vec<String>, JsonTError> {
    let mut available: Vec<String> = parent_fields;

    for (idx, op) in operations.iter().enumerate() {
        let op_num = idx + 1;

        match op {
            SchemaOperation::Rename(pairs) => {
                for pair in pairs {
                    let key = pair.from.join();
                    let pos = available
                        .iter()
                        .position(|n| n == &key)
                        .ok_or_else(|| TransformError::FieldNotFound(format!(
                            "Rename (op #{op_num}): field '{key}' does not exist"
                        )))?;
                    available[pos] = pair.to.clone();
                }
            }

            SchemaOperation::Exclude(paths) => {
                for path in paths {
                    let key = path.join();
                    let pos = available
                        .iter()
                        .position(|n| n == &key)
                        .ok_or_else(|| TransformError::FieldNotFound(format!(
                            "Exclude (op #{op_num}): field '{key}' does not exist"
                        )))?;
                    available.remove(pos);
                }
            }

            SchemaOperation::Project(paths) => {
                let keep: HashSet<String> = paths.iter().map(|p| p.join()).collect();
                for p in paths {
                    let key = p.join();
                    if !available.contains(&key) {
                        return Err(TransformError::FieldNotFound(format!(
                            "Project (op #{op_num}): field '{key}' does not exist"
                        ))
                        .into());
                    }
                }
                available.retain(|n| keep.contains(n));
            }

            SchemaOperation::Filter { refs, .. } => {
                for field_ref in refs {
                    if !available.contains(field_ref) {
                        return Err(TransformError::FieldNotFound(format!(
                            "Filter (op #{op_num}): expression references field '{field_ref}' \
                             which is not available at this point in the pipeline \
                             (it may have been excluded or projected away by an earlier operation)"
                        ))
                        .into());
                    }
                }
            }

            SchemaOperation::Transform { target, refs, .. } => {
                let key = target.join();
                if !available.contains(&key) {
                    return Err(TransformError::FieldNotFound(format!(
                        "Transform (op #{op_num}): target field '{key}' is not available"
                    ))
                    .into());
                }
                for field_ref in refs {
                    if !available.contains(field_ref) {
                        return Err(TransformError::FieldNotFound(format!(
                            "Transform (op #{op_num}): expression for '{key}' references field \
                             '{field_ref}' which is not available at this point in the pipeline"
                        ))
                        .into());
                    }
                }
            }

            // Phase 8: field-state tracking (Encrypted→Plain) will be added here.
            SchemaOperation::Decrypt { .. } => {}
        }
    }

    Ok(available)
}

// =============================================================================
// validate_validation_block
// =============================================================================

/// Check that every field reference inside a `JsonTValidationBlock` resolves
/// to a name in `available_fields`.
///
/// Covers:
///   - `rules` expressions (plain boolean and conditional requirements)
///   - `unique` constraint field paths
///   - `dataset` expressions
fn validate_validation_block(
    validation: &JsonTValidationBlock,
    available: &[String],
    schema_name: &str,
) -> Result<(), JsonTError> {
    // ── Rules ─────────────────────────────────────────────────────────────────
    for (rule_idx, rule) in validation.rules.iter().enumerate() {
        let rule_num = rule_idx + 1;

        match rule {
            JsonTRule::Expression(expr) => {
                for field_ref in collect_field_refs(expr) {
                    if !available.contains(&field_ref) {
                        return Err(TransformError::FieldNotFound(format!(
                            "schema '{schema_name}' rule #{rule_num}: \
                             expression references undeclared field '{field_ref}'"
                        ))
                        .into());
                    }
                }
            }

            JsonTRule::ConditionalRequirement { condition, required_fields } => {
                for field_ref in collect_field_refs(condition) {
                    if !available.contains(&field_ref) {
                        return Err(TransformError::FieldNotFound(format!(
                            "schema '{schema_name}' rule #{rule_num}: \
                             condition expression references undeclared field '{field_ref}'"
                        ))
                        .into());
                    }
                }
                for fp in required_fields {
                    let key = fp.join();
                    if !available.contains(&key) {
                        return Err(TransformError::FieldNotFound(format!(
                            "schema '{schema_name}' rule #{rule_num}: \
                             required field '{key}' is not declared in this schema"
                        ))
                        .into());
                    }
                }
            }
        }
    }

    // ── Unique constraint paths ───────────────────────────────────────────────
    for (ui, group) in validation.unique.iter().enumerate() {
        for fp in group {
            let key = fp.join();
            if !available.contains(&key) {
                return Err(TransformError::FieldNotFound(format!(
                    "schema '{schema_name}' unique constraint #{}: \
                     field '{key}' is not declared in this schema",
                    ui + 1
                ))
                .into());
            }
        }
    }

    // ── Dataset expressions ───────────────────────────────────────────────────
    for (di, expr) in validation.dataset.iter().enumerate() {
        for field_ref in collect_field_refs(expr) {
            if !available.contains(&field_ref) {
                return Err(TransformError::FieldNotFound(format!(
                    "schema '{schema_name}' dataset rule #{}: \
                     expression references undeclared field '{field_ref}'",
                    di + 1
                ))
                .into());
            }
        }
    }

    Ok(())
}

// =============================================================================
// Expression FieldRef collection
// =============================================================================

/// Recursively collect every `FieldRef` path string from an expression tree.
pub(crate) fn collect_field_refs(expr: &JsonTExpression) -> Vec<String> {
    let mut refs = Vec::new();
    collect_field_refs_inner(expr, &mut refs);
    refs
}

fn collect_field_refs_inner(expr: &JsonTExpression, refs: &mut Vec<String>) {
    match expr {
        JsonTExpression::Literal(_) => {}
        JsonTExpression::FieldRef(fp) => refs.push(fp.join()),
        JsonTExpression::FunctionCall { args, .. } => {
            for arg in args {
                collect_field_refs_inner(arg, refs);
            }
        }
        JsonTExpression::UnaryOp { operand, .. } => collect_field_refs_inner(operand, refs),
        JsonTExpression::BinaryOp { left, right, .. } => {
            collect_field_refs_inner(left, refs);
            collect_field_refs_inner(right, refs);
        }
    }
}

// =============================================================================
// Effective field resolution
// =============================================================================

/// Return the ordered list of output field names for `schema` after its full
/// derivation chain has been applied.
///
/// `chain` accumulates schema names on the current resolution path; a repeated
/// name signals a cycle.
fn resolve_effective_fields(
    schema: &JsonTSchema,
    registry: &SchemaRegistry,
    chain: &mut Vec<String>,
) -> Result<Vec<String>, TransformError> {
    if chain.contains(&schema.name) {
        chain.push(schema.name.clone());
        return Err(TransformError::CyclicDerivation(chain.clone()));
    }
    chain.push(schema.name.clone());

    match &schema.kind {
        SchemaKind::Straight { fields } => {
            Ok(fields.iter().map(|f| f.name.clone()).collect())
        }

        SchemaKind::Derived { from, operations } => {
            let parent = registry
                .get(from)
                .ok_or_else(|| TransformError::UnknownSchema(from.clone()))?;

            let mut field_names = resolve_effective_fields(parent, registry, chain)?;

            for op in operations {
                match op {
                    SchemaOperation::Rename(pairs) => {
                        for pair in pairs {
                            let key = pair.from.join();
                            let pos = field_names
                                .iter()
                                .position(|n| n == &key)
                                .ok_or_else(|| TransformError::FieldNotFound(key.clone()))?;
                            field_names[pos] = pair.to.clone();
                        }
                    }

                    SchemaOperation::Exclude(paths) => {
                        for path in paths {
                            let key = path.join();
                            let pos = field_names
                                .iter()
                                .position(|n| n == &key)
                                .ok_or_else(|| TransformError::FieldNotFound(key.clone()))?;
                            field_names.remove(pos);
                        }
                    }

                    SchemaOperation::Project(paths) => {
                        let keep: HashSet<String> = paths.iter().map(|p| p.join()).collect();
                        for p in paths {
                            let key = p.join();
                            if !field_names.contains(&key) {
                                return Err(TransformError::FieldNotFound(key));
                            }
                        }
                        field_names.retain(|n| keep.contains(n));
                    }

                    SchemaOperation::Filter { .. }
                    | SchemaOperation::Transform { .. }
                    | SchemaOperation::Decrypt { .. } => {}
                }
            }

            Ok(field_names)
        }
    }
}

// =============================================================================
// SchemaOperation constructors
// =============================================================================
//
// Defined here (rather than in model/schema.rs) because they depend on
// collect_field_refs, which in turn depends on JsonTExpression — keeping the
// dependency flow one-way: transform → model.

impl SchemaOperation {
    /// Construct a `Filter` operation, pre-computing the referenced field names.
    ///
    /// Use this instead of the struct literal when building schemas in code
    /// (tests, builders) so the `refs` field is always correct.
    pub fn new_filter(expr: JsonTExpression) -> Self {
        let refs = collect_field_refs(&expr);
        Self::Filter { expr, refs }
    }

    /// Construct a `Transform` operation, pre-computing the referenced field names.
    ///
    /// Use this instead of the struct literal when building schemas in code
    /// (tests, builders) so the `refs` field is always correct.
    pub fn new_transform(target: FieldPath, expr: JsonTExpression) -> Self {
        let refs = collect_field_refs(&expr);
        Self::Transform { target, expr, refs }
    }
}

// =============================================================================
// Operation application (row-level)
// =============================================================================

/// Apply one `SchemaOperation` to the current named working state.
///
/// Returns the updated working state, or an error.
/// `Err(TransformError::Filtered)` from `Filter` is a row-skip signal, not
/// a pipeline failure.
fn apply_operation(
    op: &SchemaOperation,
    working: Vec<(String, JsonTValue)>,
) -> Result<Vec<(String, JsonTValue)>, JsonTError> {
    match op {
        SchemaOperation::Rename(pairs) => {
            let mut w = working;
            for pair in pairs {
                let key = pair.from.join();
                let pos = w
                    .iter()
                    .position(|(n, _)| n == &key)
                    .ok_or_else(|| TransformError::FieldNotFound(key.clone()))?;
                w[pos].0 = pair.to.clone();
            }
            Ok(w)
        }

        SchemaOperation::Exclude(paths) => {
            let exclude: HashSet<String> = paths.iter().map(|p| p.join()).collect();
            for path in paths {
                let key = path.join();
                if !working.iter().any(|(n, _)| n == &key) {
                    return Err(TransformError::FieldNotFound(key).into());
                }
            }
            Ok(working.into_iter().filter(|(n, _)| !exclude.contains(n)).collect())
        }

        SchemaOperation::Project(paths) => {
            let keep: HashSet<String> = paths.iter().map(|p| p.join()).collect();
            for path in paths {
                let key = path.join();
                if !working.iter().any(|(n, _)| n == &key) {
                    return Err(TransformError::FieldNotFound(key).into());
                }
            }
            Ok(working.into_iter().filter(|(n, _)| keep.contains(n)).collect())
        }

        SchemaOperation::Filter { expr, refs } => {
            // refs was pre-computed at schema parse time — no per-row AST traversal.
            let ctx = build_minimal_eval_ctx(&working, refs);
            let result = expr.evaluate(&ctx).map_err(|e| match e {
                JsonTError::Eval(eval_err) => {
                    JsonTError::from(TransformError::FilterFailed(eval_err))
                }
                other => other,
            })?;
            match result {
                JsonTValue::Bool(true) => Ok(working),
                JsonTValue::Bool(false) => Err(TransformError::Filtered.into()),
                other => Err(TransformError::FilterFailed(EvalError::InvalidExpression(
                    format!("filter must return bool, got {}", other.type_name()),
                ))
                .into()),
            }
        }

        SchemaOperation::Transform { target, expr, refs } => {
            let key = target.join();
            // refs was pre-computed at schema parse time — no per-row AST traversal.
            let ctx = build_minimal_eval_ctx(&working, refs);
            let new_val = expr.evaluate(&ctx).map_err(|e| match e {
                JsonTError::Eval(eval_err) => JsonTError::from(TransformError::TransformFailed {
                    field: key.clone(),
                    source: eval_err,
                }),
                other => other,
            })?;
            let mut w = working;
            let pos = w
                .iter()
                .position(|(n, _)| n == &key)
                .ok_or_else(|| TransformError::FieldNotFound(key.clone()))?;
            w[pos].1 = new_val;
            Ok(w)
        }

        // Phase 8: CryptoConfig-based decryption will be implemented here.
        // For now, Decrypt is a no-op pass-through at the row level.
        SchemaOperation::Decrypt { .. } => Ok(working),
    }
}

// =============================================================================
// Helpers
// =============================================================================

/// Build an `EvalContext` containing only the fields whose names appear in `refs`.
///
/// Takes a slice rather than a `HashSet` — `refs` holds typically 2–4 entries
/// (pre-computed from the expression AST at schema parse time), so a linear scan
/// is faster than hashing and eliminates the per-row `HashSet` allocation entirely.
fn build_minimal_eval_ctx(working: &[(String, JsonTValue)], refs: &[String]) -> EvalContext {
    working
        .iter()
        .filter(|(name, _)| refs.contains(name))
        .fold(EvalContext::new(), |ctx, (name, value)| {
            ctx.bind(name.clone(), value.clone())
        })
}
