// =============================================================================
// model/data.rs
// =============================================================================
// JsonTValue   — the universal value type (scalar, object, array, null, etc.)
// JsonTString  — the universal string type, covering all semantic variants
// JsonTNumber  — typed numeric value, one variant per numeric ScalarType
// JsonTRow     — an ordered sequence of values forming one data row
// JsonTArray   — an ordered sequence of values forming an array field value
// =============================================================================

use rust_decimal::Decimal;

use crate::model::field::ScalarType;
use crate::model::format;

/// The universal JsonT value — covers every value form in the grammar.
#[derive(Debug, Clone, PartialEq)]
pub enum JsonTValue {
    /// `null` or `nil` — an explicit absent value.
    Null,

    /// `_` — the "unspecified" sentinel; value is intentionally omitted.
    Unspecified,

    /// A typed numeric value, including integer temporal types.
    Number(JsonTNumber),

    /// A boolean literal (`true` / `false`).
    Bool(bool),

    /// A string value, possibly holding a semantic variant (email, uuid, etc.).
    Str(JsonTString),

    /// An enum constant value (a CONSTID — all uppercase, 2+ chars).
    Enum(String),

    /// A nested object value — positional fields matching the referenced schema.
    Object(JsonTRow),

    /// An array of values.
    Array(JsonTArray),

    /// A field value that is encrypted at rest.
    ///
    /// The inner bytes are an opaque envelope produced by [`CryptoConfig::encrypt`].
    /// The value flows through parse → validate → transform unchanged and is only
    /// decrypted on demand (via the `decrypt` pipeline operation or the on-demand
    /// `decrypt()` helper).
    Encrypted(Vec<u8>),
}

impl JsonTValue {
    // ── Convenience constructors ──────────────────────────────────────────

    pub fn null() -> Self { JsonTValue::Null }
    pub fn unspecified() -> Self { JsonTValue::Unspecified }
    pub fn bool(b: bool) -> Self { JsonTValue::Bool(b) }
    pub fn str(s: impl Into<String>) -> Self { JsonTValue::Str(JsonTString::Plain(s.into())) }
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

    pub fn nstr(s: impl Into<String>) -> Self { JsonTValue::Str(JsonTString::Nstr(s.into())) }
    pub fn uuid(s: impl Into<String>) -> Self { JsonTValue::Str(JsonTString::Uuid(s.into())) }
    pub fn uri(s: impl Into<String>) -> Self { JsonTValue::Str(JsonTString::Uri(s.into())) }
    pub fn email(s: impl Into<String>) -> Self { JsonTValue::Str(JsonTString::Email(s.into())) }
    pub fn hostname(s: impl Into<String>) -> Self { JsonTValue::Str(JsonTString::Hostname(s.into())) }
    pub fn ipv4(s: impl Into<String>) -> Self { JsonTValue::Str(JsonTString::Ipv4(s.into())) }
    pub fn ipv6(s: impl Into<String>) -> Self { JsonTValue::Str(JsonTString::Ipv6(s.into())) }
    pub fn date(s: impl Into<String>) -> Self { JsonTValue::Str(JsonTString::Date(s.into())) }
    pub fn time(s: impl Into<String>) -> Self { JsonTValue::Str(JsonTString::Time(s.into())) }
    pub fn date_time(s: impl Into<String>) -> Self { JsonTValue::Str(JsonTString::DateTime(s.into())) }
    pub fn timestamp(s: impl Into<String>) -> Self { JsonTValue::Str(JsonTString::Timestamp(s.into())) }
    pub fn tsz(s: impl Into<String>) -> Self { JsonTValue::Str(JsonTString::Tsz(s.into())) }
    pub fn inst(s: impl Into<String>) -> Self { JsonTValue::Str(JsonTString::Inst(s.into())) }
    pub fn duration(s: impl Into<String>) -> Self { JsonTValue::Str(JsonTString::Duration(s.into())) }
    pub fn base64(s: impl Into<String>) -> Self { JsonTValue::Str(JsonTString::Base64(s.into())) }
    pub fn hex(s: impl Into<String>) -> Self { JsonTValue::Str(JsonTString::Hex(s.into())) }
    pub fn oid(s: impl Into<String>) -> Self { JsonTValue::Str(JsonTString::Oid(s.into())) }

    // ── Numeric temporal constructors ─────────────────────────────────────

    pub fn encrypted(bytes: Vec<u8>) -> Self { JsonTValue::Encrypted(bytes) }

    pub fn date_int(n: u32) -> Self { JsonTValue::Number(JsonTNumber::Date(n)) }
    pub fn time_int(n: u32) -> Self { JsonTValue::Number(JsonTNumber::Time(n)) }
    pub fn datetime_int(n: u64) -> Self { JsonTValue::Number(JsonTNumber::DateTime(n)) }
    pub fn timestamp_int(n: i64) -> Self { JsonTValue::Number(JsonTNumber::Timestamp(n)) }

    // ── Type queries ──────────────────────────────────────────────────────

    pub fn is_null(&self) -> bool { matches!(self, JsonTValue::Null) }
    pub fn is_numeric(&self) -> bool { matches!(self, JsonTValue::Number(_)) }
    pub fn is_string(&self) -> bool { matches!(self, JsonTValue::Str(_)) }
    pub fn is_encrypted(&self) -> bool { matches!(self, JsonTValue::Encrypted(_)) }

    /// Returns the raw envelope bytes if this value is encrypted.
    pub fn as_encrypted(&self) -> Option<&[u8]> {
        match self { JsonTValue::Encrypted(b) => Some(b), _ => None }
    }

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

    /// Returns the inner JsonTString wrapper if this is a Str variant.
    pub fn as_json_string(&self) -> Option<&JsonTString> {
        match self {
            JsonTValue::Str(js) => Some(js),
            _ => None,
        }
    }

    /// Accesses the underlying characters for any string-based variant.
    /// Returns None for numbers, bools, etc.
    pub fn as_str(&self) -> Option<&str> {
        self.as_json_string().map(|js| js.as_raw_str())
    }

    /// Accesses the underlying bytes for any string-based variant.
    pub fn as_raw_bytes(&self) -> Option<&[u8]> {
        self.as_json_string().map(|js| js.as_raw_bytes())
    }

    /// Human-readable type name, used in error messages.
    pub fn type_name(&self) -> &'static str {
        match self {
            JsonTValue::Null        => "null",
            JsonTValue::Unspecified => "unspecified",
            JsonTValue::Number(n)   => n.type_name(),
            JsonTValue::Bool(_)     => "bool",
            JsonTValue::Str(js)     => js.type_name(),
            JsonTValue::Enum(_)     => "enum",
            JsonTValue::Object(_)    => "object",
            JsonTValue::Array(_)     => "array",
            JsonTValue::Encrypted(_) => "encrypted",
        }
    }

    /// Promotes a value to a more precise variant based on the declared `ScalarType`.
    /// Performs format validation. Returns Err if the value does not conform to the type.
    pub fn promote(self, scalar_type: &ScalarType) -> Result<Self, &'static str> {
        match self {
            JsonTValue::Str(js) => {
                // Only promote if it's currently a Plain string.
                if let JsonTString::Plain(s) = js {
                    JsonTString::promote(s, scalar_type).map(JsonTValue::Str)
                } else {
                    Ok(JsonTValue::Str(js))
                }
            }
            JsonTValue::Number(n) => {
                n.promote_temporal(scalar_type).map(JsonTValue::Number)
            }
            _ => Ok(self),
        }
    }
}

/// The universal string type, covering all semantic variants.
#[derive(Debug, Clone, PartialEq)]
pub enum JsonTString {
    Plain(String),
    Nstr(String),

    // ── Network subtypes ───────────
    Uuid(String),
    Uri(String),
    Email(String),
    Hostname(String),
    Ipv4(String),
    Ipv6(String),

    // ── Temporal subtypes (String form) ───────────
    Date(String),
    Time(String),
    DateTime(String),
    Timestamp(String),
    Tsz(String),
    Inst(String),
    Duration(String),

    // ── Binary / Encoded subtypes ───────────
    Base64(String),
    Hex(String),
    Oid(String),
}

impl From<String> for JsonTString {
    fn from(s: String) -> Self {
        JsonTString::Plain(s)
    }
}

impl From<&str> for JsonTString {
    fn from(s: &str) -> Self {
        JsonTString::Plain(s.to_string())
    }
}

impl JsonTString {
    /// Infallible access to the underlying string slice.
    pub fn as_raw_str(&self) -> &str {
        match self {
            JsonTString::Plain(s) | JsonTString::Nstr(s) | JsonTString::Uuid(s)
            | JsonTString::Uri(s) | JsonTString::Email(s) | JsonTString::Hostname(s)
            | JsonTString::Ipv4(s) | JsonTString::Ipv6(s) | JsonTString::Date(s)
            | JsonTString::Time(s) | JsonTString::DateTime(s) | JsonTString::Timestamp(s)
            | JsonTString::Tsz(s) | JsonTString::Inst(s) | JsonTString::Duration(s)
            | JsonTString::Base64(s) | JsonTString::Hex(s) | JsonTString::Oid(s) => s,
        }
    }

    /// Alias for as_raw_str to match test expectations.
    pub fn as_str(&self) -> &str {
        self.as_raw_str()
    }

    pub fn as_raw_bytes(&self) -> &[u8] {
        self.as_raw_str().as_bytes()
    }

    pub fn type_name(&self) -> &'static str {
        match self {
            JsonTString::Plain(_)     => "str",
            JsonTString::Nstr(_)      => "nstr",
            JsonTString::Uuid(_)      => "uuid",
            JsonTString::Uri(_)       => "uri",
            JsonTString::Email(_)     => "email",
            JsonTString::Hostname(_)  => "hostname",
            JsonTString::Ipv4(_)      => "ipv4",
            JsonTString::Ipv6(_)      => "ipv6",
            JsonTString::Date(_)      => "date",
            JsonTString::Time(_)      => "time",
            JsonTString::DateTime(_) => "datetime",
            JsonTString::Timestamp(_) => "timestamp",
            JsonTString::Tsz(_)       => "tsz",
            JsonTString::Inst(_)      => "inst",
            JsonTString::Duration(_) => "duration",
            JsonTString::Base64(_)    => "base64",
            JsonTString::Hex(_)       => "hex",
            JsonTString::Oid(_)       => "oid",
        }
    }

    // ── Typed accessors ───────────────────────────────────────────────────

    pub fn as_plain(&self) -> Option<&str> { match self { JsonTString::Plain(s) => Some(s), _ => None } }
    pub fn as_nstr(&self) -> Option<&str> { match self { JsonTString::Nstr(s) => Some(s), _ => None } }
    pub fn as_uuid(&self) -> Option<&str> { match self { JsonTString::Uuid(s) => Some(s), _ => None } }
    pub fn as_uri(&self) -> Option<&str> { match self { JsonTString::Uri(s) => Some(s), _ => None } }
    pub fn as_email(&self) -> Option<&str> { match self { JsonTString::Email(s) => Some(s), _ => None } }
    pub fn as_hostname(&self) -> Option<&str> { match self { JsonTString::Hostname(s) => Some(s), _ => None } }
    pub fn as_ipv4(&self) -> Option<&str> { match self { JsonTString::Ipv4(s) => Some(s), _ => None } }
    pub fn as_ipv6(&self) -> Option<&str> { match self { JsonTString::Ipv6(s) => Some(s), _ => None } }
    pub fn as_date(&self) -> Option<&str> { match self { JsonTString::Date(s) => Some(s), _ => None } }
    pub fn as_time(&self) -> Option<&str> { match self { JsonTString::Time(s) => Some(s), _ => None } }
    pub fn as_datetime(&self) -> Option<&str> { match self { JsonTString::DateTime(s) => Some(s), _ => None } }
    pub fn as_timestamp(&self) -> Option<&str> { match self { JsonTString::Timestamp(s) => Some(s), _ => None } }
    pub fn as_tsz(&self) -> Option<&str> { match self { JsonTString::Tsz(s) => Some(s), _ => None } }
    pub fn as_inst(&self) -> Option<&str> { match self { JsonTString::Inst(s) => Some(s), _ => None } }
    pub fn as_duration(&self) -> Option<&str> { match self { JsonTString::Duration(s) => Some(s), _ => None } }
    pub fn as_base64(&self) -> Option<&str> { match self { JsonTString::Base64(s) => Some(s), _ => None } }
    pub fn as_hex(&self) -> Option<&str> { match self { JsonTString::Hex(s) => Some(s), _ => None } }
    pub fn as_oid(&self) -> Option<&str> { match self { JsonTString::Oid(s) => Some(s), _ => None } }

    /// Internal factory to promote a string to a semantic variant.
    pub(crate) fn promote(s: String, ty: &ScalarType) -> Result<Self, &'static str> {
        match ty {
            ScalarType::Str       => Ok(JsonTString::Plain(s)),
            ScalarType::NStr      if format::is_nstr(&s)     => Ok(JsonTString::Nstr(s)),
            ScalarType::Uuid      if format::is_uuid(&s)     => Ok(JsonTString::Uuid(s)),
            ScalarType::Uri       if format::is_uri(&s)      => Ok(JsonTString::Uri(s)),
            ScalarType::Email     if format::is_email(&s)    => Ok(JsonTString::Email(s)),
            ScalarType::Hostname  if format::is_hostname(&s) => Ok(JsonTString::Hostname(s)),
            ScalarType::Ipv4      if format::is_ipv4(&s)     => Ok(JsonTString::Ipv4(s)),
            ScalarType::Ipv6      if format::is_ipv6(&s)     => Ok(JsonTString::Ipv6(s)),
            ScalarType::Date      if format::is_date(&s)     => Ok(JsonTString::Date(s)),
            ScalarType::Time      if format::is_time(&s)     => Ok(JsonTString::Time(s)),
            ScalarType::DateTime  if format::is_date_time(&s) => Ok(JsonTString::DateTime(s)),
            ScalarType::Timestamp if format::is_timestamp(&s) => Ok(JsonTString::Timestamp(s)),
            ScalarType::Tsz       if format::is_tsz(&s)      => Ok(JsonTString::Tsz(s)),
            ScalarType::Inst      if format::is_inst(&s)     => Ok(JsonTString::Inst(s)),
            ScalarType::Duration  if format::is_duration(&s) => Ok(JsonTString::Duration(s)),
            ScalarType::Base64    if format::is_base64(&s)   => Ok(JsonTString::Base64(s)),
            ScalarType::Hex       if format::is_hex(&s)      => Ok(JsonTString::Hex(s)),
            ScalarType::Oid       if format::is_oid(&s)      => Ok(JsonTString::Oid(s)),
            
            _ => Err("format violation"),
        }
    }
}

/// A typed numeric value.
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

    // ── Integer temporal variants ──────────
    Date(u32),
    Time(u32),
    DateTime(u64),
    Timestamp(i64),
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
            JsonTNumber::Date(n) => *n as f64,
            JsonTNumber::Time(n) => *n as f64,
            JsonTNumber::DateTime(n) => *n as f64,
            JsonTNumber::Timestamp(n) => *n as f64,
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
            JsonTNumber::Date(_) => "date",
            JsonTNumber::Time(_) => "time",
            JsonTNumber::DateTime(_) => "datetime",
            JsonTNumber::Timestamp(_) => "timestamp",
        }
    }

    pub fn as_date(&self) -> Option<u32> { match self { JsonTNumber::Date(n) => Some(*n), _ => None } }
    pub fn as_time(&self) -> Option<u32> { match self { JsonTNumber::Time(n) => Some(*n), _ => None } }
    pub fn as_datetime(&self) -> Option<u64> { match self { JsonTNumber::DateTime(n) => Some(*n), _ => None } }
    pub fn as_timestamp(&self) -> Option<i64> { match self { JsonTNumber::Timestamp(n) => Some(*n), _ => None } }

    /// Promotes a raw integer to a temporal variant.
    pub(crate) fn promote_temporal(self, ty: &ScalarType) -> Result<Self, &'static str> {
        match (self, ty) {
            (JsonTNumber::Date(n), ScalarType::Date) => Ok(JsonTNumber::Date(n)),
            (JsonTNumber::Time(n), ScalarType::Time) => Ok(JsonTNumber::Time(n)),
            (JsonTNumber::DateTime(n), ScalarType::DateTime) => Ok(JsonTNumber::DateTime(n)),
            (JsonTNumber::Timestamp(n), ScalarType::Timestamp) => Ok(JsonTNumber::Timestamp(n)),

            (n, ScalarType::Date) => {
                let val = match n {
                    JsonTNumber::I16(v) => v as u32,
                    JsonTNumber::I32(v) => v as u32,
                    JsonTNumber::I64(v) => v as u32,
                    JsonTNumber::U16(v) => v as u32,
                    JsonTNumber::U32(v) => v,
                    JsonTNumber::U64(v) => v as u32,
                    JsonTNumber::D32(v) => v as u32,
                    JsonTNumber::D64(v) => v as u32,
                    JsonTNumber::D128(v) => {
                        use rust_decimal::prelude::ToPrimitive;
                        v.to_u32().ok_or("decimal out of range for date")?
                    }
                    _ => return Err("non-integer for date"),
                };
                if !format::is_date_int(val) { return Err("invalid date format YYYYMMDD"); }
                Ok(JsonTNumber::Date(val))
            }
            (n, ScalarType::Time) => {
                let val = match n {
                    JsonTNumber::I16(v) => v as u32,
                    JsonTNumber::I32(v) => v as u32,
                    JsonTNumber::I64(v) => v as u32,
                    JsonTNumber::U16(v) => v as u32,
                    JsonTNumber::U32(v) => v,
                    JsonTNumber::U64(v) => v as u32,
                    JsonTNumber::D32(v) => v as u32,
                    JsonTNumber::D64(v) => v as u32,
                    JsonTNumber::D128(v) => {
                        use rust_decimal::prelude::ToPrimitive;
                        v.to_u32().ok_or("decimal out of range for time")?
                    }
                    _ => return Err("non-integer for time"),
                };
                if !format::is_time_int(val) { return Err("invalid time format HHmmss"); }
                Ok(JsonTNumber::Time(val))
            }
            (n, ScalarType::DateTime) => {
                let val = match n {
                    JsonTNumber::I16(v) => v as u64,
                    JsonTNumber::I32(v) => v as u64,
                    JsonTNumber::I64(v) => v as u64,
                    JsonTNumber::U16(v) => v as u64,
                    JsonTNumber::U32(v) => v as u64,
                    JsonTNumber::U64(v) => v,
                    JsonTNumber::D32(v) => v as u64,
                    JsonTNumber::D64(v) => v as u64,
                    JsonTNumber::D128(v) => {
                        use rust_decimal::prelude::ToPrimitive;
                        v.to_u64().ok_or("decimal out of range for datetime")?
                    }
                    _ => return Err("non-integer for datetime"),
                };
                if !format::is_date_time_int(val) { return Err("invalid datetime format YYYYMMDDHHmmss"); }
                Ok(JsonTNumber::DateTime(val))
            }
            (n, ScalarType::Timestamp) => {
                let val = match n {
                    JsonTNumber::I16(v) => v as i64,
                    JsonTNumber::I32(v) => v as i64,
                    JsonTNumber::I64(v) => v,
                    JsonTNumber::U16(v) => v as i64,
                    JsonTNumber::U32(v) => v as i64,
                    JsonTNumber::U64(v) => v as i64,
                    JsonTNumber::D32(v) => v as i64,
                    JsonTNumber::D64(v) => v as i64,
                    JsonTNumber::D128(v) => {
                        use rust_decimal::prelude::ToPrimitive;
                        v.to_i64().ok_or("decimal out of range for timestamp")?
                    }
                    _ => return Err("non-integer for timestamp"),
                };
                Ok(JsonTNumber::Timestamp(val))
            }
            (n, _) => Ok(n),
        }
    }
}

/// A single data row — an ordered sequence of values forming one data row.
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

/// An ordered sequence of values forming an array field value.
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
