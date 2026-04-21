// =============================================================================
// model/namespace.rs
// =============================================================================
// JsonTNamespace  — top-level container (baseUrl, version, catalogs, data-schema)
// JsonTCatalog    — one catalog entry (schemas + optional enums)
// =============================================================================

use crate::model::schema::JsonTSchema;
use crate::model::enumdef::JsonTEnum;

/// The outermost JsonT namespace block.
///
/// Corresponds to the grammar rule:
/// ```text
/// { namespace: {
///     baseUrl: "...",
///     version: "...",
///     catalogs: [ ... ],
///     data-schema: SomeSchema
/// }}
/// ```
#[derive(Debug, Clone, PartialEq)]
pub struct JsonTNamespace {
    /// The base URL identifying this namespace (a string literal in the source).
    pub base_url: String,

    /// The version string for this namespace.
    pub version: String,

    /// One or more catalog blocks, each grouping related schemas and enums.
    pub catalogs: Vec<JsonTCatalog>,

    /// The name of the root schema used when parsing data rows.
    /// Corresponds to `data-schema: SomeSchema` in the namespace block.
    pub data_schema: String,
}

/// A catalog groups a set of schema definitions and an optional set of enum definitions.
///
/// Corresponds to the grammar rule:
/// ```text
/// { schemas: [ ... ], enums: [ ... ] }
/// ```
#[derive(Debug, Clone, PartialEq)]
pub struct JsonTCatalog {
    /// All schema entries declared in this catalog's `schemas` section.
    pub schemas: Vec<JsonTSchema>,

    /// All enum definitions declared in this catalog's `enums` section.
    /// The section is optional in the grammar; defaults to an empty vec.
    pub enums: Vec<JsonTEnum>,
}
