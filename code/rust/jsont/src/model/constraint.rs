// =============================================================================
// model/constraint.rs
// =============================================================================
// JsonTConstraint — sum type covering all constraint forms in the grammar
// Key enums       — the specific keyword variants for each constraint family
// =============================================================================

/// All constraint types that can appear on a field declaration.
///
/// Scalar constraints: ValueConstraint, LengthConstraint, RegexConstraint, Required, ArrayItems
/// Object constraints: Required, ArrayItems
///
/// Constraint–type compatibility is checked at build/parse time (option B):
/// invalid constraints are accumulated and reported together at build().
#[derive(Debug, Clone, PartialEq)]
pub enum JsonTConstraint {
    /// `required = true | false`
    Required(bool),

    /// A numeric value range or precision bound.
    /// e.g. `(minValue = 0, maxValue = 100)`
    Value {
        key: ValueConstraintKey,
        value: f64,
    },

    /// A string or binary length bound.
    /// e.g. `(minLength = 1, maxLength = 255)`
    Length {
        key: LengthConstraintKey,
        value: u64,
    },

    /// Array item count or null-item policy.
    ArrayItems(ArrayItemsConstraint),

    /// A regex pattern the string value must match.
    /// Both `regex` and `pattern` keywords map to this variant.
    Regex(String),
}

/// Keyword variants for value (numeric) constraints.
#[derive(Debug, Clone, PartialEq, Eq)]
pub enum ValueConstraintKey {
    MinValue,
    MaxValue,
    MinPrecision,
    MaxPrecision,
}

impl ValueConstraintKey {
    pub fn keyword(&self) -> &'static str {
        match self {
            ValueConstraintKey::MinValue      => "minValue",
            ValueConstraintKey::MaxValue      => "maxValue",
            ValueConstraintKey::MinPrecision  => "minPrecision",
            ValueConstraintKey::MaxPrecision  => "maxPrecision",
        }
    }

    pub fn from_keyword(kw: &str) -> Option<Self> {
        match kw {
            "minValue"     => Some(ValueConstraintKey::MinValue),
            "maxValue"     => Some(ValueConstraintKey::MaxValue),
            "minPrecision" => Some(ValueConstraintKey::MinPrecision),
            "maxPrecision" => Some(ValueConstraintKey::MaxPrecision),
            _              => None,
        }
    }
}

/// Keyword variants for length (string/binary) constraints.
#[derive(Debug, Clone, PartialEq, Eq)]
pub enum LengthConstraintKey {
    MinLength,
    MaxLength,
}

impl LengthConstraintKey {
    pub fn keyword(&self) -> &'static str {
        match self {
            LengthConstraintKey::MinLength => "minLength",
            LengthConstraintKey::MaxLength => "maxLength",
        }
    }

    pub fn from_keyword(kw: &str) -> Option<Self> {
        match kw {
            "minLength" => Some(LengthConstraintKey::MinLength),
            "maxLength" => Some(LengthConstraintKey::MaxLength),
            _           => None,
        }
    }
}

/// Keyword variants for array item count constraints (numeric).
#[derive(Debug, Clone, PartialEq, Eq)]
pub enum ArrayConstraintNbr {
    MinItems,
    MaxItems,
    MaxNullItems,
}

impl ArrayConstraintNbr {
    pub fn keyword(&self) -> &'static str {
        match self {
            ArrayConstraintNbr::MinItems     => "minItems",
            ArrayConstraintNbr::MaxItems     => "maxItems",
            ArrayConstraintNbr::MaxNullItems => "maxNullItems",
        }
    }

    pub fn from_keyword(kw: &str) -> Option<Self> {
        match kw {
            "minItems"     => Some(ArrayConstraintNbr::MinItems),
            "maxItems"     => Some(ArrayConstraintNbr::MaxItems),
            "maxNullItems" => Some(ArrayConstraintNbr::MaxNullItems),
            _              => None,
        }
    }
}

/// Keyword variant for the boolean array item policy constraint.
#[derive(Debug, Clone, PartialEq, Eq)]
pub enum ArrayConstraintBool {
    AllowNullItems,
}

impl ArrayConstraintBool {
    pub fn keyword(&self) -> &'static str {
        "allowNullItems"
    }
}

/// A single option inside an array items constraint block.
/// The grammar allows mixing numeric and boolean options in one block.
#[derive(Debug, Clone, PartialEq)]
pub enum ArrayItemsConstraint {
    Numeric { key: ArrayConstraintNbr, value: u64 },
    Boolean { key: ArrayConstraintBool, value: bool },
}
