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
// # Command pattern (D1 + D2)
//   Operation dispatch uses two traits:
//     - RowOperationHandler   — per-row execution; one struct per SchemaOperation variant
//     - OperationScopeValidator — schema-level static analysis; same structs
//   Both use `try_apply(op, ...) -> Option<Result>`: `None` = wrong op variant,
//   `Some(result)` = this handler matched (ok or error).
//
// # validate_schema (static analysis)
//   See OperationScopeValidator for the operation-by-operation checks.
// =============================================================================

use std::collections::HashSet;

use crate::crypto::CipherSession;
use crate::{EvalContext, Evaluatable, RowTransformer, SchemaRegistry};
use crate::error::{EvalError, JsonTError, TransformError};
use crate::model::data::{JsonTRow, JsonTString, JsonTValue};
use crate::model::field::JsonTFieldKind;
use crate::model::schema::{FieldPath, JsonTSchema, SchemaKind, SchemaOperation};
use crate::model::validation::{JsonTExpression, JsonTRule, JsonTValidationBlock};

// =============================================================================
// Command pattern — RowOperationHandler
// =============================================================================

/// Row-level operation handler.
///
/// `try_apply` returns `None` when `op` is not this handler's variant, or
/// `Some(result)` when it matches — mutating `working` in place on success.
trait RowOperationHandler: Send + Sync {
    fn try_apply(
        &self,
        op: &SchemaOperation,
        working: &mut Vec<(String, JsonTValue)>,
        session: Option<&CipherSession>,
    ) -> Option<Result<(), JsonTError>>;
}

// ── One struct per SchemaOperation variant ────────────────────────────────────

struct RenameHandler;
struct ExcludeHandler;
struct ProjectHandler;
struct FilterHandler;
struct TransformHandler;
struct DecryptHandler;

impl RowOperationHandler for RenameHandler {
    fn try_apply(
        &self,
        op: &SchemaOperation,
        working: &mut Vec<(String, JsonTValue)>,
        _session: Option<&CipherSession>,
    ) -> Option<Result<(), JsonTError>> {
        let SchemaOperation::Rename(pairs) = op else { return None; };
        for pair in pairs {
            let key = pair.from.join();
            let pos = match working.iter().position(|(n, _)| n == &key) {
                Some(p) => p,
                None    => return Some(Err(TransformError::FieldNotFound(key).into())),
            };
            working[pos].0 = pair.to.clone();
        }
        Some(Ok(()))
    }
}

impl RowOperationHandler for ExcludeHandler {
    fn try_apply(
        &self,
        op: &SchemaOperation,
        working: &mut Vec<(String, JsonTValue)>,
        _session: Option<&CipherSession>,
    ) -> Option<Result<(), JsonTError>> {
        let SchemaOperation::Exclude(paths) = op else { return None; };
        let exclude: HashSet<String> = paths.iter().map(|p| p.join()).collect();
        for path in paths {
            let key = path.join();
            if !working.iter().any(|(n, _)| n == &key) {
                return Some(Err(TransformError::FieldNotFound(key).into()));
            }
        }
        working.retain(|(n, _)| !exclude.contains(n));
        Some(Ok(()))
    }
}

impl RowOperationHandler for ProjectHandler {
    fn try_apply(
        &self,
        op: &SchemaOperation,
        working: &mut Vec<(String, JsonTValue)>,
        _session: Option<&CipherSession>,
    ) -> Option<Result<(), JsonTError>> {
        let SchemaOperation::Project(paths) = op else { return None; };
        let keep: HashSet<String> = paths.iter().map(|p| p.join()).collect();
        for path in paths {
            let key = path.join();
            if !working.iter().any(|(n, _)| n == &key) {
                return Some(Err(TransformError::FieldNotFound(key).into()));
            }
        }
        working.retain(|(n, _)| keep.contains(n));
        Some(Ok(()))
    }
}

impl RowOperationHandler for FilterHandler {
    fn try_apply(
        &self,
        op: &SchemaOperation,
        working: &mut Vec<(String, JsonTValue)>,
        _session: Option<&CipherSession>,
    ) -> Option<Result<(), JsonTError>> {
        let SchemaOperation::Filter { expr, refs } = op else { return None; };
        let ctx    = build_minimal_eval_ctx(working, refs);
        let result = expr.evaluate(&ctx).map_err(|e| match e {
            JsonTError::Eval(eval_err) => JsonTError::from(TransformError::FilterFailed(eval_err)),
            other => other,
        });
        let result = match result {
            Err(e) => return Some(Err(e)),
            Ok(v)  => v,
        };
        match result {
            JsonTValue::Bool(true)  => Some(Ok(())),
            JsonTValue::Bool(false) => Some(Err(TransformError::Filtered.into())),
            other => Some(Err(TransformError::FilterFailed(EvalError::InvalidExpression(
                format!("filter must return bool, got {}", other.type_name()),
            )).into())),
        }
    }
}

impl RowOperationHandler for TransformHandler {
    fn try_apply(
        &self,
        op: &SchemaOperation,
        working: &mut Vec<(String, JsonTValue)>,
        _session: Option<&CipherSession>,
    ) -> Option<Result<(), JsonTError>> {
        let SchemaOperation::Transform { target, expr, refs } = op else { return None; };
        let key = target.join();
        let ctx = build_minimal_eval_ctx(working, refs);
        let new_val = match expr.evaluate(&ctx).map_err(|e| match e {
            JsonTError::Eval(eval_err) => JsonTError::from(TransformError::TransformFailed {
                field:  key.clone(),
                source: eval_err,
            }),
            other => other,
        }) {
            Ok(v)  => v,
            Err(e) => return Some(Err(e)),
        };
        let pos = match working.iter().position(|(n, _)| n == &key) {
            Some(p) => p,
            None    => return Some(Err(TransformError::FieldNotFound(key).into())),
        };
        working[pos].1 = new_val;
        Some(Ok(()))
    }
}

impl RowOperationHandler for DecryptHandler {
    fn try_apply(
        &self,
        op: &SchemaOperation,
        working: &mut Vec<(String, JsonTValue)>,
        session: Option<&CipherSession>,
    ) -> Option<Result<(), JsonTError>> {
        let SchemaOperation::Decrypt { fields } = op else { return None; };
        let session = match session {
            Some(s) => s,
            None => return Some(Err(TransformError::DecryptFailed {
                field:  String::new(),
                reason: "Decrypt operation requires a CipherSession; \
                         use transform_with_session instead of transform".into(),
            }.into())),
        };
        for field_name in fields {
            let pos = match working.iter().position(|(n, _)| n == field_name) {
                Some(p) => p,
                None    => return Some(Err(TransformError::FieldNotFound(field_name.clone()).into())),
            };
            let new_val = match &working[pos].1 {
                JsonTValue::Encrypted(_) => {
                    let plaintext = match working[pos].1.decrypt_bytes(field_name, session) {
                        Ok(Some(b)) => b,
                        Ok(None)    => unreachable!("matched Encrypted variant"),
                        Err(e)      => return Some(Err(TransformError::DecryptFailed {
                            field:  field_name.clone(),
                            reason: e.to_string(),
                        }.into())),
                    };
                    let s = match String::from_utf8(plaintext) {
                        Ok(s)  => s,
                        Err(e) => return Some(Err(TransformError::DecryptFailed {
                            field:  field_name.clone(),
                            reason: format!("decrypted bytes are not valid UTF-8: {e}"),
                        }.into())),
                    };
                    JsonTValue::Str(JsonTString::Plain(s))
                }
                // Already plaintext — idempotent, leave unchanged.
                other => other.clone(),
            };
            working[pos].1 = new_val;
        }
        Some(Ok(()))
    }
}

/// Static registry of all row-operation handlers, in variant order.
static ROW_HANDLERS: &[&(dyn RowOperationHandler + Sync)] = &[
    &RenameHandler,
    &ExcludeHandler,
    &ProjectHandler,
    &FilterHandler,
    &TransformHandler,
    &DecryptHandler,
];

// =============================================================================
// Command pattern — OperationScopeValidator
// =============================================================================

/// Schema-level static analysis for one operation variant.
///
/// `try_validate` returns `None` when `op` is not this validator's variant,
/// or `Some(result)` when it matches — mutating `available` to simulate the
/// evolving field set.
trait OperationScopeValidator: Send + Sync {
    fn try_validate(
        &self,
        op: &SchemaOperation,
        available: &mut Vec<String>,
        op_num: usize,
    ) -> Option<Result<(), JsonTError>>;
}

struct RenameScopeValidator;
struct ExcludeScopeValidator;
struct ProjectScopeValidator;
struct FilterScopeValidator;
struct TransformScopeValidator;
struct DecryptScopeValidator;

impl OperationScopeValidator for RenameScopeValidator {
    fn try_validate(
        &self,
        op: &SchemaOperation,
        available: &mut Vec<String>,
        op_num: usize,
    ) -> Option<Result<(), JsonTError>> {
        let SchemaOperation::Rename(pairs) = op else { return None; };
        for pair in pairs {
            let key = pair.from.join();
            match available.iter().position(|n| n == &key) {
                Some(pos) => available[pos] = pair.to.clone(),
                None => return Some(Err(TransformError::FieldNotFound(format!(
                    "Rename (op #{op_num}): field '{key}' does not exist"
                )).into())),
            }
        }
        Some(Ok(()))
    }
}

impl OperationScopeValidator for ExcludeScopeValidator {
    fn try_validate(
        &self,
        op: &SchemaOperation,
        available: &mut Vec<String>,
        op_num: usize,
    ) -> Option<Result<(), JsonTError>> {
        let SchemaOperation::Exclude(paths) = op else { return None; };
        for path in paths {
            let key = path.join();
            match available.iter().position(|n| n == &key) {
                Some(pos) => { available.remove(pos); }
                None => return Some(Err(TransformError::FieldNotFound(format!(
                    "Exclude (op #{op_num}): field '{key}' does not exist"
                )).into())),
            }
        }
        Some(Ok(()))
    }
}

impl OperationScopeValidator for ProjectScopeValidator {
    fn try_validate(
        &self,
        op: &SchemaOperation,
        available: &mut Vec<String>,
        op_num: usize,
    ) -> Option<Result<(), JsonTError>> {
        let SchemaOperation::Project(paths) = op else { return None; };
        let keep: HashSet<String> = paths.iter().map(|p| p.join()).collect();
        for p in paths {
            let key = p.join();
            if !available.contains(&key) {
                return Some(Err(TransformError::FieldNotFound(format!(
                    "Project (op #{op_num}): field '{key}' does not exist"
                )).into()));
            }
        }
        available.retain(|n| keep.contains(n));
        Some(Ok(()))
    }
}

impl OperationScopeValidator for FilterScopeValidator {
    fn try_validate(
        &self,
        op: &SchemaOperation,
        available: &mut Vec<String>,
        op_num: usize,
    ) -> Option<Result<(), JsonTError>> {
        let SchemaOperation::Filter { refs, .. } = op else { return None; };
        for field_ref in refs {
            if !available.contains(field_ref) {
                return Some(Err(TransformError::FieldNotFound(format!(
                    "Filter (op #{op_num}): expression references field '{field_ref}' \
                     which is not available at this point in the pipeline \
                     (it may have been excluded or projected away by an earlier operation)"
                )).into()));
            }
        }
        Some(Ok(()))
    }
}

impl OperationScopeValidator for TransformScopeValidator {
    fn try_validate(
        &self,
        op: &SchemaOperation,
        available: &mut Vec<String>,
        op_num: usize,
    ) -> Option<Result<(), JsonTError>> {
        let SchemaOperation::Transform { target, refs, .. } = op else { return None; };
        let key = target.join();
        if !available.contains(&key) {
            return Some(Err(TransformError::FieldNotFound(format!(
                "Transform (op #{op_num}): target field '{key}' is not available"
            )).into()));
        }
        for field_ref in refs {
            if !available.contains(field_ref) {
                return Some(Err(TransformError::FieldNotFound(format!(
                    "Transform (op #{op_num}): expression for '{key}' references field \
                     '{field_ref}' which is not available at this point in the pipeline"
                )).into()));
            }
        }
        Some(Ok(()))
    }
}

impl OperationScopeValidator for DecryptScopeValidator {
    fn try_validate(
        &self,
        op: &SchemaOperation,
        _available: &mut Vec<String>,
        _op_num: usize,
    ) -> Option<Result<(), JsonTError>> {
        let SchemaOperation::Decrypt { .. } = op else { return None; };
        // Field-state tracking (Encrypted→Plain) is deferred; no error for now.
        Some(Ok(()))
    }
}

static SCOPE_VALIDATORS: &[&(dyn OperationScopeValidator + Sync)] = &[
    &RenameScopeValidator,
    &ExcludeScopeValidator,
    &ProjectScopeValidator,
    &FilterScopeValidator,
    &TransformScopeValidator,
    &DecryptScopeValidator,
];

// =============================================================================
// RowTransformer impl
// =============================================================================

impl RowTransformer for JsonTSchema {
    fn transform(
        &self,
        row: JsonTRow,
        registry: &SchemaRegistry,
    ) -> Result<JsonTRow, JsonTError> {
        self.apply_operations(row, registry, None)
    }
}

impl JsonTSchema {
    /// Transform one row with a [`CipherSession`] so that `Decrypt` operations
    /// can decrypt their fields in-place.
    ///
    /// Identical to `transform` except `Decrypt` operations use the session's
    /// pre-unwrapped DEK.
    pub fn transform_with_session(
        &self,
        row: JsonTRow,
        registry: &SchemaRegistry,
        session: &CipherSession,
    ) -> Result<JsonTRow, JsonTError> {
        self.apply_operations(row, registry, Some(session))
    }

    // ── shared core ──────────────────────────────────────────────────────────

    fn apply_operations(
        &self,
        row: JsonTRow,
        registry: &SchemaRegistry,
        session: Option<&CipherSession>,
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
                        row.len(), from, parent_fields.len()
                    )).into());
                }

                let mut working: Vec<(String, JsonTValue)> = parent_fields
                    .into_iter()
                    .zip(row.fields)
                    .collect();

                for op in operations {
                    apply_operation(op, &mut working, session)?;
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
    pub fn validate_schema(&self, registry: &SchemaRegistry) -> Result<(), JsonTError> {
        match &self.kind {
            SchemaKind::Straight { fields } => {
                let own_field_names: Vec<String> =
                    fields.iter().map(|f| f.name.clone()).collect();

                for field in fields {
                    if let JsonTFieldKind::Object { schema_ref, .. } = &field.kind {
                        if registry.get(schema_ref).is_none() {
                            return Err(TransformError::UnknownSchema(format!(
                                "field '{}' references unknown schema '{}'",
                                field.name, schema_ref
                            )).into());
                        }
                    }
                }

                if let Some(validation) = &self.validation {
                    validate_validation_block(validation, &own_field_names, &self.name)?;
                }

                Ok(())
            }

            SchemaKind::Derived { from, operations } => {
                let parent = registry
                    .get(from)
                    .ok_or_else(|| TransformError::UnknownSchema(from.clone()))?;

                let mut chain = vec![self.name.clone()];
                let parent_fields = resolve_effective_fields(parent, registry, &mut chain)?;

                let output_fields = validate_pipeline(operations, parent_fields)?;

                if let Some(validation) = &self.validation {
                    validate_validation_block(validation, &output_fields, &self.name)?;
                }

                Ok(())
            }
        }
    }
}

// =============================================================================
// validate_pipeline — static field-availability tracking
// =============================================================================

fn validate_pipeline(
    operations: &[SchemaOperation],
    parent_fields: Vec<String>,
) -> Result<Vec<String>, JsonTError> {
    let mut available = parent_fields;
    for (idx, op) in operations.iter().enumerate() {
        let op_num = idx + 1;
        for validator in SCOPE_VALIDATORS {
            if let Some(result) = validator.try_validate(op, &mut available, op_num) {
                result?;
                break;
            }
        }
    }
    Ok(available)
}

// =============================================================================
// validate_validation_block
// =============================================================================

fn validate_validation_block(
    validation: &JsonTValidationBlock,
    available: &[String],
    schema_name: &str,
) -> Result<(), JsonTError> {
    for (rule_idx, rule) in validation.rules.iter().enumerate() {
        let rule_num = rule_idx + 1;
        match rule {
            JsonTRule::Expression(expr) => {
                for field_ref in collect_field_refs(expr) {
                    if !available.contains(&field_ref) {
                        return Err(TransformError::FieldNotFound(format!(
                            "schema '{schema_name}' rule #{rule_num}: \
                             expression references undeclared field '{field_ref}'"
                        )).into());
                    }
                }
            }
            JsonTRule::ConditionalRequirement { condition, required_fields } => {
                for field_ref in collect_field_refs(condition) {
                    if !available.contains(&field_ref) {
                        return Err(TransformError::FieldNotFound(format!(
                            "schema '{schema_name}' rule #{rule_num}: \
                             condition expression references undeclared field '{field_ref}'"
                        )).into());
                    }
                }
                for fp in required_fields {
                    let key = fp.join();
                    if !available.contains(&key) {
                        return Err(TransformError::FieldNotFound(format!(
                            "schema '{schema_name}' rule #{rule_num}: \
                             required field '{key}' is not declared in this schema"
                        )).into());
                    }
                }
            }
        }
    }

    for (ui, group) in validation.unique.iter().enumerate() {
        for fp in group {
            let key = fp.join();
            if !available.contains(&key) {
                return Err(TransformError::FieldNotFound(format!(
                    "schema '{schema_name}' unique constraint #{}: \
                     field '{key}' is not declared in this schema",
                    ui + 1
                )).into());
            }
        }
    }

    for (di, expr) in validation.dataset.iter().enumerate() {
        for field_ref in collect_field_refs(expr) {
            if !available.contains(&field_ref) {
                return Err(TransformError::FieldNotFound(format!(
                    "schema '{schema_name}' dataset rule #{}: \
                     expression references undeclared field '{field_ref}'",
                    di + 1
                )).into());
            }
        }
    }

    Ok(())
}

// =============================================================================
// Expression FieldRef collection
// =============================================================================

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
            for arg in args { collect_field_refs_inner(arg, refs); }
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

impl SchemaOperation {
    pub fn new_filter(expr: JsonTExpression) -> Self {
        let refs = collect_field_refs(&expr);
        Self::Filter { expr, refs }
    }

    pub fn new_transform(target: FieldPath, expr: JsonTExpression) -> Self {
        let refs = collect_field_refs(&expr);
        Self::Transform { target, expr, refs }
    }
}

// =============================================================================
// apply_operation — row-level dispatch via ROW_HANDLERS
// =============================================================================

fn apply_operation(
    op: &SchemaOperation,
    working: &mut Vec<(String, JsonTValue)>,
    session: Option<&CipherSession>,
) -> Result<(), JsonTError> {
    for handler in ROW_HANDLERS {
        if let Some(result) = handler.try_apply(op, working, session) {
            return result;
        }
    }
    Ok(())
}

// =============================================================================
// Helpers
// =============================================================================

fn build_minimal_eval_ctx(working: &[(String, JsonTValue)], refs: &[String]) -> EvalContext {
    working
        .iter()
        .filter(|(name, _)| refs.contains(name))
        .fold(EvalContext::new(), |ctx, (name, value)| {
            ctx.bind(name.clone(), value.clone())
        })
}
