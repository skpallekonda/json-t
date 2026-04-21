// =============================================================================
// transform/resolve.rs — parse-time schema resolution
// =============================================================================
//
// Computes `ResolvedSchema` for every schema in a registry exactly once.
// Called from `SchemaRegistry::from_namespace` after all schemas are
// registered and validated.
//
// Resolution is topological: parents are resolved before children.
// Cycles are caught and returned as `TransformError::CyclicDerivation`.
// =============================================================================

use std::collections::{HashMap, HashSet};

use crate::error::{JsonTError, TransformError};
use crate::model::field::JsonTField;
use crate::model::resolved::{
    PrecomputedBinding, ResolvedRule, ResolvedSchema, ResolvedStep, ResolvedValidation,
};
use crate::model::schema::{JsonTSchema, SchemaKind, SchemaOperation};
use crate::model::validation::{JsonTExpression, JsonTRule, JsonTValidationBlock};
use crate::SchemaRegistry;
use crate::transform::collect_field_refs;

/// Compute `ResolvedSchema` for every schema in `registry` and write the
/// result into `schema.resolved`.
///
/// Must be called after all schemas are registered and validated.
pub(crate) fn resolve_all(registry: &mut SchemaRegistry) -> Result<(), JsonTError> {
    let names: Vec<String> = registry.schemas.keys().cloned().collect();
    let mut resolved_map: HashMap<String, ResolvedSchema> = HashMap::new();

    for name in &names {
        resolve_one(name, &registry.schemas, &mut resolved_map, &mut HashSet::new())?;
    }

    for (name, resolved) in resolved_map {
        if let Some(schema) = registry.schemas.get_mut(&name) {
            schema.resolved = Some(resolved);
        }
    }

    Ok(())
}

/// Recursively resolve one schema, ensuring its parent is resolved first.
fn resolve_one(
    name: &str,
    schemas: &HashMap<String, JsonTSchema>,
    resolved_map: &mut HashMap<String, ResolvedSchema>,
    visiting: &mut HashSet<String>,
) -> Result<(), JsonTError> {
    if resolved_map.contains_key(name) {
        return Ok(());
    }
    if !visiting.insert(name.to_string()) {
        let mut chain: Vec<String> = visiting.iter().cloned().collect();
        chain.push(name.to_string());
        return Err(TransformError::CyclicDerivation(chain).into());
    }

    let schema = schemas
        .get(name)
        .ok_or_else(|| TransformError::UnknownSchema(name.to_string()))?;

    // For derived schemas resolve parent first so its effective_fields are ready.
    if let SchemaKind::Derived { from, .. } = &schema.kind.clone() {
        resolve_one(from, schemas, resolved_map, visiting)?;
    }

    let resolved = compute_resolved(schema, schemas, resolved_map)?;
    resolved_map.insert(name.to_string(), resolved);
    visiting.remove(name);
    Ok(())
}

/// Compute the `ResolvedSchema` for one schema.
fn compute_resolved(
    schema: &JsonTSchema,
    schemas: &HashMap<String, JsonTSchema>,
    resolved_map: &HashMap<String, ResolvedSchema>,
) -> Result<ResolvedSchema, JsonTError> {
    match &schema.kind {
        SchemaKind::Straight { fields } => {
            let validation = schema
                .validation
                .as_ref()
                .map(|v| resolve_validation(v, fields))
                .transpose()?;
            Ok(ResolvedSchema { steps: vec![], validation, effective_fields: None })
        }

        SchemaKind::Derived { from, operations } => {
            // Working state: (current_name, field_def) evolving through operations.
            let parent_fields = get_effective_fields(from, schemas, resolved_map)?;
            let mut working: Vec<(String, JsonTField)> = parent_fields
                .iter()
                .map(|f| (f.name.clone(), f.clone()))
                .collect();

            let mut steps = Vec::with_capacity(operations.len());
            for op in operations {
                let step = compute_step(op, &mut working)
                    .map_err(JsonTError::from)?;
                steps.push(step);
            }

            // Effective fields after all operations, with names updated by Rename ops.
            let effective_fields: Vec<JsonTField> = working
                .into_iter()
                .map(|(name, mut f)| { f.name = name; f })
                .collect();

            let validation = schema
                .validation
                .as_ref()
                .map(|v| resolve_validation(v, &effective_fields))
                .transpose()?;

            Ok(ResolvedSchema {
                steps,
                validation,
                effective_fields: Some(effective_fields),
            })
        }
    }
}

/// Returns the effective fields for a schema, using resolved_map for derived schemas.
fn get_effective_fields<'a>(
    name: &str,
    schemas: &'a HashMap<String, JsonTSchema>,
    resolved_map: &'a HashMap<String, ResolvedSchema>,
) -> Result<&'a [JsonTField], TransformError> {
    let schema = schemas
        .get(name)
        .ok_or_else(|| TransformError::UnknownSchema(name.to_string()))?;

    match &schema.kind {
        SchemaKind::Straight { fields } => Ok(fields.as_slice()),
        SchemaKind::Derived { .. } => resolved_map
            .get(name)
            .and_then(|r| r.effective_fields.as_deref())
            .ok_or_else(|| {
                TransformError::UnknownSchema(format!(
                    "parent schema '{name}' has not been resolved yet — topological ordering error"
                ))
            }),
    }
}

/// Compute one `ResolvedStep` for an operation, mutating `working` to reflect
/// the field-name/order changes it causes.
fn compute_step(
    op: &SchemaOperation,
    working: &mut Vec<(String, JsonTField)>,
) -> Result<ResolvedStep, TransformError> {
    match op {
        SchemaOperation::Rename(pairs) => {
            let mut renames = Vec::with_capacity(pairs.len());
            for pair in pairs {
                let key = pair.from.join();
                let pos = working
                    .iter()
                    .position(|(n, _)| n == &key)
                    .ok_or_else(|| TransformError::FieldNotFound(key.clone()))?;
                renames.push((pos, pair.to.clone()));
                working[pos].0 = pair.to.clone();
            }
            Ok(ResolvedStep::Rename { renames })
        }

        SchemaOperation::Exclude(paths) => {
            let excluded: HashSet<String> = paths.iter().map(|p| p.join()).collect();
            // Validate all paths exist before mutating.
            for path in paths {
                let key = path.join();
                if !working.iter().any(|(n, _)| n == &key) {
                    return Err(TransformError::FieldNotFound(key));
                }
            }
            // Collect original indices of kept fields.
            let keep: Vec<usize> = working
                .iter()
                .enumerate()
                .filter_map(|(i, (n, _))| if excluded.contains(n) { None } else { Some(i) })
                .collect();
            working.retain(|(n, _)| !excluded.contains(n));
            Ok(ResolvedStep::Reshape { keep })
        }

        SchemaOperation::Project(paths) => {
            // Build keep in the order listed by the Project op.
            let mut seen = HashSet::new();
            let mut keep: Vec<usize> = Vec::with_capacity(paths.len());
            let mut new_working = Vec::with_capacity(paths.len());
            for path in paths {
                let key = path.join();
                let pos = working
                    .iter()
                    .position(|(n, _)| n == &key)
                    .ok_or_else(|| TransformError::FieldNotFound(key.clone()))?;
                if seen.insert(pos) {
                    keep.push(pos);
                    new_working.push(working[pos].clone());
                }
            }
            *working = new_working;
            Ok(ResolvedStep::Reshape { keep })
        }

        SchemaOperation::Filter { refs, .. } => {
            let bindings = refs
                .iter()
                .map(|r| {
                    let pos = working
                        .iter()
                        .position(|(n, _)| n == r)
                        .ok_or_else(|| TransformError::FieldNotFound(r.clone()))?;
                    Ok(PrecomputedBinding::top_level(pos, r.clone()))
                })
                .collect::<Result<Vec<_>, TransformError>>()?;
            Ok(ResolvedStep::Filter { bindings })
        }

        SchemaOperation::Transform { target, refs, .. } => {
            let key = target.join();
            let target_pos = working
                .iter()
                .position(|(n, _)| n == &key)
                .ok_or_else(|| TransformError::FieldNotFound(key.clone()))?;
            let bindings = refs
                .iter()
                .map(|r| {
                    let pos = working
                        .iter()
                        .position(|(n, _)| n == r)
                        .ok_or_else(|| TransformError::FieldNotFound(r.clone()))?;
                    Ok(PrecomputedBinding::top_level(pos, r.clone()))
                })
                .collect::<Result<Vec<_>, TransformError>>()?;
            Ok(ResolvedStep::Transform { target_pos, bindings })
        }

        SchemaOperation::Decrypt { fields } => {
            let positions = fields
                .iter()
                .map(|f| {
                    let pos = working
                        .iter()
                        .position(|(n, _)| n == f)
                        .ok_or_else(|| TransformError::FieldNotFound(f.clone()))?;
                    Ok((pos, f.clone()))
                })
                .collect::<Result<Vec<_>, TransformError>>()?;
            Ok(ResolvedStep::Decrypt { positions })
        }
    }
}

/// Compute `ResolvedValidation` for a validation block, given the effective fields
/// of the schema at the point where the validation applies.
fn resolve_validation(
    validation: &JsonTValidationBlock,
    fields: &[JsonTField],
) -> Result<ResolvedValidation, JsonTError> {
    let field_names: Vec<&str> = fields.iter().map(|f| f.name.as_str()).collect();

    let rules = validation
        .rules
        .iter()
        .map(|rule| resolve_rule(rule, &field_names))
        .collect::<Result<Vec<_>, _>>()?;

    let unique_positions = validation
        .unique
        .iter()
        .map(|group| {
            group
                .iter()
                .map(|fp| {
                    let key = fp.join();
                    field_names
                        .iter()
                        .position(|n| *n == key.as_str())
                        .ok_or_else(|| {
                            JsonTError::from(TransformError::FieldNotFound(key))
                        })
                })
                .collect::<Result<Vec<_>, _>>()
        })
        .collect::<Result<Vec<_>, _>>()?;

    let dataset_bindings = validation
        .dataset
        .iter()
        .map(|expr| resolve_expr_bindings(expr, &field_names).map_err(JsonTError::from))
        .collect::<Result<Vec<_>, _>>()?;

    Ok(ResolvedValidation { rules, unique_positions, dataset_bindings })
}

fn resolve_rule(rule: &JsonTRule, field_names: &[&str]) -> Result<ResolvedRule, JsonTError> {
    match rule {
        JsonTRule::Expression(expr) => {
            let bindings =
                resolve_expr_bindings(expr, field_names).map_err(JsonTError::from)?;
            Ok(ResolvedRule::Expression { bindings })
        }
        JsonTRule::ConditionalRequirement { condition, required_fields } => {
            let condition_bindings =
                resolve_expr_bindings(condition, field_names).map_err(JsonTError::from)?;
            let required_positions = required_fields
                .iter()
                .map(|fp| {
                    let key = fp.join();
                    field_names
                        .iter()
                        .position(|n| *n == key.as_str())
                        .ok_or_else(|| {
                            JsonTError::from(TransformError::FieldNotFound(key))
                        })
                })
                .collect::<Result<Vec<_>, _>>()?;
            Ok(ResolvedRule::ConditionalRequirement {
                condition_bindings,
                required_positions,
            })
        }
    }
}

/// Build `PrecomputedBinding` entries for every field reference in `expr`.
fn resolve_expr_bindings(
    expr: &JsonTExpression,
    field_names: &[&str],
) -> Result<Vec<PrecomputedBinding>, TransformError> {
    let refs = collect_field_refs(expr);
    refs.into_iter()
        .map(|r| {
            let pos = field_names
                .iter()
                .position(|n| *n == r.as_str())
                .ok_or_else(|| TransformError::FieldNotFound(r.clone()))?;
            Ok(PrecomputedBinding::top_level(pos, r))
        })
        .collect()
}
