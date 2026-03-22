// =============================================================================
// model/schema.rs
// =============================================================================
// JsonTSchema      — a single schema entry (straight or derived)
// SchemaKind       — discriminates straight vs derived
// SchemaOperation  — the operations available in a derived schema
// =============================================================================

use crate::model::field::JsonTField;
use crate::model::validation::JsonTValidationBlock;

/// A single schema definition within a catalog.
///
/// The `name` is always present; `kind` carries the structural content.
#[derive(Debug, Clone, PartialEq)]
pub struct JsonTSchema {
    /// The schema name (SCHEMAID — starts with an uppercase letter).
    pub name: String,

    /// Whether this is a straight (field-declared) schema or a derived one.
    pub kind: SchemaKind,

    /// Optional validation rules attached to this schema.
    pub validation: Option<JsonTValidationBlock>,
}

/// Discriminates the two schema forms.
#[derive(Debug, Clone, PartialEq)]
pub enum SchemaKind {
    /// A straight schema declares its fields directly.
    Straight {
        fields: Vec<JsonTField>,
    },

    /// A derived schema references a parent schema by name and applies a
    /// sequence of operations to its field set.
    ///
    /// Operations are stored in declaration order and applied as a pipeline
    /// when `RowTransformer::transform` is called.
    Derived {
        /// The name of the parent schema (resolved via SchemaRegistry at
        /// transform time — not resolved at parse time to allow forward refs).
        from: String,
        /// The ordered list of operations to apply to each incoming row.
        operations: Vec<SchemaOperation>,
    },
}

/// One operation in a derived schema's `operations(...)` block.
///
/// Operations are applied left-to-right in the order they appear in source.
#[derive(Debug, Clone, PartialEq)]
pub enum SchemaOperation {
    /// Rename fields: each pair maps an existing field path to a new name.
    Rename(Vec<RenamePair>),

    /// Drop the listed field paths from the row entirely.
    Exclude(Vec<FieldPath>),

    /// Keep only the listed field paths; drop everything else.
    Project(Vec<FieldPath>),

    /// Drop rows where the expression evaluates to false.
    Filter(crate::model::validation::JsonTExpression),

    /// Replace the value at `target` with the result of evaluating `expr`.
    Transform {
        target: FieldPath,
        expr: crate::model::validation::JsonTExpression,
    },
}

/// A single rename mapping in a Rename operation.
#[derive(Debug, Clone, PartialEq)]
pub struct RenamePair {
    /// The dot-separated path to the field being renamed (e.g. `["address", "city"]`).
    pub from: FieldPath,
    /// The new field name (a single FIELDID).
    pub to: String,
}

/// A dot-separated field reference, stored as an ordered list of path segments.
///
/// `person.address.city` → `["person", "address", "city"]`
#[derive(Debug, Clone, PartialEq)]
pub struct FieldPath(pub Vec<String>);

impl FieldPath {
    pub fn new(segments: Vec<String>) -> Self {
        Self(segments)
    }

    pub fn single(name: impl Into<String>) -> Self {
        Self(vec![name.into()])
    }

    /// Join segments with '.' for display and stringification.
    pub fn join(&self) -> String {
        self.0.join(".")
    }
}
