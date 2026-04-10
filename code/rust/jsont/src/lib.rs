// =============================================================================
// lib.rs — JsonT crate root
// =============================================================================
// Public surface:
//   - Core traits:  Parseable, Stringification, Evaluatable, RowTransformer
//   - All model types re-exported from model::*
//   - Error types re-exported from error::*
// =============================================================================

pub mod builder;
pub mod crypto;
pub mod diagnostic;
pub mod error;
pub mod model;
pub mod validate;

// parse and stringify are implementation modules, not public API modules —
// their logic is exposed through the trait impls on the model types.
pub(crate) mod parse;
pub(crate) mod stringify;

// transform: RowTransformer impl for JsonTSchema
pub(crate) mod transform;

// JSON interoperability — bidirectional JSON ↔ JsonTRow conversion.
pub mod json;

// Streaming row API — bypasses pest and per-row String allocation.
pub use parse::rows::parse_rows;
pub use parse::rows::parse_rows_streaming;
pub use parse::rows::RowIter;
pub use stringify::rows::{write_row, write_rows, write_row_with_schema};

// Re-export everything a consumer needs at the crate root.
pub use builder::catalog::JsonTCatalogBuilder;
pub use builder::data::{JsonTArrayBuilder, JsonTRowBuilder};
pub use builder::enumdef::JsonTEnumBuilder;
pub use builder::field::JsonTFieldBuilder;
pub use builder::infer::SchemaInferrer;
pub use builder::namespace::JsonTNamespaceBuilder;
pub use builder::schema::JsonTSchemaBuilder;
pub use builder::validation::JsonTValidationBlockBuilder;

// Diagnostic
pub use diagnostic::sink::{ConsoleSink, FileSink, MemorySink};
pub use diagnostic::{DiagnosticEvent, EventKind, Severity, SinkError};

// Crypto
pub use crypto::{CryptoConfig, CryptoError, PassthroughCryptoConfig};

// Error
pub use error::{EvalError, JsonTError, ParseError, StringifyError, TransformError};

// Model
pub use model::constraint::{
    ArrayConstraintBool, ArrayConstraintNbr, JsonTConstraint, LengthConstraintKey,
    ValueConstraintKey,
};
pub use model::data::{JsonTArray, JsonTNumber, JsonTRow, JsonTString, JsonTValue};
pub use model::enumdef::JsonTEnum;
pub use model::field::{AnyOfVariant, JsonTField, JsonTFieldKind, JsonTFieldType, ScalarType};
pub use model::namespace::{JsonTCatalog, JsonTNamespace};
pub use model::schema::{FieldPath, JsonTSchema, RenamePair, SchemaKind, SchemaOperation};
pub use model::validation::{BinaryOp, JsonTExpression, JsonTRule, JsonTValidationBlock, UnaryOp};

// ─────────────────────────────────────────────────────────────────────────────
// Core traits
// ─────────────────────────────────────────────────────────────────────────────

/// Parse a JsonT source string into a strongly-typed value.
pub trait Parseable: Sized {
    fn parse(input: &str) -> Result<Self, JsonTError>;
}

/// Serialize a JsonT value back to a JsonT source string.
pub trait Stringification {
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
    fn transform(&self, row: JsonTRow, registry: &SchemaRegistry) -> Result<JsonTRow, JsonTError>;
}

// ─────────────────────────────────────────────────────────────────────────────
// Supporting types used by the traits above
// ─────────────────────────────────────────────────────────────────────────────

/// Options controlling how Stringification output is formatted.
#[derive(Debug, Clone)]
pub struct StringifyOptions {
    /// Emit human-readable indented output when true; compact single-line otherwise.
    pub pretty: bool,
    /// Number of spaces per indentation level (ignored when pretty = false).
    pub indent: usize,
}

impl Default for StringifyOptions {
    fn default() -> Self {
        Self {
            pretty: false,
            indent: 2,
        }
    }
}

impl StringifyOptions {
    pub fn compact() -> Self {
        Self {
            pretty: false,
            indent: 2,
        }
    }

    pub fn pretty() -> Self {
        Self {
            pretty: true,
            indent: 2,
        }
    }

    pub fn pretty_with_indent(indent: usize) -> Self {
        Self {
            pretty: true,
            indent,
        }
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

/// Receive and handle diagnostic events emitted during pipeline processing.
///
/// Implement this trait to route events to any target:
/// console, file, database, event stream, etc.
/// The crate ships three built-in impls: ConsoleSink, FileSink, MemorySink.
pub trait DiagnosticSink {
    /// Receive one event. Implementations should not panic; errors should be
    /// swallowed or stored and surfaced via flush().
    fn emit(&mut self, event: diagnostic::DiagnosticEvent);

    /// Flush any buffered output. Called by the pipeline at the end of processing.
    fn flush(&mut self) -> Result<(), diagnostic::SinkError>;
}

// ─────────────────────────────────────────────────────────────────────────────
// Validate pipeline re-exports
// ─────────────────────────────────────────────────────────────────────────────
pub use validate::{ValidationPipeline, ValidationPipelineBuilder};
