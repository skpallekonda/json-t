// =============================================================================
// error.rs — JsonT error hierarchy
// =============================================================================
// JsonTError is the single top-level error type exposed by the crate.
// Each layer has its own sub-enum so callers can match on the specific cause.
// =============================================================================

use thiserror::Error;

/// Top-level crate error — wraps per-layer sub-errors.
#[derive(Debug, Error)]
pub enum JsonTError {
    #[error("parse error: {0}")]
    Parse(#[from] ParseError),

    #[error("evaluation error: {0}")]
    Eval(#[from] EvalError),

    #[error("transform error: {0}")]
    Transform(#[from] TransformError),

    #[error("stringify error: {0}")]
    Stringify(#[from] StringifyError),
}

// ─────────────────────────────────────────────────────────────────────────────
// Parse layer
// ─────────────────────────────────────────────────────────────────────────────

#[derive(Debug, Error)]
pub enum ParseError {
    /// Raw pest parse failure — includes line/column information.
    #[error("syntax error: {0}")]
    Pest(String),

    /// A schema name referenced in FROM or an object field does not exist
    /// in the same catalog.
    #[error("unknown schema reference: '{0}'")]
    UnknownSchemaRef(String),

    /// A type keyword was encountered that the grammar does not recognise.
    #[error("unknown field type: '{0}'")]
    UnknownFieldType(String),

    /// A constraint keyword appeared on a field type that does not support it
    /// (e.g. maxLength on an i32 field).
    #[error("invalid constraint on field '{field}': {reason}")]
    InvalidConstraint { field: String, reason: String },

    /// A CONSTID (enum value) was used in a context that expects a SCHEMAID.
    #[error("expected schema name but got constant identifier: '{0}'")]
    ExpectedSchemaid(String),

    /// General structural mismatch not covered by other variants.
    #[error("unexpected structure: {0}")]
    Unexpected(String),
}

// ─────────────────────────────────────────────────────────────────────────────
// Evaluation layer
// ─────────────────────────────────────────────────────────────────────────────

#[derive(Debug, Error)]
pub enum EvalError {
    /// An expression referenced a field name not present in the eval context.
    #[error("unbound field: '{0}'")]
    UnboundField(String),

    /// An operator received operands of incompatible types.
    #[error("type mismatch: expected {expected}, got {actual}")]
    TypeMismatch { expected: String, actual: String },

    /// Division by zero attempted in an arithmetic expression.
    #[error("division by zero")]
    DivisionByZero,

    /// A function call referenced a name that has no registered implementation.
    #[error("unknown function: '{0}'")]
    UnknownFunction(String),

    /// A function was called with the wrong number of arguments.
    #[error("arity mismatch for '{name}': expected {expected}, got {got}")]
    ArityMismatch { name: String, expected: usize, got: usize },

    /// An expression tree node was structurally invalid (should not happen
    /// after a successful parse, but can occur with programmatically built
    /// expression trees).
    #[error("invalid expression: {0}")]
    InvalidExpression(String),
}

// ─────────────────────────────────────────────────────────────────────────────
// Transform layer
// ─────────────────────────────────────────────────────────────────────────────

#[derive(Debug, Error)]
pub enum TransformError {
    /// A derived schema's FROM clause names a schema not in the registry.
    #[error("unknown parent schema: '{0}'")]
    UnknownSchema(String),

    /// A rename or exclude operation targeted a field that does not exist on
    /// the incoming row.
    #[error("field not found during transform: '{0}'")]
    FieldNotFound(String),

    /// The filter expression on a derived schema could not be evaluated.
    #[error("filter expression failed: {0}")]
    FilterFailed(#[source] EvalError),

    /// A transform expression on a field could not be evaluated.
    #[error("transform expression failed on field '{field}': {source}")]
    TransformFailed {
        field: String,
        #[source]
        source: EvalError,
    },

    /// Walking the derivation chain encountered a cycle.
    /// The Vec contains the schema names visited before the cycle was detected.
    #[error("cyclic schema derivation: {}", .0.join(" -> "))]
    CyclicDerivation(Vec<String>),

    /// A `filter(...)` predicate evaluated to false — the row is excluded.
    ///
    /// This is not a hard error; callers should skip the row and continue
    /// processing the rest of the stream.
    #[error("row filtered out by predicate")]
    Filtered,

    /// A `decrypt(...)` operation failed — either the crypto call returned an
    /// error or the decrypted bytes were not valid UTF-8.
    #[error("decrypt failed for field '{field}': {reason}")]
    DecryptFailed { field: String, reason: String },
}

// ─────────────────────────────────────────────────────────────────────────────
// Stringify layer
// ─────────────────────────────────────────────────────────────────────────────

#[derive(Debug, Error)]
pub enum StringifyError {
    /// A schema reference embedded in the model could not be resolved when
    /// serialising (e.g. object field referencing a schema not in the catalog).
    #[error("unresolved schema reference: '{0}'")]
    UnresolvedSchemaRef(String),

    /// A value could not be serialised to a valid JsonT literal.
    #[error("cannot stringify value: {0}")]
    UnstringifiableValue(String),
}

// ─────────────────────────────────────────────────────────────────────────────
// Builder layer (used by all builder::* modules)
// ─────────────────────────────────────────────────────────────────────────────

#[derive(Debug, Error)]
pub enum BuildError {
    /// A required field on the builder was not supplied before build() was called.
    #[error("missing required builder field: '{0}'")]
    MissingField(String),

    /// A constraint was added that is not valid for the field's scalar type.
    /// Collected and reported at build() time (option B decision).
    #[error("invalid constraint '{constraint}' for type '{field_type}' on field '{field}'")]
    InvalidConstraintForType {
        field: String,
        field_type: String,
        constraint: String,
    },

    /// Two fields in the same schema share the same name.
    #[error("duplicate field name: '{0}'")]
    DuplicateFieldName(String),

    /// Two schemas in the same catalog share the same name.
    #[error("duplicate schema name: '{0}'")]
    DuplicateSchemaName(String),

    /// Two enum values in the same enum definition are identical.
    #[error("duplicate enum value: '{0}'")]
    DuplicateEnumValue(String),

    /// A field definition is structurally invalid (e.g., anyOf with < 2 variants).
    #[error("invalid field '{field}': {reason}")]
    InvalidField { field: String, reason: String },

    /// The names hint supplied to SchemaInferrer does not match row field count.
    #[error("name hint count ({hint}) does not match row field count ({row})")]
    NameHintMismatch { hint: usize, row: usize },

    /// Schema-aware row builder: value type does not match expected field type.
    #[error("type mismatch at field '{field}': expected {expected}, got {actual}")]
    RowTypeMismatch { field: String, expected: String, actual: String },

    /// Schema-aware row builder: more values pushed than the schema has fields.
    #[error("too many values: schema has {schema_fields} fields, {pushed} values pushed")]
    TooManyValues { schema_fields: usize, pushed: usize },
}

impl From<BuildError> for JsonTError {
    fn from(e: BuildError) -> Self {
        // Wrap build errors as parse errors since they represent invalid
        // model construction — same semantic layer.
        JsonTError::Parse(ParseError::Unexpected(e.to_string()))
    }
}
