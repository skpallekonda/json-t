# jsont

A schema-driven, positional data language for Rust.

JsonT separates field declarations (defined once in a schema) from data
(encoded as compact positional tuples).  The result is a format that is more
concise than JSON, strictly typed, and designed for high-throughput pipelines.

---

## Contents

1. [Core concepts](#core-concepts)
2. [Building schemas](#building-schemas)
3. [Parsing](#parsing)
   - [Buffered — full namespace](#buffered--full-namespace)
   - [Streaming — rows only (in-memory string)](#streaming--rows-only-in-memory-string)
   - [Streaming — rows from a file (O(1) memory)](#streaming--rows-from-a-file-o1-memory)
4. [Stringification](#stringification)
5. [Validation](#validation)
   - [Streaming mode](#streaming-mode-o1-memory)
   - [Buffered mode](#buffered-mode)
   - [Custom sinks](#custom-sinks)
6. [Derived schemas and transforms](#derived-schemas-and-transforms)
   - [Schema-level validation](#schema-level-validation)
     - [Straight schemas](#straight-schemas)
     - [Derived schemas](#derived-schemas)
   - [Applying transforms to rows](#applying-transforms-to-rows)
7. [Privacy & Encryption](#privacy--encryption)
8. [Expression building](#expression-building)
9. [Error handling](#error-handling)
10. [Performance notes](#performance-notes)

---

## Core concepts

| Concept | Description |
|---|---|
| **`JsonTSchema`** | Declares a named set of typed fields (`Straight`) or derives from another schema (`Derived`). |
| **`JsonTRow`** | One data record — an ordered `Vec<JsonTValue>` whose positions map to schema fields. |
| **`JsonTNamespace`** | Top-level container: one or more `JsonTCatalog`s, each holding schemas and enums. |
| **`SchemaRegistry`** | A name-keyed map of schemas used to resolve derivation chains at transform time. |
| **`ValidationPipeline`** | Validates rows against a schema's field constraints and rules; routes diagnostics to sinks. |

A `JsonTRow` is positional, not named.  Field 0 corresponds to schema field 0,
field 1 to schema field 1, and so on.  Names exist only in the schema.

---

## Building schemas

Use the fluent builders to construct schemas programmatically.

```rust
use jsont::{
    JsonTFieldBuilder, JsonTSchemaBuilder, ScalarType,
    JsonTValidationBlockBuilder, FieldPath,
};

// ── Straight schema: declare fields explicitly ────────────────────────────────

let order_schema = JsonTSchemaBuilder::straight("Order")
    .field_from(JsonTFieldBuilder::scalar("id",       ScalarType::I64))?
    .field_from(JsonTFieldBuilder::scalar("product",  ScalarType::Str)
        .min_length(2))?
    .field_from(JsonTFieldBuilder::scalar("quantity", ScalarType::I32)
        .min_value(1.0)
        .max_value(999.0))?
    .field_from(JsonTFieldBuilder::scalar("price",    ScalarType::D64)
        .min_value(0.01))?
    .validation_from(
        JsonTValidationBlockBuilder::new()
            .unique(vec![FieldPath::single("id")])  // id must be unique across rows
    )?
    .build()?;

// ── Derived schema: inherit fields and apply operations ───────────────────────

use jsont::{SchemaOperation, RenamePair, JsonTExpression, BinaryOp, JsonTValue};

let summary_schema = JsonTSchemaBuilder::derived("OrderSummary", "Order")
    // Keep only id and product.
    .operation(SchemaOperation::Project(vec![
        FieldPath::single("id"),
        FieldPath::single("product"),
    ]))?
    .build()?;
```

### Available field types

`ScalarType::I16 | I32 | I64 | U16 | U32 | U64 | D32 | D64 | D128`
`| Bool | Str | NStr | Uri | Uuid | Email | Hostname | Ipv4 | Ipv6`
`| Date | Time | DateTime | Timestamp | Tsz | Duration | Inst`
`| Base64 | Oid | Hex`

Object and array fields:

```rust
// Scalar array field
let tags_field = JsonTFieldBuilder::scalar("tags", ScalarType::Str)
    .as_array()
    .optional();

// Object field (references another schema by name)
let address_field = JsonTFieldBuilder::object("address", "Address");
```

---

## Parsing

### Buffered — full namespace

Parse a complete JsonT source file (namespace block + optional data rows)
in one call.  Backed by a [pest](https://pest.rs/) PEG grammar.

```rust
use jsont::{Parseable, JsonTNamespace};

let source = r#"
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
                  str: product  [(minLength=2)],
                  i32: qty      [(minValue=1, maxValue=999)],
                  d64: price    [(minValue=0.01)]
                }
              }
            ]
          }
        ],
        data-schema: Order
      }
    }
"#;

let ns: JsonTNamespace = JsonTNamespace::parse(source)?;

// Build a registry for derived-schema resolution.
use jsont::SchemaRegistry;
let registry = SchemaRegistry::from_namespace(&ns);
```

### Streaming — rows only (in-memory string)

`parse_rows` is a hand-written byte scanner.  It calls a closure for each
completed `JsonTRow` without building an intermediate parse tree, making memory
use O(1) in the number of rows.

```rust
use jsont::parse_rows;

let data = r#"
    { 1, "Widget A", 10, 9.99 },
    { 2, "Widget B",  5, 24.50 },
    { 3, "Widget C",  1, 99.00 }
"#;

let mut rows = Vec::new();
let count = parse_rows(data, |row| rows.push(row))?;
println!("parsed {count} rows");

// Or process inline without collecting:
parse_rows(data, |row| {
    println!("row has {} fields", row.len());
})?;
```

### Streaming — rows from a file (O(1) memory)

`parse_rows_streaming` accepts any `BufRead` source and uses `fill_buf()` +
`consume()` internally.  The read buffer stays at O(BufReader capacity) (~8 KB)
and only one row's bytes are live at a time.  **File size plays no role in peak
memory** — a 5 GB file parses with the same ~14 KB overhead as a 5 KB file.

```rust
use std::fs::File;
use std::io::BufReader;
use jsont::parse_rows_streaming;

let file = File::open("orders.jsont")?;
let reader = BufReader::new(file);

let count = parse_rows_streaming(reader, |row| {
    // each row is dropped here; no accumulation
    println!("row has {} fields", row.len());
})?;
println!("parsed {count} rows");
```

`RowIter` wraps the same extractor as an `Iterator<Item = JsonTRow>`, useful
when an API expects `IntoIterator`:

```rust
use std::fs::File;
use std::io::BufReader;
use jsont::RowIter;

let file = File::open("orders.jsont")?;
for row in RowIter::new(BufReader::new(file)) {
    println!("{:?}", row);
}
```

**Single-threaded streaming parse + validate in one pass** (O(1) memory):

```rust
use std::fs::File;
use std::io::BufReader;
use jsont::{RowIter, ValidationPipeline};

let pipeline = ValidationPipeline::builder(order_schema)
    .without_console()
    .build();

let file = File::open("orders.jsont")?;
let row_iter = RowIter::new(BufReader::new(file));

pipeline.validate_each(row_iter, |clean_row| {
    // called immediately per clean row — no Vec, no buffering
});

pipeline.finish()?;
```

**Parallel parse + validate pipeline** (for large files, uses all CPU cores):

```rust
use std::fs::File;
use std::io::BufReader;
use std::sync::{Arc, Mutex, mpsc::sync_channel};
use jsont::{JsonTRow, RowIter, ValidationPipeline};

let n_workers = std::thread::available_parallelism()
    .map(|n| n.get().saturating_sub(1).max(1))
    .unwrap_or(1);

let (tx, rx) = sync_channel::<JsonTRow>(2048);
let rx = Arc::new(Mutex::new(rx));

std::thread::scope(|s| {
    // Producer: parses rows and feeds the channel
    s.spawn(move || {
        let file = File::open("orders.jsont").unwrap();
        for row in RowIter::new(BufReader::new(file)) {
            if tx.send(row).is_err() { break; }
        }
        // tx dropped here → channel closes → workers exit
    });

    // Workers: each owns a pipeline and calls validate_one per row
    for _ in 0..n_workers {
        let rx_arc = Arc::clone(&rx);
        s.spawn(move || {
            let pipeline = ValidationPipeline::builder(order_schema.clone())
                .without_console()
                .build();
            loop {
                match rx_arc.lock().unwrap().recv() {
                    Ok(row) => pipeline.validate_one(row, |_clean| { /* emit */ }),
                    Err(_)  => break, // channel closed
                }
            }
            pipeline.finish().unwrap();
        });
    }
});
```

---

## Stringification

Convert model types back to JsonT source text.

```rust
use jsont::{Stringification, StringifyOptions};

// Compact (single-line)
let compact = schema.stringify(StringifyOptions::compact());

// Pretty-printed
let pretty  = schema.stringify(StringifyOptions::pretty());

// Pretty with custom indent
let wide    = schema.stringify(StringifyOptions::pretty_with_indent(4));
```

### Zero-allocation row writers

For high-throughput output, write rows directly to any `impl Write`
without allocating intermediate `String` values.

```rust
use jsont::{write_row, write_rows};
use std::io::BufWriter;
use std::fs::File;

// Write a single row
let mut buf = Vec::new();
write_row(&row, &mut buf)?;
// buf: b"{1,\"Widget A\",10,9.99}"

// Write many rows to a file
let file = File::create("output.jsont")?;
let mut writer = BufWriter::new(file);
write_rows(&rows, &mut writer)?;
writer.flush()?;
```

---

## Validation

The `ValidationPipeline` validates `JsonTRow` values against:

- Field-level constraints (required, value bounds, length, pattern, …)
- Row-level rules from the schema's `validations` block
- Cross-row uniqueness constraints

Diagnostics are routed asynchronously to registered sinks so I/O never stalls
the validation loop.

### Streaming mode (O(1) memory)

```rust
use jsont::{ValidationPipeline, MemorySink};

let sink = Box::new(MemorySink::new());
let pipeline = ValidationPipeline::builder(order_schema)
    .without_console()
    .with_sink(sink)
    .build();

// validate_each emits each clean row to the closure immediately.
// No buffering of clean rows — suitable for arbitrarily large datasets.
pipeline.validate_each(parsed_rows, |clean_row| {
    // write to output, feed into next stage, etc.
});

// Always call finish() to flush sinks and join the background thread.
pipeline.finish()?;
```

### Buffered mode

```rust
// Collects all clean rows into a Vec — O(clean-rows) memory.
let clean: Vec<_> = pipeline.validate_rows(parsed_rows);
pipeline.finish()?;
```

`validate_rows` accepts any `IntoIterator<Item = JsonTRow>`, including
`Vec<JsonTRow>`, arrays, or custom lazy iterators.

### Custom sinks

Implement `DiagnosticSink` to route events anywhere — a database, a log
aggregator, a test buffer, etc.

```rust
use jsont::{DiagnosticSink, DiagnosticEvent, SinkError};

struct JsonlSink {
    writer: std::io::BufWriter<std::fs::File>,
}

impl DiagnosticSink for JsonlSink {
    fn emit(&mut self, event: DiagnosticEvent) {
        // Serialize event and write one JSON-L line.
        let line = format!("{:?}\n", event);
        let _ = self.writer.write_all(line.as_bytes());
    }

    fn flush(&mut self) -> Result<(), SinkError> {
        self.writer.flush().map_err(|e| SinkError::Io(e.to_string()))
    }
}

let pipeline = ValidationPipeline::builder(schema)
    .without_console()
    .with_sink(Box::new(JsonlSink { writer: /* … */ }))
    .build();
```

Built-in sinks: `ConsoleSink` (default), `FileSink`, `MemorySink`.

---

## Derived schemas and transforms

A derived schema inherits its parent's field set and applies an ordered list
of operations.  Operations are applied left-to-right per row.

```
Order (Straight)  id, product, quantity, price
  └─ OrderSummary (Derived)   Project([id, product])         → id, product
       └─ PublicOrder (Derived)  Rename(product → title)    → id, title
```

### Available operations

| Operation | Effect |
|---|---|
| `Rename(pairs)` | Rename one or more fields (values unchanged). |
| `Exclude(paths)` | Drop the listed fields from the row. |
| `Project(paths)` | Keep only the listed fields; drop everything else. |
| `new_filter(expr)` | Drop rows where the boolean expression is false. Returns `TransformError::Filtered` — not a hard failure. |
| `new_transform(target, expr)` | Replace a field's value with the result of evaluating `expr`. |

```rust
use jsont::{
    JsonTSchemaBuilder, SchemaOperation, FieldPath, RenamePair,
    JsonTExpression, BinaryOp, JsonTValue,
};

// Exclude quantity, rename price → amount, filter rows where amount > 0
let view = JsonTSchemaBuilder::derived("OrderView", "Order")
    .operation(SchemaOperation::Exclude(vec![
        FieldPath::single("quantity"),
    ]))?
    .operation(SchemaOperation::Rename(vec![RenamePair {
        from: FieldPath::single("price"),
        to:   "amount".into(),
    }]))?
    .operation(SchemaOperation::new_filter(
        JsonTExpression::binary(
            BinaryOp::Gt,
            JsonTExpression::field_name("amount"),
            JsonTExpression::literal(JsonTValue::d64(0.0)),
        )
    ))?
    .build()?;
```

### Schema-level validation

Call `validate_schema` **once after building your registry** to catch mistakes
at schema construction time rather than mid-stream during row processing.

It is a **static analysis** pass — no row data is required.  It covers all of
the following, for both straight and derived schemas.

#### Straight schemas

| Check | What is verified |
|---|---|
| Object field references | Every `Object { schema_ref }` field must name a schema present in the registry. |
| Rule `FieldRef`s | Every field name referenced inside a `rules` expression or conditional requirement must be declared in this schema's own field list. |
| Unique constraint paths | Every field path in a `unique` constraint group must be declared in this schema. |
| Dataset expression `FieldRef`s | Same check for `dataset` expressions. |

#### Derived schemas

| Check | What is verified |
|---|---|
| Parent exists | The schema named in `from` must be present in the registry. |
| No cycle | The full derivation chain must be free of cycles (e.g. `A → B → A`). |
| Operation field paths | Every path in `Rename` / `Exclude` / `Project` must name a field that is still available at that point in the pipeline. |
| Expression `FieldRef`s | Every `FieldRef` in a `Filter` or `Transform` expression must name a field that is still available at that point (not yet excluded or projected away). |
| Validation block on derived schema | If the derived schema itself carries a `validations` block, checks 2–4 above are applied against the **output** field set (fields that remain after all operations). |

```rust
use jsont::SchemaRegistry;

let mut registry = SchemaRegistry::new();
registry.register(order_schema);
registry.register(view_schema.clone());

// Validate both schemas.
order_schema.validate_schema(&registry)?;
view_schema.validate_schema(&registry)?;
```

**What it detects — derived schema examples:**

```rust
// ✗ Exclude age, then filter on age — caught at validate time:
let bad = JsonTSchemaBuilder::derived("Bad", "Person")
    .operation(SchemaOperation::Exclude(vec![FieldPath::single("age")]))?
    .operation(SchemaOperation::Filter(
        JsonTExpression::field_name("age")  // age was excluded above
    ))?
    .build()?;

bad.validate_schema(&registry)?;
// → Err(FieldNotFound("Filter (op #2): expression references field 'age'
//        which is not available at this point in the pipeline …"))

// ✗ Rename age → years, then filter on the old name:
// → Err(FieldNotFound("Filter (op #2): expression references field 'age' …"))

// ✓ Rename age → years, then filter on years (new name):
// → Ok(())
```

**What it detects — straight schema examples:**

```rust
// ✗ Object field references a schema not in the registry:
// → Err(Transform(UnknownSchema("field 'address' references unknown schema 'Address'")))

// ✗ Validation rule references an undeclared field:
// → Err(Transform(FieldNotFound("schema 'Order' rule #1: expression references undeclared field 'discount'")))

// ✗ Unique constraint references an undeclared field:
// → Err(Transform(FieldNotFound("schema 'Order' unique constraint #1: field 'sku' is not declared in this schema")))
```

### Applying transforms to rows

```rust
use jsont::RowTransformer;

// Straight schema: transform is a no-op, returns row unchanged.
let row = order_schema.transform(row, &registry)?;

// Derived schema: applies the full operation pipeline.
let transformed = view_schema.transform(order_row, &registry)?;

// Filter predicates that evaluate to false signal row exclusion:
match view_schema.transform(row, &registry) {
    Ok(transformed_row) => { /* emit to output */ }
    Err(JsonTError::Transform(TransformError::Filtered)) => { /* row excluded */ }
    Err(e) => return Err(e),
}
```

**Chaining derivation chains:**

```rust
// Person (Straight): id, name, age
// PersonNoAge (Derived from Person): Exclude(age)        → id, name
// PersonSummary (Derived from PersonNoAge): Rename(name → fullName) → id, fullName

let after_exclude  = no_age_schema.transform(person_row,   &registry)?;
let final_row      = summary_schema.transform(after_exclude, &registry)?;
```

---

## Privacy & Encryption

JsonT supports field-level encryption through **privacy markers** (`~`) in the schema DSL and a pluggable `CryptoConfig` trait.

### Marking sensitive fields

In a DSL schema use `~type` for any field that must be encrypted on the wire:

```jsont
Person: {
  fields: {
    str:  name,
    ~str: ssn,
    ~str: cardNumber?
  }
}
```

When building programmatically, call `.sensitive()` on the field builder:

```rust
use jsont::{JsonTFieldBuilder, JsonTSchemaBuilder, ScalarType};

let schema = JsonTSchemaBuilder::straight("Person")
    .field(JsonTFieldBuilder::scalar("name", ScalarType::Str).build()?)?
    .field(JsonTFieldBuilder::scalar("ssn",  ScalarType::Str).sensitive().build()?)?
    .build()?;
```

### Schema-aware write (encrypt on output)

Use `write_row_with_schema` instead of `write_row` when a schema is available.
Sensitive fields that hold plain text are encrypted; already-encrypted fields are re-encoded as `base64:` without another crypto call.

```rust
use jsont::{write_row_with_schema, PassthroughCryptoConfig};
use std::io::BufWriter;
use std::fs::File;

// schema_fields() extracts &[JsonTField] from SchemaKind::Straight { fields }
let fields = match &schema.kind {
    jsont::model::schema::SchemaKind::Straight { fields } => fields.as_slice(),
    _ => panic!("expected straight schema"),
};

let mut out = BufWriter::new(File::create("output.jsont")?);
write_row_with_schema(&row, fields, &PassthroughCryptoConfig, &mut out)?;
// SSN column → "base64:<b64>"; name column → plain string
```

Replace `PassthroughCryptoConfig` with any type that implements `CryptoConfig`:

```rust
use jsont::crypto::{CryptoConfig, CryptoError};

struct AesGcmCrypto { /* key material */ }

impl CryptoConfig for AesGcmCrypto {
    fn encrypt(&self, field: &str, plaintext: &[u8]) -> Result<Vec<u8>, CryptoError> {
        // AES-GCM encrypt
        todo!()
    }
    fn decrypt(&self, field: &str, ciphertext: &[u8]) -> Result<Vec<u8>, CryptoError> {
        // AES-GCM decrypt
        todo!()
    }
}
```

### Decrypt in a derived schema pipeline

Add a `Decrypt` operation to a derived schema to decrypt fields as part of the transform:

```rust
use jsont::model::schema::SchemaOperation;

let derived = JsonTSchemaBuilder::derived("PersonDecrypted", "Person")
    .operation(SchemaOperation::Decrypt { fields: vec!["ssn".to_string()] })?
    .build()?;

// Use transform_with_crypto — transform() without crypto returns DecryptFailed
let result = derived.transform_with_crypto(row, &registry, &PassthroughCryptoConfig)?;
// result.fields[1] is now JsonTValue::Str("123-45-6789")
```

### On-demand decrypt

Decrypt a single field without deriving a new schema:

```rust
use jsont::{JsonTValue, JsonTRow, PassthroughCryptoConfig};

// Decrypt a value directly
let val = JsonTValue::encrypted(b"hello".to_vec());
let text: Option<String> = val.decrypt_str("field_name", &PassthroughCryptoConfig)?;
let bytes: Option<Vec<u8>> = val.decrypt_bytes("field_name", &PassthroughCryptoConfig)?;

// Decrypt by position in a row (returns None for out-of-range or non-encrypted)
let row = JsonTRow::new(vec![
    JsonTValue::str("Alice"),
    JsonTValue::encrypted(b"123-45-6789".to_vec()),
]);
let ssn: Option<String> = row.decrypt_field_str(1, "ssn", &PassthroughCryptoConfig)?;
```

---

## Expression building

`JsonTExpression` is used in validation rules, filter predicates, and
transform expressions.  Build them with the provided convenience constructors.

```rust
use jsont::{JsonTExpression, BinaryOp, UnaryOp, JsonTValue};

// age >= 18
let adult = JsonTExpression::binary(
    BinaryOp::Ge,
    JsonTExpression::field_name("age"),
    JsonTExpression::literal(JsonTValue::i32(18)),
);

// !(active) — logical NOT
let inactive = JsonTExpression::not(
    JsonTExpression::field_name("active"),
);

// price * quantity  (used in a Transform)
let total = JsonTExpression::binary(
    BinaryOp::Mul,
    JsonTExpression::field_name("price"),
    JsonTExpression::field_name("quantity"),
);

// Evaluate directly against a binding context
use jsont::{Evaluatable, EvalContext};
let ctx = EvalContext::new()
    .bind("age",   JsonTValue::i32(25))
    .bind("price", JsonTValue::d64(9.99));

let result = adult.evaluate(&ctx)?;  // JsonTValue::Bool(true)
```

---

## Error handling

All public functions return `Result<_, JsonTError>`, which wraps per-layer
sub-errors:

```rust
use jsont::{JsonTError, ParseError, EvalError, TransformError};

match err {
    JsonTError::Parse(ParseError::Pest(msg))         => { /* grammar error with position */ }
    JsonTError::Parse(ParseError::UnknownSchemaRef(name)) => { /* schema reference not found */ }

    JsonTError::Eval(EvalError::UnboundField(name))  => { /* expression field not in context */ }
    JsonTError::Eval(EvalError::TypeMismatch { .. })  => { /* operator type error */ }

    JsonTError::Transform(TransformError::Filtered)              => { /* row excluded by filter */ }
    JsonTError::Transform(TransformError::FieldNotFound(msg))    => { /* field missing */ }
    JsonTError::Transform(TransformError::UnknownSchema(name))   => { /* parent not in registry */ }
    JsonTError::Transform(TransformError::CyclicDerivation(chain)) => { /* A → B → A */ }
    JsonTError::Transform(TransformError::FilterFailed(eval_err))  => { /* filter eval error */ }
    JsonTError::Transform(TransformError::TransformFailed { field, source }) => { /* … */ }
    JsonTError::Transform(TransformError::DecryptFailed { field, reason }) => {
        // Decrypt operation failed — no CryptoConfig supplied, or crypto.decrypt() returned error
    }

    JsonTError::Stringify(_) => { /* serialisation error */ }
}
```

`TransformError::Filtered` is a **row-skip signal**, not a hard failure.
It means a filter predicate evaluated to false and the caller should skip the
row and continue processing the stream.

---

## Performance notes

| Scenario | Memory | Notes |
|---|---|---|
| Streaming parse from string (`parse_rows`) | O(1) rows | Byte scanner; no parse tree |
| Streaming parse from file (`parse_rows_streaming` / `RowIter`) | O(buf + 1 row) ≈ 14 KB | `BufRead::fill_buf()` + `consume()`; file size irrelevant |
| Streaming validation (`validate_each`, constraints + rules only) | O(1) | Per-row evaluation |
| Streaming validation with uniqueness | O(unique-keys) | Only key strings accumulate, not row data |
| Parallel validate pipeline (`validate_one` + `sync_channel`) | O(channel_cap) | Back-pressured channel; N−1 worker threads |
| Buffered validation (`validate_rows`) | O(clean-rows) | Output Vec allocation |
| Streaming write (`write_row` / `write_rows`) | O(1) per row | Writes directly to `impl Write` |

For large datasets, prefer:

1. `parse_rows_streaming` (or `RowIter`) over `parse_rows` when reading from a file — constant ~14 KB vs O(file_size)
2. `validate_each(RowIter::new(...), ...)` for single-threaded O(1) end-to-end
3. Channel pipeline with `validate_one` for multi-core throughput on 1M+ rows
4. `write_row` / `write_rows` with a `BufWriter` in a streaming loop for O(1) stringify

The validation sink thread runs on a separate OS thread so sink I/O
(file writes, console output) never stalls the hot validation loop.

### Measured parallel validation throughput

The parallel pipeline (`validate_one` + `sync_channel(2048)`, `N−1` workers) was
benchmarked on the marketplace `Order` schema (9 typed fields, cross-row uniqueness,
validation rules) on a real dataset file:

| Rows | Throughput | Peak heap |
|---|---|---|
| 1 M  | 39.6 K rows/s  | — |
| 10 M | 66.9 K rows/s  | 813.81 KB |

At 10 M rows the whole pipeline — read, parse, constraint check, uniqueness tracking,
and sink dispatch — uses under 1 MB of heap.  The channel back-pressure
(`sync_channel(2048)`) keeps the producer from racing ahead, which is why memory
stays flat regardless of dataset size.

> Use the parallel pipeline whenever you need to validate large files (1 M+ rows)
> and have spare CPU cores.  The code pattern is shown in the
> [Streaming — rows from a file](#streaming--rows-from-a-file-o1-memory) section above.
