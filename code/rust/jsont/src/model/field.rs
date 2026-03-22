// =============================================================================
// model/field.rs
// =============================================================================
// JsonTField      — a single field declaration inside a schema
// JsonTFieldKind  — scalar vs object field, carrying their distinct properties
// JsonTFieldType  — the concrete scalar type (maps 1:1 to grammar type keywords)
// ScalarType      — the type keyword itself, separate from array-ness
// =============================================================================

use crate::model::constraint::JsonTConstraint;
use crate::model::data::JsonTValue;

/// A single field declaration within a schema's `fields` block.
#[derive(Debug, Clone, PartialEq)]
pub struct JsonTField {
    /// The field name (FIELDID — starts with a lowercase letter).
    pub name: String,

    /// The kind and type-specific properties of this field.
    pub kind: JsonTFieldKind,
}

/// Discriminates scalar fields (directly typed) from object fields
/// (typed by reference to another schema).
#[derive(Debug, Clone, PartialEq)]
pub enum JsonTFieldKind {
    Scalar {
        /// The concrete scalar type, including whether it is an array.
        field_type: JsonTFieldType,

        /// Whether the field may be absent / null in a data row.
        optional: bool,

        /// An optional default value used when the field is absent.
        /// Must be compatible with `field_type`.
        default: Option<JsonTValue>,

        /// A compile-time constant value for this field (cannot be overridden
        /// in data rows).
        constant: Option<JsonTValue>,

        /// Zero or more constraints narrowing the valid value range.
        /// Stored in declaration order; validated for type compatibility at
        /// build/parse time.
        constraints: Vec<JsonTConstraint>,
    },

    Object {
        /// The name of the schema this field's value must conform to.
        schema_ref: String,

        /// Whether this field holds a single object or an array of objects.
        is_array: bool,

        /// Whether the field may be absent / null in a data row.
        optional: bool,

        /// Constraints applicable to object fields (required, array item counts).
        constraints: Vec<JsonTConstraint>,
    },
}

/// The resolved type of a scalar field, combining the base type keyword
/// with array-ness.
#[derive(Debug, Clone, PartialEq)]
pub struct JsonTFieldType {
    pub scalar: ScalarType,
    /// True when the field declaration has a `[]` suffix (i.e. is an array).
    pub is_array: bool,
}

impl JsonTFieldType {
    pub fn new(scalar: ScalarType, is_array: bool) -> Self {
        Self { scalar, is_array }
    }

    pub fn simple(scalar: ScalarType) -> Self {
        Self { scalar, is_array: false }
    }
}

/// All scalar type keywords supported by JsonT.
///
/// Variants map 1:1 to the grammar's type keyword tokens.
/// The Rust storage type in JsonTNumber (and JsonTValue) is chosen to match
/// the intended memory footprint for each variant.
#[derive(Debug, Clone, PartialEq, Eq, Hash)]
pub enum ScalarType {
    // ── Numeric ────────────────────────────────────────────────────────────
    /// 16-bit signed integer  → Rust i16
    I16,
    /// 32-bit signed integer  → Rust i32
    I32,
    /// 64-bit signed integer  → Rust i64
    I64,
    /// 16-bit unsigned integer → Rust u16
    U16,
    /// 32-bit unsigned integer → Rust u32
    U32,
    /// 64-bit unsigned integer → Rust u64
    U64,
    /// 32-bit decimal (single-precision float) → Rust f32
    D32,
    /// 64-bit decimal (double-precision float) → Rust f64
    D64,
    /// 128-bit decimal → rust_decimal::Decimal (no native f128 on stable Rust)
    D128,

    // ── Boolean ────────────────────────────────────────────────────────────
    Bool,

    // ── String-like ────────────────────────────────────────────────────────
    Str,
    /// Normalised (unicode NFC) string
    NStr,
    Uri,
    Uuid,
    Email,
    Hostname,
    Ipv4,
    Ipv6,

    // ── Temporal ───────────────────────────────────────────────────────────
    Date,
    Time,
    DateTime,
    Timestamp,
    /// Timestamp with timezone
    Tsz,
    Duration,
    /// Instant (monotonic point in time)
    Inst,

    // ── Binary ─────────────────────────────────────────────────────────────
    Base64,
    Oid,
    Hex,
}

impl ScalarType {
    /// Return the JsonT keyword string for this type.
    pub fn keyword(&self) -> &'static str {
        match self {
            ScalarType::I16       => "i16",
            ScalarType::I32       => "i32",
            ScalarType::I64       => "i64",
            ScalarType::U16       => "u16",
            ScalarType::U32       => "u32",
            ScalarType::U64       => "u64",
            ScalarType::D32       => "d32",
            ScalarType::D64       => "d64",
            ScalarType::D128      => "d128",
            ScalarType::Bool      => "bool",
            ScalarType::Str       => "str",
            ScalarType::NStr      => "nstr",
            ScalarType::Uri       => "uri",
            ScalarType::Uuid      => "uuid",
            ScalarType::Email     => "email",
            ScalarType::Hostname  => "hostname",
            ScalarType::Ipv4      => "ipv4",
            ScalarType::Ipv6      => "ipv6",
            ScalarType::Date      => "date",
            ScalarType::Time      => "time",
            ScalarType::DateTime  => "datetime",
            ScalarType::Timestamp => "timestamp",
            ScalarType::Tsz       => "tsz",
            ScalarType::Duration  => "duration",
            ScalarType::Inst      => "inst",
            ScalarType::Base64    => "base64",
            ScalarType::Oid       => "oid",
            ScalarType::Hex       => "hex",
        }
    }

    pub fn from_keyword(kw: &str) -> Option<Self> {
        match kw {
            "i16"       => Some(ScalarType::I16),
            "i32"       => Some(ScalarType::I32),
            "i64"       => Some(ScalarType::I64),
            "u16"       => Some(ScalarType::U16),
            "u32"       => Some(ScalarType::U32),
            "u64"       => Some(ScalarType::U64),
            "d32"       => Some(ScalarType::D32),
            "d64"       => Some(ScalarType::D64),
            "d128"      => Some(ScalarType::D128),
            "bool"      => Some(ScalarType::Bool),
            "str"       => Some(ScalarType::Str),
            "nstr"      => Some(ScalarType::NStr),
            "uri"       => Some(ScalarType::Uri),
            "uuid"      => Some(ScalarType::Uuid),
            "email"     => Some(ScalarType::Email),
            "hostname"  => Some(ScalarType::Hostname),
            "ipv4"      => Some(ScalarType::Ipv4),
            "ipv6"      => Some(ScalarType::Ipv6),
            "date"      => Some(ScalarType::Date),
            "time"      => Some(ScalarType::Time),
            "datetime"  => Some(ScalarType::DateTime),
            "timestamp" => Some(ScalarType::Timestamp),
            "tsz"       => Some(ScalarType::Tsz),
            "duration"  => Some(ScalarType::Duration),
            "inst"      => Some(ScalarType::Inst),
            "base64"    => Some(ScalarType::Base64),
            "oid"       => Some(ScalarType::Oid),
            "hex"       => Some(ScalarType::Hex),
            _           => None,
        }
    }

    /// True for types where value constraints (min/maxValue, min/maxPrecision)
    /// are semantically valid.
    pub fn supports_value_constraints(&self) -> bool {
        matches!(self,
            ScalarType::I16 | ScalarType::I32 | ScalarType::I64
            | ScalarType::U16 | ScalarType::U32 | ScalarType::U64
            | ScalarType::D32 | ScalarType::D64 | ScalarType::D128
        )
    }

    /// True for types where length constraints (min/maxLength) are valid.
    pub fn supports_length_constraints(&self) -> bool {
        matches!(self,
            ScalarType::Str | ScalarType::NStr
            | ScalarType::Uri | ScalarType::Uuid | ScalarType::Email
            | ScalarType::Hostname | ScalarType::Ipv4 | ScalarType::Ipv6
            | ScalarType::Base64 | ScalarType::Hex | ScalarType::Oid
        )
    }

    /// True for types where regex/pattern constraints are valid.
    pub fn supports_regex_constraints(&self) -> bool {
        self.supports_length_constraints() // same set
    }
}
