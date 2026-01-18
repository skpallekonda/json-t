# JsonT Language Specification

JsonT is a compact data representation format that combines schema definition and data in a single document. It is
designed to minimize redundancy in tabular and repetitive datasets by separating the structural definition (schema) from
the data records.

---

## 1. Document Structure

A JsonT document is composed of two primary sections: the **Catalog** (Schemas and Enums) and the **Data** (Records).

```jsont
{
  // --- Catalog ---
  "schemas": { ... },
  "enums": [ ... ],

  // --- Data ---
  "data-schema": "SchemaName",
  "data": [
    { ... },
    { ... }
  ]
}
```

---

## 2. Catalog Section

The Catalog defines the structural blueprints used to interpret the data records.

### 2.1 Schema Definitions

Schemas define the fields, types, and constraints for data records.

**Formal Syntax:**

```antlr
fieldDecl : typeRef ':' identifier optionalMark? '(' constraintsSection? ')' ;
```

**Components:**

- **Type Reference (`typeRef`)**: Specifies the data type.
    - Scalar: `i32`, `str`, `bool`, etc.
    - Object Reference: `<Address>`
    - Arrays: `str[]`, `<Item>[]`
- **Identifier**: The name of the field.
- **Optional Mark (`?`)**: Indicates the field may contain `null`.
- **Constraint Block `()`**: A parenthesized block containing one or more [Validation Constraints](#4-constraints).
  *Note: The parentheses are required by the grammar even if no constraints are specified.*

**Example:**

```jsont
schemas: {
  User: {
    i32: id (),
    str: email? (regex("^.+@.+\..+$")),
    str[]: roles ()
  }
}
```

### 2.2 Enum Definitions

Enums define a fixed set of allowed symbolic values.

**Syntax:**

```jsont
enums: [
  EnumName { Value1, Value2, ... }
]
```

**Example:**

```jsont
enums: [
  Status { ACTIVE, INACTIVE, PENDING }
]
```

---

## 3. Data Section

The Data section contains the actual records and links them to a specific schema.

### 3.1 Data Schema Declaration

Specifies which schema from the Catalog applies to the data rows.

```jsont
"data-schema": "User"
```

### 3.2 Data Records

Data is structured as an array of rows. Each row is a comma-separated list of values enclosed in curly braces `{}`.

**Syntax:**

```jsont
"data": [
  { val1, val2, ... },
  { val1, val2, ... }
]
```

- **Values** can be Literals (Strings, Numbers, Booleans, Null), unquoted identifiers (for Enums), nested objects
  `{...}`, or nested arrays `[...]`.

---

## 4. Scalar Types

JsonT supports a rich set of built-in scalar types, identified by specific keywords.

| Key    | Type    | Description                     |
|:-------|:--------|:--------------------------------|
| `i16`  | Short   | 16-bit signed integer           |
| `i32`  | Integer | 32-bit signed integer           |
| `i64`  | Long    | 64-bit signed integer           |
| `u16`  | UShort  | 16-bit unsigned integer         |
| `u32`  | UInt    | 32-bit unsigned integer         |
| `u64`  | ULong   | 64-bit unsigned integer         |
| `d32`  | Float   | Single-precision floating point |
| `d64`  | Double  | Double-precision floating point |
| `d128` | Decimal | 128-bit decimal floating point  |
| `bool` | Boolean | `true` or `false`               |
| `str`  | String  | UTF-8 character sequence        |
| `uuid` | UUID    | Universally Unique Identifier   |
| `uri`  | URI     | Uniform Resource Identifier     |
| `zip`  | Zip     | General Zip code                |
| `zip5` | Zip5    | 5-digit Zip code                |
| `pin`  | PIN     | Postal Index Number             |

---

## 5. Constraints

Constraints are applied to fields to enforce validation rules. They are declared within the field's parentheses.

**Syntax:**

```jsont
( ConstraintName(Value), ... )
```

**Common Constraints:**

- `minLength(n)`: Minimum string length.
- `maxLength(n)`: Maximum string length.
- `regex(pattern)`: Regular expression match.
- `minValue(n)`: Minimum numeric value.
- `maxValue(n)`: Maximum numeric value.

---

## 6. Syntax Rules & Literals

### 6.1 Identifiers

Identifiers (for schema names, field names, and enum values) must start with a letter or underscore, followed by
letters, numbers, or underscores.

### 6.2 Literals

- **Booleans**: `true`, `false`.
- **Null**: `null` or `∅` (Unicode `\u2205`).
- **Numbers**: Standard integer and decimal notation (e.g., `-123`, `45.67`).
- **Strings**: Enclosed in double (`"`) or single (`'`) quotes. Supports standard escape sequences.

### 6.3 Whitespace

Whitespace is non-significant and is used for readability.
