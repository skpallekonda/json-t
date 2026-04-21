// =============================================================================
// model/resolved.rs
// =============================================================================
// Pre-computed execution descriptors built once at namespace parse time.
//
// ResolvedSchema      — unified resolved view for any schema (straight or derived)
// ResolvedStep        — compact per-operation descriptor for derived schemas
// ResolvedValidation  — pre-computed binding recipes for validation rules
// PrecomputedBinding  — maps a field path (as position indices) to an EvalContext key
// ResolvedRule        — per-rule resolved form (expression or conditional)
// =============================================================================

/// Pre-computed execution descriptor for a schema.
///
/// Built once when the namespace is parsed (or via the builder API).
/// Straight schemas get `steps = []` and `effective_fields = None`.
/// Derived schemas get the full step sequence and their output field list.
/// Any schema with a validation block gets `validation`.
#[derive(Debug, Clone, PartialEq)]
pub struct ResolvedSchema {
    /// One entry per SchemaOperation in the derivation chain.
    /// Empty for straight schemas.
    pub steps: Vec<ResolvedStep>,

    /// Pre-computed binding recipes for the validation block.
    /// Present on any schema that declares a `validations { ... }` block.
    pub validation: Option<ResolvedValidation>,

    /// The output fields after all operations are applied.
    /// `None` for straight schemas — callers use `SchemaKind::Straight { fields }` directly.
    pub effective_fields: Option<Vec<crate::model::field::JsonTField>>,
}

/// Compact descriptor for one derived-schema operation.
///
/// All positions are indices into the working-state vector at the point this
/// step runs — they account for all prior Rename/Reshape steps in the chain.
#[derive(Debug, Clone, PartialEq)]
pub enum ResolvedStep {
    /// One or more field renames applied in a single `rename(...)` op.
    Rename {
        /// `(working_position, new_name)` for each renamed field.
        renames: Vec<(usize, String)>,
    },

    /// Keeps only the listed positions, discarding the rest.
    ///
    /// Handles both `project(...)` (keep listed) and `exclude(...)` (drop listed) —
    /// both compile down to the same "keep these positions" form.
    Reshape {
        /// Positions to keep, in the order they appear after the reshape.
        keep: Vec<usize>,
    },

    /// Drop rows where the expression returns false.
    Filter {
        /// Pre-computed EvalContext bindings: which positions map to which variable names.
        bindings: Vec<PrecomputedBinding>,
    },

    /// Replace the value at `target_pos` with the result of evaluating `expr`.
    Transform {
        /// Position of the target field in the working state.
        target_pos: usize,
        /// Pre-computed EvalContext bindings for the expression's field refs.
        bindings: Vec<PrecomputedBinding>,
    },

    /// Decrypt the fields at the listed positions in-place.
    Decrypt {
        /// Positions of fields to decrypt, alongside their field names (for error reporting).
        positions: Vec<(usize, String)>,
    },
}

/// Pre-computed binding recipes for a validation block.
///
/// Built once at parse time; used per-row to construct EvalContexts in O(refs)
/// rather than O(F) by scanning all working fields.
#[derive(Debug, Clone, PartialEq)]
pub struct ResolvedValidation {
    /// One `ResolvedRule` per entry in the validation block's `rules { ... }`.
    pub rules: Vec<ResolvedRule>,

    /// For each unique-constraint group: the positions of the listed fields.
    pub unique_positions: Vec<Vec<usize>>,

    /// For each dataset rule: pre-computed bindings for its expression.
    pub dataset_bindings: Vec<Vec<PrecomputedBinding>>,
}

/// Resolved form of one validation rule.
#[derive(Debug, Clone, PartialEq)]
pub enum ResolvedRule {
    /// A plain boolean expression — pre-computed field-ref bindings.
    Expression {
        bindings: Vec<PrecomputedBinding>,
    },

    /// `condition -> required field1, field2` — bindings for the condition
    /// expression and positions of the required fields.
    ConditionalRequirement {
        condition_bindings: Vec<PrecomputedBinding>,
        /// Positions of the required fields in the effective fields list.
        required_positions: Vec<usize>,
    },
}

/// Maps a field (possibly nested) to a variable name in an EvalContext.
///
/// `path` holds the chain of positional indices needed to reach the value:
/// - `[pos]`               — top-level field at position `pos`
/// - `[pos, sub_pos, ...]` — descend into an Object value at each level
///
/// `key` is the variable name the expression uses to reference this field.
#[derive(Debug, Clone, PartialEq)]
pub struct PrecomputedBinding {
    /// Positional descent path into the row (and into nested Object values).
    pub path: Vec<usize>,
    /// The EvalContext variable name (the dot-joined field path string).
    pub key: String,
}

impl PrecomputedBinding {
    /// Convenience constructor for a simple top-level binding.
    pub fn top_level(pos: usize, key: impl Into<String>) -> Self {
        Self { path: vec![pos], key: key.into() }
    }
}
