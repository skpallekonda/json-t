// =============================================================================
// lib.rs — JsonT crate root
// =============================================================================
// Public surface:
//   - Core traits:  Parseable, Stringifiable, Evaluatable, RowTransformer
//   - All model types re-exported from model::*
//   - Error types re-exported from error::*
// =============================================================================

pub mod error;
pub mod model;
pub mod builder;

// parse and stringify are implementation modules, not public API modules —
// their logic is exposed through the trait impls on the model types.
pub(crate) mod parse;
pub(crate) mod stringify;

// Re-export everything a consumer needs at the crate root.
pub use error::{JsonTError, ParseError, EvalError, TransformError, StringifyError};
pub use model::namespace::{JsonTNamespace, JsonTCatalog};
pub use model::schema::{JsonTSchema, SchemaKind, SchemaOperation};
pub use model::field::{JsonTField, JsonTFieldKind, JsonTFieldType, ScalarType};
pub use model::constraint::{
    JsonTConstraint,
    ValueConstraintKey, LengthConstraintKey,
    ArrayConstraintNbr, ArrayConstraintBool,
};
pub use model::validation::{
    JsonTValidationBlock, JsonTRule, JsonTExpression,
    UnaryOp, BinaryOp,
};
pub use model::enumdef::JsonTEnum;
pub use model::data::{JsonTValue, JsonTNumber, JsonTRow, JsonTArray};
pub use builder::namespace::JsonTNamespaceBuilder;
pub use builder::catalog::JsonTCatalogBuilder;
pub use builder::schema::JsonTSchemaBuilder;
pub use builder::field::JsonTFieldBuilder;
pub use builder::enumdef::JsonTEnumBuilder;
pub use builder::validation::JsonTValidationBlockBuilder;
pub use builder::data::{JsonTRowBuilder, JsonTArrayBuilder};
pub use builder::infer::SchemaInferrer;

// ─────────────────────────────────────────────────────────────────────────────
// Core traits
// ─────────────────────────────────────────────────────────────────────────────

/// Parse a JsonT source string into a strongly-typed value.
pub trait Parseable: Sized {
    fn parse(input: &str) -> Result<Self, JsonTError>;
}

/// Serialize a JsonT value back to a JsonT source string.
pub trait Stringifiable {
    fn stringify(&self, options: StringifyOptions) -> String;
}

/// Evaluate a JsonT expression in a binding context.
pub trait Evaluatable {
    fn evaluate(&self, ctx: &EvalContext) -> Result<JsonTValue, JsonTError>;
}

/// Apply a transformation pipeline to a row, producing a new row.
/// Implementations must accept a SchemaRegistry so derived schemas can
/// resolve their parent chain at transform time.
pub trait RowTransformer {
    fn transform(
        &self,
        row: JsonTRow,
        registry: &SchemaRegistry,
    ) -> Result<JsonTRow, JsonTError>;
}

// ─────────────────────────────────────────────────────────────────────────────
// Supporting types used by the traits above
// ─────────────────────────────────────────────────────────────────────────────

/// Options controlling how Stringifiable output is formatted.
#[derive(Debug, Clone)]
pub struct StringifyOptions {
    /// Emit human-readable indented output when true; compact single-line otherwise.
    pub pretty: bool,
    /// Number of spaces per indentation level (ignored when pretty = false).
    pub indent: usize,
}

impl Default for StringifyOptions {
    fn default() -> Self {
        Self { pretty: false, indent: 2 }
    }
}

impl StringifyOptions {
    pub fn compact() -> Self {
        Self { pretty: false, indent: 2 }
    }

    pub fn pretty() -> Self {
        Self { pretty: true, indent: 2 }
    }

    pub fn pretty_with_indent(indent: usize) -> Self {
        Self { pretty: true, indent }
    }
}

/// Binding context supplied to expression evaluation.
/// Maps field path segments (dot-joined) to their current values.
#[derive(Debug, Default, Clone)]
pub struct EvalContext {
    pub bindings: std::collections::HashMap<String, JsonTValue>,
}

impl EvalContext {
    pub fn new() -> Self {
        Self::default()
    }

    pub fn bind(mut self, key: impl Into<String>, value: JsonTValue) -> Self {
        self.bindings.insert(key.into(), value);
        self
    }

    pub fn get(&self, key: &str) -> Option<&JsonTValue> {
        self.bindings.get(key)
    }
}

/// Registry of all known schemas by name.
/// Used during derived-schema transformation chain resolution.
#[derive(Debug, Default)]
pub struct SchemaRegistry {
    schemas: std::collections::HashMap<String, JsonTSchema>,
}

impl SchemaRegistry {
    pub fn new() -> Self {
        Self::default()
    }

    pub fn register(&mut self, schema: JsonTSchema) {
        self.schemas.insert(schema.name.clone(), schema);
    }

    pub fn get(&self, name: &str) -> Option<&JsonTSchema> {
        self.schemas.get(name)
    }

    /// Build a registry from a parsed namespace (all catalogs, all schemas).
    pub fn from_namespace(ns: &JsonTNamespace) -> Self {
        let mut registry = Self::new();
        for catalog in &ns.catalogs {
            for schema in &catalog.schemas {
                registry.register(schema.clone());
            }
        }
        registry
    }
}
