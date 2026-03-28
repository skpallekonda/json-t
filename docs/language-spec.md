# JsonT Language Specification

JsonT is a compact, schema-driven data format that separates structural definitions (schemas) from data (positional tuples). A single document can contain a namespace block (with schema definitions), data rows, or both.

---

## 1. Document Structure

A JsonT document is a top-level sequence of two optional sections:

```
document = namespace? data_rows?
```

- **`namespace`** — declares schemas, enums, catalogs, and the active data schema.
- **`data_rows`** — one or more positional data tuples, comma-separated.

When both are present the namespace block always comes first.

**Minimal complete document:**

```jsont
{
  namespace: {
    baseUrl: "https://api.example.com/v1",
    version: "1.0",
    catalogs: [
      {
        schemas: [
          Order: {
            fields: {
              i64: id,
              str: product [(minLength=2, maxLength=80)],
              i32: quantity [(minValue=1, maxValue=999)],
              d64: price    [(minValue=0.01)]
            }
          }
        ]
      }
    ],
    data-schema: Order
  }
}

{1,"Widget A",10,9.99},
{2,"Widget B",5,24.50}
```

---

## 2. Namespace

```jsont
{
  namespace: {
    baseUrl: "https://api.example.com/v1",
    version: "1.0",
    catalogs: [ <catalog>, ... ],
    data-schema: SchemaName
  }
}
```

| Field | Required | Description |
|---|---|---|
| `baseUrl` | Yes | Canonical URI for this namespace |
| `version` | Yes | Version identifier (string) |
| `catalogs` | Yes | One or more catalog objects |
| `data-schema` | Yes | Name of the schema that data rows conform to |

---

## 3. Catalog

A catalog groups related schema and enum definitions.

```jsont
{
  schemas: [ <schema_entry>, ... ],
  enums:   [ <enum_def>, ... ]       // optional
}
```

`enums` is optional. A catalog must contain at least one schema.

---

## 4. Schema

### 4.1 Straight Schema

A straight schema declares its own typed fields.

```jsont
SchemaName: {
  fields: {
    type: fieldName,
    type: fieldName? [(constraints)],
    <OtherSchema>:  objectField,
    type[]:         arrayField?
  },
  validations: {              // optional
    rules   { <rule>, ... }
    unique  { (<field_path_list>), ... }
    dataset { <expression>, ... }
  }
}
```

**Field declaration:**

```
scalar_type : fieldName[?] [(<constraint_pair>, ...)]
<SchemaName>: fieldName[?]
type[]      : fieldName[?]
```

| Part | Description |
|---|---|
| `type` | Scalar type keyword or object reference `<SchemaName>` |
| `fieldName` | Lowercase-start identifier |
| `?` | Optional mark — value may be `null` or absent |
| `[(constraints)]` | Square bracket wrapping one or more constraint expressions |

**Examples:**

```jsont
i64:     id,
str:     email?   [(minLength=5, maxLength=255)],
d64:     price    [(minValue=0.01, maxValue=9999.99)],
str:     code     [pattern="^[A-Z]{3}-\\d{4}$"],
bool:    active,
str[]:   tags?,
<Item>[]: lineItems
```

### 4.2 Field Constraints

| Constraint | Applies to | Syntax |
|---|---|---|
| `minValue` / `maxValue` | Numeric types | `(minValue=0, maxValue=100)` |
| `minLength` / `maxLength` | String types | `(minLength=2, maxLength=80)` |
| `pattern` / `regex` | String types | `pattern="^[A-Z]+"` |
| `minItems` / `maxItems` | Array fields | `(minItems=1, maxItems=20)` |
| `allowNullItems` | Array fields | `allowNullItems=true` |
| `maxNullItems` | Array fields | `(maxNullItems=3)` |
| `required` | Any field | `required=true` |
| `default` | Scalar fields | `default 0` |
| `const` | Scalar fields | `const "v1"` |

---

### 4.3 Derived Schema

A derived schema inherits all fields from a parent schema and applies an ordered list of operations.

```jsont
DerivedName: FROM ParentName {
  operations: (
    <operation>,
    <operation>,
    ...
  ),
  validations: { ... }    // optional — applied to the output field set
}
```

`FROM` is uppercase. Operations run in declaration order; each operation sees the field set as shaped by all previous operations.

---

## 5. Operations (Derived Schemas)

| Operation | Syntax | Effect |
|---|---|---|
| `rename` | `rename(oldName as newName, ...)` | Rename one or more fields. Values are unchanged. |
| `exclude` | `exclude(field1, field2, ...)` | Drop the listed fields. All others are kept. |
| `project` | `project(field1, field2, ...)` | Keep only the listed fields. All others are dropped. |
| `filter` | `filter <expression>` | Drop rows where the boolean expression is `false`. Not a hard error — callers skip the row. |
| `transform` | `transform field = <expression>` | Replace a field's value with the result of evaluating the expression. |

**Example — derived broadcast projection:**

```jsont
MatchSummary: FROM CricketMatch {
  operations: (
    project(matchId, teamAName, teamBName, scheduledAt, isDayNight, attendanceCount),
    rename(attendanceCount as crowdSize),
    filter isDayNight || crowdSize > 30000,
    transform crowdSize = crowdSize / 1000
  )
}
```

---

## 6. Validation Block

```jsont
validations: {
  rules {
    price > 0,
    quantity > 0,
    stock > 0 -> required(warehouseId)       // conditional requirement
  }
  unique {
    (id),                                     // single-field uniqueness
    (orderId, lineNumber)                     // composite uniqueness
  }
  dataset {
    count(id) > 0                             // dataset-level assertions
  }
}
```

| Sub-block | Description |
|---|---|
| `rules` | Per-row rule expressions, comma-separated. Conditional form: `condition -> required(field1, field2)` means those fields must be non-null whenever `condition` is true. |
| `unique` | Each entry is a parenthesized field path list; the combined value must be unique across all rows. |
| `dataset` | Expressions applied to the full dataset (e.g., aggregate checks). |

---

## 7. Enums

```jsont
enums: [
  Status:  [ACTIVE, INACTIVE, SUSPENDED],
  Role:    [ADMIN, USER, GUEST],
  Phase:   [GROUP_STAGE, SUPER_8, SEMI_FINAL, FINAL]
]
```

Enum values are uppercase identifiers of two or more characters (`[A-Z][A-Z0-9_]+`). They are referenced in field declarations as `<EnumName>` and appear in data rows as bare constants (e.g. `ACTIVE`).

---

## 8. Data Rows

```jsont
{val1, val2, val3, ...},
{val1, val2, val3, ...}
```

- Rows are positional — value at position *n* maps to field *n* in the schema.
- Rows are separated by commas. A trailing comma after the last row is permitted.
- Whitespace inside and between rows is non-significant.

### 8.1 Value Types

| Value | Syntax | Notes |
|---|---|---|
| Number | `42`, `3.14` | Integer or decimal literal |
| String | `"hello"`, `'hello'` | Double or single quotes; `\\`, `\"` escapes |
| Boolean | `true`, `false` | |
| Null | `null` or `nil` | Both spellings are equivalent |
| Unspecified | `_` | Field intentionally omitted (CDC / patch semantics) |
| Enum constant | `ACTIVE` | Bare all-uppercase identifier |
| Nested row | `{v1, v2}` | Inline object (maps to an object-typed field) |
| Array | `[v1, v2, v3]` | Inline array (maps to an array-typed field) |

---

## 9. Expressions

Expressions appear in validation rules, filter predicates, dataset blocks, and transform targets.

| Category | Description | Examples |
|---|---|---|
| **Literal** | Constant value | `42`, `"hello"`, `true`, `null` |
| **FieldRef** | Reference to a field by name | `price`, `order.total` |
| **UnaryOp** | Prefix operator | `!active`, `-price` |
| **BinaryOp** | Binary operator | `price * qty`, `age >= 18`, `active && verified` |
| **FunctionCall** | Built-in function call | `count(id)`, `len(tags)` |

**Operator precedence** (high → low):

1. Unary: `!`, `-`
2. Multiplicative: `*`, `/`
3. Additive: `+`, `-`
4. Relational: `<`, `>`, `<=`, `>=`
5. Equality: `==`, `!=`
6. Logical AND: `&&`
7. Logical OR: `||`

---

## 10. Identifiers

| Kind | Pattern | Examples |
|---|---|---|
| Schema name / Enum name | Uppercase-start, alphanumeric + `_` | `Order`, `LineItem`, `TournamentPhase` |
| Enum constant | Two or more uppercase letters / digits / `_` | `ACTIVE`, `IN_PROGRESS`, `V2` |
| Field name | Lowercase-start, alphanumeric + `_` | `orderId`, `price`, `teamAName` |

---

## 11. Comments

Both `//` line comments and `/* */` block comments are supported and are ignored by the parser.

```jsont
// This is a line comment
{
  namespace: {
    baseUrl: "https://api.example.com/v1",  /* inline block comment */
    version: "1.0",
    ...
  }
}
```
