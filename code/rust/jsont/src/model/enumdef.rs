// =============================================================================
// model/enumdef.rs
// =============================================================================
// JsonTEnum — an enum definition inside an enums section of a catalog
// =============================================================================

/// An enum definition within a catalog's `enums` section.
///
/// Grammar:
/// ```text
/// EnumName: [ VALUE_ONE, VALUE_TWO, ... ]
/// ```
///
/// Enum names are SCHEMAIDs (start uppercase, mixed case body).
/// Enum values are CONSTIDs (all uppercase, 2+ chars).
#[derive(Debug, Clone, PartialEq)]
pub struct JsonTEnum {
    /// The enum name — a SCHEMAID (e.g. `Status`, `Color`).
    pub name: String,

    /// The allowed constant values — each is a CONSTID (e.g. `ACTIVE`, `RED`).
    pub values: Vec<String>,
}
