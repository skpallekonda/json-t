// =============================================================================
// model/data.rs
// =============================================================================
// JsonTValue   — the universal value type (scalar, object, array, null, etc.)
// JsonTNumber  — typed numeric value, one variant per numeric ScalarType
// JsonTRow     — an ordered sequence of values forming one data row
// JsonTArray   — an ordered sequence of values forming an array field value
// =============================================================================

use rust_decimal::Decimal;

use crate::model::field::ScalarType;

/// The universal JsonT value — covers every value form in the grammar.
///
/// Variants map to the grammar's `value` rule alternatives.
/// The semantic string variants (Email, Uuid, etc.) are identical on the wire
/// to `Str` — they are promoted from `Str` in the validation layer once the
/// declared `ScalarType` is known.
#[derive(Debug, Clone, PartialEq)]
pub enum JsonTValue {
    /// `null` or `nil` — an explicit absent value.
    Null,

    /// `_` — the "unspecified" sentinel; value is intentionally omitted.
    Unspecified,

    /// A typed numeric value. The variant inside JsonTNumber determines the
    /// in-memory representation, matching the declared field type.
    Number(JsonTNumber),

    /// A boolean literal (`true` / `false`).
    Bool(bool),

    /// A plain string value (single or double-quoted in source).
    Str(String),

    // ── Semantic string variants ──────────────────────────────────────────────
    // All of these serialise as double-quoted strings, identical to Str.
    // They are promoted from Str during validation when the ScalarType is known.

    /// Normalised (unicode NFC) string — maps to `ScalarType::NStr`.
    Nstr(String),
    /// UUID — maps to `ScalarType::Uuid`.
    Uuid(String),
    /// URI — maps to `ScalarType::Uri`.
    Uri(String),
    /// Email address — maps to `ScalarType::Email`.
    Email(String),
    /// Hostname — maps to `ScalarType::Hostname`.
    Hostname(String),
    /// IPv4 address — maps to `ScalarType::Ipv4`.
    Ipv4(String),
    /// IPv6 address — maps to `ScalarType::Ipv6`.
    Ipv6(String),

    // ── Temporal variants ─────────────────────────────────────────────────────

    /// Calendar date — maps to `ScalarType::Date`.
    Date(String),
    /// Time-of-day — maps to `ScalarType::Time`.
    Time(String),
    /// Date and time without timezone — maps to `ScalarType::DateTime`.
    DateTime(String),
    /// Unix timestamp (integer or decimal string) — maps to `ScalarType::Timestamp`.
    Timestamp(String),
    /// Timestamp with timezone — maps to `ScalarType::Tsz`.
    Tsz(String),
    /// Monotonic instant — maps to `ScalarType::Inst`.
    Inst(String),
    /// ISO 8601 duration — maps to `ScalarType::Duration`.
    Duration(String),

    // ── Binary / identifier variants ──────────────────────────────────────────

    /// Base64-encoded binary — maps to `ScalarType::Base64`.
    Base64(String),
    /// Hex-encoded binary — maps to `ScalarType::Hex`.
    Hex(String),
    /// Object identifier — maps to `ScalarType::Oid`.
    Oid(String),

    /// An enum constant value (a CONSTID — all uppercase, 2+ chars).
    Enum(String),

    /// A nested object value — positional fields matching the referenced schema.
    Object(JsonTRow),

    /// An array of values.
    Array(JsonTArray),
}

impl JsonTValue {
    // ── Convenience constructors ──────────────────────────────────────────

    pub fn null() -> Self { JsonTValue::Null }
    pub fn unspecified() -> Self { JsonTValue::Unspecified }
    pub fn bool(b: bool) -> Self { JsonTValue::Bool(b) }
    pub fn str(s: impl Into<String>) -> Self { JsonTValue::Str(s.into()) }
    pub fn enum_val(c: impl Into<String>) -> Self { JsonTValue::Enum(c.into()) }

    pub fn i16(n: i16)  -> Self { JsonTValue::Number(JsonTNumber::I16(n)) }
    pub fn i32(n: i32)  -> Self { JsonTValue::Number(JsonTNumber::I32(n)) }
    pub fn i64(n: i64)  -> Self { JsonTValue::Number(JsonTNumber::I64(n)) }
    pub fn u16(n: u16)  -> Self { JsonTValue::Number(JsonTNumber::U16(n)) }
    pub fn u32(n: u32)  -> Self { JsonTValue::Number(JsonTNumber::U32(n)) }
    pub fn u64(n: u64)  -> Self { JsonTValue::Number(JsonTNumber::U64(n)) }
    pub fn d32(n: f32)  -> Self { JsonTValue::Number(JsonTNumber::D32(n)) }
    pub fn d64(n: f64)  -> Self { JsonTValue::Number(JsonTNumber::D64(n)) }
    pub fn d128(n: Decimal) -> Self { JsonTValue::Number(JsonTNumber::D128(n)) }

    // ── Semantic string constructors ──────────────────────────────────────

    pub fn nstr(s: impl Into<String>) -> Self { JsonTValue::Nstr(s.into()) }
    pub fn uuid(s: impl Into<String>) -> Self { JsonTValue::Uuid(s.into()) }
    pub fn uri(s: impl Into<String>) -> Self { JsonTValue::Uri(s.into()) }
    pub fn email(s: impl Into<String>) -> Self { JsonTValue::Email(s.into()) }
    pub fn hostname(s: impl Into<String>) -> Self { JsonTValue::Hostname(s.into()) }
    pub fn ipv4(s: impl Into<String>) -> Self { JsonTValue::Ipv4(s.into()) }
    pub fn ipv6(s: impl Into<String>) -> Self { JsonTValue::Ipv6(s.into()) }
    pub fn date(s: impl Into<String>) -> Self { JsonTValue::Date(s.into()) }
    pub fn time(s: impl Into<String>) -> Self { JsonTValue::Time(s.into()) }
    pub fn date_time(s: impl Into<String>) -> Self { JsonTValue::DateTime(s.into()) }
    pub fn timestamp(s: impl Into<String>) -> Self { JsonTValue::Timestamp(s.into()) }
    pub fn tsz(s: impl Into<String>) -> Self { JsonTValue::Tsz(s.into()) }
    pub fn inst(s: impl Into<String>) -> Self { JsonTValue::Inst(s.into()) }
    pub fn duration(s: impl Into<String>) -> Self { JsonTValue::Duration(s.into()) }
    pub fn base64(s: impl Into<String>) -> Self { JsonTValue::Base64(s.into()) }
    pub fn hex(s: impl Into<String>) -> Self { JsonTValue::Hex(s.into()) }
    pub fn oid(s: impl Into<String>) -> Self { JsonTValue::Oid(s.into()) }

    // ── Type queries ──────────────────────────────────────────────────────

    pub fn is_null(&self) -> bool {
        matches!(self, JsonTValue::Null)
    }

    pub fn is_numeric(&self) -> bool {
        matches!(self, JsonTValue::Number(_))
    }

    pub fn is_string(&self) -> bool {
        matches!(self, JsonTValue::Str(_))
    }

    /// Coerce any numeric variant to f64 for expression arithmetic.
    /// Precision loss is acceptable here — arithmetic in expressions operates
    /// on runtime values, not stored data.
    pub fn as_f64(&self) -> Option<f64> {
        match self {
            JsonTValue::Number(n) => Some(n.as_f64()),
            _ => None,
        }
    }

    pub fn as_bool(&self) -> Option<bool> {
        match self {
            JsonTValue::Bool(b) => Some(*b),
            _ => None,
        }
    }

    pub fn as_str(&self) -> Option<&str> {
        match self {
            JsonTValue::Str(s)
            | JsonTValue::Nstr(s) | JsonTValue::Uuid(s) | JsonTValue::Uri(s)
            | JsonTValue::Email(s) | JsonTValue::Hostname(s)
            | JsonTValue::Ipv4(s) | JsonTValue::Ipv6(s)
            | JsonTValue::Date(s) | JsonTValue::Time(s) | JsonTValue::DateTime(s)
            | JsonTValue::Timestamp(s) | JsonTValue::Tsz(s) | JsonTValue::Inst(s)
            | JsonTValue::Duration(s) | JsonTValue::Base64(s) | JsonTValue::Hex(s)
            | JsonTValue::Oid(s) => Some(s.as_str()),
            _ => None,
        }
    }

    /// Human-readable type name, used in error messages.
    pub fn type_name(&self) -> &'static str {
        match self {
            JsonTValue::Null        => "null",
            JsonTValue::Unspecified => "unspecified",
            JsonTValue::Number(n)   => n.type_name(),
            JsonTValue::Bool(_)     => "bool",
            JsonTValue::Str(_)      => "str",
            JsonTValue::Nstr(_)     => "nstr",
            JsonTValue::Uuid(_)     => "uuid",
            JsonTValue::Uri(_)      => "uri",
            JsonTValue::Email(_)    => "email",
            JsonTValue::Hostname(_) => "hostname",
            JsonTValue::Ipv4(_)     => "ipv4",
            JsonTValue::Ipv6(_)     => "ipv6",
            JsonTValue::Date(_)     => "date",
            JsonTValue::Time(_)     => "time",
            JsonTValue::DateTime(_) => "datetime",
            JsonTValue::Timestamp(_) => "timestamp",
            JsonTValue::Tsz(_)      => "tsz",
            JsonTValue::Inst(_)     => "inst",
            JsonTValue::Duration(_) => "duration",
            JsonTValue::Base64(_)   => "base64",
            JsonTValue::Hex(_)      => "hex",
            JsonTValue::Oid(_)      => "oid",
            JsonTValue::Enum(_)     => "enum",
            JsonTValue::Object(_)   => "object",
            JsonTValue::Array(_)    => "array",
        }
    }

    /// Promotes a `Str` value to the appropriate semantic variant for the given `ScalarType`.
    ///
    /// Returns the value unchanged if it is not `Str`, or if the type maps to plain `Str`.
    /// This is called in the validation layer after type/constraint checking passes so
    /// that callers of `on_clean` always receive the semantically precise variant.
    pub fn promote(self, scalar_type: &ScalarType) -> Self {
        let JsonTValue::Str(s) = self else { return self; };
        match scalar_type {
            ScalarType::NStr      => JsonTValue::Nstr(s),
            ScalarType::Uuid      => JsonTValue::Uuid(s),
            ScalarType::Uri       => JsonTValue::Uri(s),
            ScalarType::Email     => JsonTValue::Email(s),
            ScalarType::Hostname  => JsonTValue::Hostname(s),
            ScalarType::Ipv4      => JsonTValue::Ipv4(s),
            ScalarType::Ipv6      => JsonTValue::Ipv6(s),
            ScalarType::Date      => JsonTValue::Date(s),
            ScalarType::Time      => JsonTValue::Time(s),
            ScalarType::DateTime  => JsonTValue::DateTime(s),
            ScalarType::Timestamp => JsonTValue::Timestamp(s),
            ScalarType::Tsz       => JsonTValue::Tsz(s),
            ScalarType::Inst      => JsonTValue::Inst(s),
            ScalarType::Duration  => JsonTValue::Duration(s),
            ScalarType::Base64    => JsonTValue::Base64(s),
            ScalarType::Hex       => JsonTValue::Hex(s),
            ScalarType::Oid       => JsonTValue::Oid(s),
            _                     => JsonTValue::Str(s),  // Str stays Str
        }
    }
}

/// A typed numeric value.
///
/// Each variant carries the Rust native type matching the JsonT type keyword's
/// intended memory footprint:
///
/// | JsonT | Rust            |
/// |-------|-----------------|
/// | i16   | i16             |
/// | i32   | i32             |
/// | i64   | i64             |
/// | u16   | u16             |
/// | u32   | u32             |
/// | u64   | u64             |
/// | d32   | f32             |
/// | d64   | f64             |
/// | d128  | Decimal (96-bit)|
///
/// Note: `d128` uses `rust_decimal::Decimal` (96-bit mantissa) because Rust
/// stable has no `f128`. This covers financial/high-precision use cases.
/// Swap to `f128` when it stabilises without changing the enum shape.
#[derive(Debug, Clone, PartialEq)]
pub enum JsonTNumber {
    I16(i16),
    I32(i32),
    I64(i64),
    U16(u16),
    U32(u32),
    U64(u64),
    D32(f32),
    D64(f64),
    D128(Decimal),
}

impl JsonTNumber {
    /// Coerce to f64 for expression arithmetic (precision loss acceptable).
    pub fn as_f64(&self) -> f64 {
        match self {
            JsonTNumber::I16(n)  => *n as f64,
            JsonTNumber::I32(n)  => *n as f64,
            JsonTNumber::I64(n)  => *n as f64,
            JsonTNumber::U16(n)  => *n as f64,
            JsonTNumber::U32(n)  => *n as f64,
            JsonTNumber::U64(n)  => *n as f64,
            JsonTNumber::D32(n)  => *n as f64,
            JsonTNumber::D64(n)  => *n,
            JsonTNumber::D128(n) => n.to_string().parse::<f64>().unwrap_or(f64::NAN),
        }
    }

    pub fn type_name(&self) -> &'static str {
        match self {
            JsonTNumber::I16(_)  => "i16",
            JsonTNumber::I32(_)  => "i32",
            JsonTNumber::I64(_)  => "i64",
            JsonTNumber::U16(_)  => "u16",
            JsonTNumber::U32(_)  => "u32",
            JsonTNumber::U64(_)  => "u64",
            JsonTNumber::D32(_)  => "d32",
            JsonTNumber::D64(_)  => "d64",
            JsonTNumber::D128(_) => "d128",
        }
    }
}

/// A single data row — an ordered sequence of values corresponding to the
/// fields of the row's schema (positional, not named at the data layer).
#[derive(Debug, Clone, PartialEq)]
pub struct JsonTRow {
    pub fields: Vec<JsonTValue>,
}

impl JsonTRow {
    pub fn new(fields: Vec<JsonTValue>) -> Self {
        Self { fields }
    }

    pub fn empty() -> Self {
        Self { fields: Vec::new() }
    }

    pub fn len(&self) -> usize {
        self.fields.len()
    }

    pub fn is_empty(&self) -> bool {
        self.fields.is_empty()
    }

    pub fn get(&self, index: usize) -> Option<&JsonTValue> {
        self.fields.get(index)
    }
}

/// An ordered sequence of values forming an array-typed field value.
#[derive(Debug, Clone, PartialEq)]
pub struct JsonTArray {
    pub items: Vec<JsonTValue>,
}

impl JsonTArray {
    pub fn new(items: Vec<JsonTValue>) -> Self {
        Self { items }
    }

    pub fn empty() -> Self {
        Self { items: Vec::new() }
    }

    pub fn len(&self) -> usize {
        self.items.len()
    }

    pub fn is_empty(&self) -> bool {
        self.items.is_empty()
    }

    pub fn get(&self, index: usize) -> Option<&JsonTValue> {
        self.items.get(index)
    }
}
