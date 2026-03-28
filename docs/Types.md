# JsonT Scalar Types

JsonT supports a rich set of built-in scalar types, each identified by a keyword in the schema DSL. The type keyword determines both the wire representation expected in data rows and the validation rules applied.

---

## Numeric Types

| Keyword | Description | Range / Precision |
|---|---|---|
| `i16` | 16-bit signed integer | −32 768 to 32 767 |
| `i32` | 32-bit signed integer | −2 147 483 648 to 2 147 483 647 |
| `i64` | 64-bit signed integer | −9.2 × 10¹⁸ to 9.2 × 10¹⁸ |
| `u16` | 16-bit unsigned integer | 0 to 65 535 |
| `u32` | 32-bit unsigned integer | 0 to 4 294 967 295 |
| `u64` | 64-bit unsigned integer | 0 to 1.8 × 10¹⁹ |
| `d32` | Single-precision decimal | ~7 significant digits |
| `d64` | Double-precision decimal | ~15 significant digits |
| `d128` | Arbitrary-precision decimal | Exact, no rounding |

`d128` uses arbitrary-precision decimal arithmetic (backed by `rust_decimal` in Rust and `BigDecimal` in Java). Use it for financial, scientific, or any domain where floating-point rounding is unacceptable.

**Applicable constraints:** `minValue`, `maxValue`, `minPrecision`, `maxPrecision`

---

## Boolean Type

| Keyword | Description | Wire values |
|---|---|---|
| `bool` | Logical flag | `true` / `false` |

**No constraints apply to `bool` fields.**

---

## String Types

| Keyword | Description | Validation |
|---|---|---|
| `str` | General UTF-8 string | Length bounds; optional regex |
| `nstr` | Normalised string (collapses internal whitespace) | Same as `str` |
| `uuid` | RFC 4122 UUID | `xxxxxxxx-xxxx-Mxxx-Nxxx-xxxxxxxxxxxx` (hyphenated hex) |
| `uri` | Uniform Resource Identifier | URI syntax |
| `email` | Email address | `local@domain.tld` (simplified RFC 5321) |
| `hostname` | DNS hostname | RFC 1123 label rules |
| `ipv4` | IPv4 address | Dotted-decimal, e.g. `192.168.1.1` |
| `ipv6` | IPv6 address | Colon-hex, e.g. `2001:db8::1` |

**Applicable constraints:** `minLength`, `maxLength`, `pattern` / `regex`

---

## Temporal Types

| Keyword | Description | Format |
|---|---|---|
| `date` | Calendar date | `YYYYMMDD` (integer) |
| `time` | Clock time | `HHmmss` (integer) |
| `datetime` | Combined date and time | `YYYYMMDDHHmmss` (integer) |
| `timestamp` | Unix epoch timestamp | Integer seconds or milliseconds |
| `tsz` | Timestamp with UTC offset | ISO 8601: `YYYY-MM-DDTHH:mm:ssZ` |
| `inst` | Local instant (no timezone) | ISO 8601: `YYYY-MM-DDTHH:mm:ss` |
| `duration` | ISO 8601 duration | `P3Y6M4DT12H30M5S` |

Integer temporal types (`date`, `time`, `datetime`, `timestamp`) appear as numeric values in data rows. String temporal types (`tsz`, `inst`, `duration`) appear as quoted strings.

---

## Binary / Encoded Types

| Keyword | Description | Format |
|---|---|---|
| `base64` | Base64-encoded binary | Standard Base64 alphabet |
| `hex` | Hex-encoded binary | Lowercase or uppercase hex digits |
| `oid` | BSON ObjectId | Exactly 24 hexadecimal characters |

---

## Constraint Applicability

| Constraint | Applicable types |
|---|---|
| `minValue` / `maxValue` | `i16` `i32` `i64` `u16` `u32` `u64` `d32` `d64` `d128` |
| `minPrecision` / `maxPrecision` | `d32` `d64` `d128` |
| `minLength` / `maxLength` | `str` `nstr` `uuid` `uri` `email` `hostname` `ipv4` `ipv6` `hex` `base64` `oid` |
| `pattern` / `regex` | All string-based types |
| `minItems` / `maxItems` | Any array field (`type[]` or `<Schema>[]`) |
| `allowNullItems` / `maxNullItems` | Any array field |
| `default` | All scalar types |
| `const` | All scalar types |

---

## Schema Usage Examples

```jsont
// Integer fields
i32:  quantity [(minValue=1, maxValue=999)],
u64:  sequenceId,
d128: financialAmount [(minPrecision=2, maxPrecision=6)],

// String fields
str:  productCode [(minLength=3, maxLength=20, pattern="^[A-Z]{2}-\\d+$")],
uuid: traceId,
email: contactEmail?,
ipv4: serverAddress?,

// Temporal fields
datetime:  scheduledAt,
tsz:       completedAt?,
duration:  estimatedTime,

// Binary fields
hex: checksum [(minLength=64, maxLength=64)],   // SHA-256
oid: documentRef?,

// Array fields
str[]:  tags?      [(minItems=1, maxItems=10)],
uuid[]: relatedIds [(maxNullItems=0)]
```

---

## Object and Array Field Types

In addition to the scalar types above, fields can reference other schemas or be declared as arrays.

| Declaration | Description |
|---|---|
| `<SchemaName>: field` | Object field — value is a nested row conforming to `SchemaName` |
| `type[]: field` | Array of scalar values |
| `<SchemaName>[]: field` | Array of objects conforming to `SchemaName` |

Object and array fields share the `minItems`, `maxItems`, `allowNullItems`, and `maxNullItems` constraints.
