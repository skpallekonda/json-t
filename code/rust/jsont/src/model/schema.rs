// =============================================================================
// model/schema.rs
// =============================================================================
// JsonTSchema      — a single schema entry (straight or derived)
// SchemaKind       — discriminates straight vs derived
// SchemaOperation  — the operations available in a derived schema
// =============================================================================

use crate::model::field::JsonTField;
use crate::model::validation::JsonTValidationBlock;
use crate::model::resolved::ResolvedSchema;

/// A single schema definition within a catalog.
///
/// The `name` is always present; `kind` carries the structural content.
/// `resolved` is populated once by the namespace resolver after all schemas
/// are parsed — it is `None` until that point.
#[derive(Debug, Clone, PartialEq)]
pub struct JsonTSchema {
    /// The schema name (SCHEMAID — starts with an uppercase letter).
    pub name: String,

    /// Whether this is a straight (field-declared) schema or a derived one.
    pub kind: SchemaKind,

    /// Optional validation rules attached to this schema.
    pub validation: Option<JsonTValidationBlock>,

    /// Pre-computed execution descriptor.  Populated by the namespace resolver;
    /// `None` until resolution runs.
    pub resolved: Option<ResolvedSchema>,
}

/// Discriminates the two schema forms.
#[derive(Debug, Clone, PartialEq)]
pub enum SchemaKind {
    /// A straight schema declares its fields directly.
    Straight {
        fields: Vec<JsonTField>,
    },

    /// Derived schema that uses a parent and applies operations in order.
    /// Parent name is resolved later to support forward references.
    Derived {
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
    ///
    /// `refs` holds the field names referenced by `expr`, pre-computed at parse
    /// time so the hot row-evaluation path never traverses the AST per row.
    Filter {
        expr: crate::model::validation::JsonTExpression,
        refs: Vec<String>,
    },

    /// Replace the value at `target` with the result of evaluating `expr`.
    ///
    /// `refs` holds the field names referenced by `expr`, pre-computed at parse
    /// time so the hot row-evaluation path never traverses the AST per row.
    Transform {
        target: FieldPath,
        expr: crate::model::validation::JsonTExpression,
        refs: Vec<String>,
    },

    /// This will decrypt the mentioned fields in-place. Subsequent operations 
    /// can treat these as plaintext. It skips if already in plaintext.
    Decrypt {
        /// Names of fields to decrypt.  Non-existent names are a build error.
        fields: Vec<String>,
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

impl JsonTSchema {
    /// Returns `true` if this straight schema declares at least one sensitive (`~`) field.
    ///
    /// Derived schemas always return `false` — sensitivity is declared on straight schemas.
    pub fn has_sensitive_fields(&self) -> bool {
        match &self.kind {
            SchemaKind::Straight { fields } => fields.iter().any(|f| f.kind.is_sensitive()),
            SchemaKind::Derived { .. } => false,
        }
    }

    /// Returns the effective output fields for this schema after all operations.
    ///
    /// For straight schemas this is the declared field list.
    /// For derived schemas this is `resolved.effective_fields`, which is populated
    /// by the namespace resolver.  Returns `None` if resolution has not run yet.
    pub fn effective_fields(&self) -> Option<&[JsonTField]> {
        match &self.kind {
            SchemaKind::Straight { fields } => Some(fields),
            SchemaKind::Derived { .. } => {
                self.resolved.as_ref()?.effective_fields.as_deref()
            }
        }
    }
}

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
