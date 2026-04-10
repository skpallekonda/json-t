# jsont (Java)

A schema-driven, positional data language for Java 17.

JsonT separates field declarations (defined once in a schema) from data (encoded as compact positional tuples). The result is a format that is more concise than JSON, strictly typed, and designed for high-throughput pipelines.

---

## Contents

1. [Core concepts](#core-concepts)
2. [Building schemas](#building-schemas)
3. [Parsing](#parsing)
   - [Parse a namespace DSL document](#parse-a-namespace-dsl-document)
   - [Parse data rows — in-memory string](#parse-data-rows--in-memory-string)
   - [Parse data rows — streaming from a file](#parse-data-rows--streaming-from-a-file)
   - [Lazy row iterator](#lazy-row-iterator)
4. [Stringification](#stringification)
5. [Validation](#validation)
   - [Streaming mode](#streaming-mode-o1-memory)
   - [Buffered mode](#buffered-mode)
   - [Parallel mode](#parallel-mode)
   - [Custom sinks](#custom-sinks)
6. [Derived schemas and transforms](#derived-schemas-and-transforms)
7. [Privacy & Encryption](#privacy--encryption)
8. [Expression building](#expression-building)
9. [Error handling](#error-handling)
10. [Performance notes](#performance-notes)

---

## Core concepts

| Class | Description |
|---|---|
| `JsonTSchema` | Declares a named set of typed fields (`STRAIGHT`) or derives from another schema (`DERIVED`). |
| `JsonTRow` | One data record — an ordered list of `JsonTValue` whose positions map to schema fields. |
| `JsonTNamespace` | Top-level container: one or more `JsonTCatalog`s, each holding schemas and enums. |
| `ValidationPipeline` | Validates rows against a schema's field constraints and rules; routes diagnostics to sinks. |
| `JsonT` | Static entry point for parse, stringify, and write operations. |

A `JsonTRow` is positional, not named. Field 0 corresponds to schema field 0, field 1 to schema field 1, and so on. Names exist only in the schema.

---

## Building schemas

Use the fluent builders to construct schemas programmatically.

```java
import io.github.datakore.jsont.builder.JsonTFieldBuilder;
import io.github.datakore.jsont.builder.JsonTSchemaBuilder;
import io.github.datakore.jsont.builder.JsonTValidationBlockBuilder;
import io.github.datakore.jsont.model.*;

// ── Straight schema: declare fields explicitly ────────────────────────────────

JsonTSchema orderSchema = JsonTSchemaBuilder.straight("Order")
    .fieldFrom(JsonTFieldBuilder.scalar("id",       ScalarType.I64))
    .fieldFrom(JsonTFieldBuilder.scalar("product",  ScalarType.STR).minLength(2))
    .fieldFrom(JsonTFieldBuilder.scalar("quantity", ScalarType.I32).minValue(1).maxValue(999))
    .fieldFrom(JsonTFieldBuilder.scalar("price",    ScalarType.D64).minValue(0.01))
    .validationFrom(
        JsonTValidationBlockBuilder.create()
            .unique("id")           // id must be unique across rows
            .rule(/* expression */)
    )
    .build();

// ── Derived schema: inherit fields and apply operations ───────────────────────

JsonTSchema summarySchema = JsonTSchemaBuilder.derived("OrderSummary", "Order")
    .operation(SchemaOperation.project(
        FieldPath.single("id"),
        FieldPath.single("product"),
        FieldPath.single("price")))
    .operation(SchemaOperation.filter(
        JsonTExpression.binary(BinaryOp.GT,
            JsonTExpression.fieldName("price"),
            JsonTExpression.literal(JsonTValue.d64(10.0)))))
    .build();
```

### Available field types

```java
ScalarType.I16   ScalarType.I32   ScalarType.I64
ScalarType.U16   ScalarType.U32   ScalarType.U64
ScalarType.D32   ScalarType.D64   ScalarType.D128
ScalarType.BOOL
ScalarType.STR   ScalarType.NSTR
ScalarType.UUID  ScalarType.URI   ScalarType.EMAIL
ScalarType.HOSTNAME  ScalarType.IPV4  ScalarType.IPV6
ScalarType.DATE  ScalarType.TIME  ScalarType.DATETIME
ScalarType.TIMESTAMP  ScalarType.TSZ  ScalarType.DURATION  ScalarType.INST
ScalarType.BASE64  ScalarType.OID  ScalarType.HEX
```

Object and array fields:

```java
// Scalar array field
JsonTFieldBuilder.scalar("tags", ScalarType.STR)
    .asArray()
    .optional()
    .minItems(1)
    .maxItems(10);

// Object field (references another schema by name)
JsonTFieldBuilder.object("address", "Address");
```

---

## Parsing

### Parse a namespace DSL document

Parse a complete JsonT source file (namespace block) using ANTLR4.

```java
import io.github.datakore.jsont.JsonT;
import io.github.datakore.jsont.model.JsonTNamespace;

String source = """
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
                  str: product [(minLength=2)],
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
    """;

JsonTNamespace ns = JsonT.parseNamespace(source);
// Throws JsonTError.Parse on invalid input
```

### Parse data rows — in-memory string

`JsonT.parseRows` uses a hand-written `RowScanner` state machine. It calls a `RowConsumer` for each completed `JsonTRow` without building an intermediate parse tree, making memory use O(1) in the number of rows.

```java
import io.github.datakore.jsont.JsonT;
import io.github.datakore.jsont.model.JsonTRow;
import java.util.ArrayList;
import java.util.List;

String data = "{1,\"Widget A\",10,9.99},{2,\"Widget B\",5,24.50}";

List<JsonTRow> rows = new ArrayList<>();
int count = JsonT.parseRows(data, rows::add);
System.out.printf("parsed %d rows%n", count);

// Or process inline without collecting:
JsonT.parseRows(data, row -> System.out.printf("row has %d fields%n", row.size()));
```

### Parse data rows — streaming from a file

`JsonT.parseRowsStreaming` accepts any `Reader`. It uses a 64 KB bulk buffer internally. Peak memory stays at O(1) regardless of file size — a 5 GB file parses with the same overhead as a 5 KB file.

```java
import io.github.datakore.jsont.JsonT;
import java.io.*;

try (var reader = new BufferedReader(new FileReader("orders.jsont"))) {
    int count = JsonT.parseRowsStreaming(reader, row -> {
        // each row is processed and discarded — no accumulation
        System.out.printf("row has %d fields%n", row.size());
    });
    System.out.printf("parsed %d rows%n", count);
}
```

### Lazy row iterator

`JsonT.rowIter` returns a `RowIter` that implements both `Iterator<JsonTRow>` and `AutoCloseable`. Rows are produced on demand — suitable for use with `for`-each loops or APIs that accept `Iterable`.

```java
import io.github.datakore.jsont.JsonT;
import io.github.datakore.jsont.parse.RowIter;
import java.io.*;

try (RowIter iter = JsonT.rowIter(new BufferedReader(new FileReader("orders.jsont")))) {
    for (JsonTRow row : iter) {
        System.out.println(row);
    }
}
```

**Direct parse + validate in one streaming pass (O(1) memory):**

```java
import io.github.datakore.jsont.JsonT;
import io.github.datakore.jsont.validate.ValidationPipeline;
import io.github.datakore.jsont.validate.ValidationPipelineBuilder;
import java.io.*;

ValidationPipeline pipeline = new ValidationPipelineBuilder(orderSchema)
    .withoutConsole()
    .build();

try (var iter = JsonT.rowIter(new BufferedReader(new FileReader("orders.jsont")))) {
    pipeline.validateEach(iter, cleanRow -> { /* emit clean rows */ });
}
pipeline.finish();
```

---

## Stringification

Convert model types back to JsonT source text.

```java
import io.github.datakore.jsont.JsonT;
import io.github.datakore.jsont.stringify.StringifyOptions;

// Compact (single-line)
String compact = JsonT.stringify(schema);

// Pretty-printed
String pretty  = JsonT.stringify(schema, StringifyOptions.pretty());

// Compact namespace
String nsText  = JsonT.stringify(namespace);

// Serialize a row
String wire    = JsonT.stringifyRow(row);   // "{1,\"Widget A\",10,9.99}"
```

### Zero-allocation row writers

For high-throughput output, write rows directly to any `java.io.Writer` without intermediate `String` allocation.

```java
import io.github.datakore.jsont.JsonT;
import java.io.*;

// Write a single row
try (var sw = new StringWriter()) {
    JsonT.writeRow(row, sw);
    System.out.println(sw);     // {1,"Widget A",10,9.99}
}

// Write many rows to a file, separated by ",\n"
try (var writer = new BufferedWriter(new FileWriter("output.jsont"))) {
    JsonT.writeRows(rows, writer);
}
```

---

## Validation

`ValidationPipeline` validates `JsonTRow` values against:

- Field-level constraints (required, value bounds, length, pattern, …)
- Row-level rules from the schema's `validations` block
- Cross-row uniqueness constraints

Diagnostics are routed to registered sinks. The default sink prints to the console.

### Streaming mode (O(1) memory)

```java
import io.github.datakore.jsont.diagnostic.MemorySink;
import io.github.datakore.jsont.validate.*;

MemorySink sink = new MemorySink();
ValidationPipeline pipeline = new ValidationPipelineBuilder(orderSchema)
    .withoutConsole()
    .withSink(sink)
    .build();

// validateEach emits each clean row immediately — no buffering of clean rows.
pipeline.validateEach(parsedRows, cleanRow -> {
    // write to output, feed into next stage, etc.
});

pipeline.finish();

// Inspect collected diagnostics
sink.events().forEach(System.out::println);
```

### Buffered mode

```java
// Collect all clean rows into a List — O(clean-rows) memory.
List<JsonTRow> clean = pipeline.validateRows(parsedRows);
pipeline.finish();
```

### Parallel mode

For large files, `validateStream` distributes work across multiple worker threads backed by a bounded queue.

```java
import io.github.datakore.jsont.validate.*;
import java.util.stream.Stream;

ValidationPipeline pipeline = new ValidationPipelineBuilder(orderSchema)
    .withoutConsole()
    .workers(Runtime.getRuntime().availableProcessors())
    .bufferCapacity(256)
    .build();

// validateStream accepts a java.util.stream.Stream<JsonTRow>
Stream<JsonTRow> cleanStream = pipeline.validateStream(rowStream);
cleanStream.forEach(row -> { /* consume clean rows */ });

pipeline.finish();
```

### Custom sinks

Implement `DiagnosticSink` to route events anywhere — a database, a log aggregator, a file, etc.

```java
import io.github.datakore.jsont.diagnostic.DiagnosticSink;
import io.github.datakore.jsont.diagnostic.DiagnosticEvent;
import java.io.*;

class JsonlSink implements DiagnosticSink {
    private final PrintWriter writer;

    JsonlSink(Path file) throws IOException {
        this.writer = new PrintWriter(new BufferedWriter(new FileWriter(file.toFile())));
    }

    @Override
    public void emit(DiagnosticEvent event) {
        writer.println(event.toString());
    }

    @Override
    public void flush() {
        writer.flush();
    }

    @Override
    public void close() {
        writer.close();
    }
}

ValidationPipeline pipeline = new ValidationPipelineBuilder(orderSchema)
    .withoutConsole()
    .withSink(new JsonlSink(Path.of("errors.jsonl")))
    .build();
```

Built-in sinks: `ConsoleSink` (default), `MemorySink`.

---

## Derived schemas and transforms

A derived schema inherits its parent's field set and applies an ordered list of operations. Operations run left-to-right per row.

```
Order (Straight)      id, product, quantity, price
  └─ OrderSummary (Derived)   Project([id, product, price])   → id, product, price
       └─ PriceView (Derived) Rename(price → amount)         → id, product, amount
```

### Available operations

| Class | Factory method | Effect |
|---|---|---|
| `SchemaOperation.Rename` | `SchemaOperation.rename(pairs...)` | Rename one or more fields |
| `SchemaOperation.Exclude` | `SchemaOperation.exclude(paths...)` | Drop listed fields |
| `SchemaOperation.Project` | `SchemaOperation.project(paths...)` | Keep only listed fields |
| `SchemaOperation.Filter` | `SchemaOperation.filter(predicate)` | Drop rows where predicate is false |
| `SchemaOperation.Transform` | `SchemaOperation.transform(field, expr)` | Replace a field's value |

```java
import io.github.datakore.jsont.model.*;

// Exclude quantity, rename price → amount, filter rows where amount > 0
JsonTSchema view = JsonTSchemaBuilder.derived("OrderView", "Order")
    .operation(SchemaOperation.exclude(FieldPath.single("quantity")))
    .operation(SchemaOperation.rename(RenamePair.of("price", "amount")))
    .operation(SchemaOperation.filter(
        JsonTExpression.binary(BinaryOp.GT,
            JsonTExpression.fieldName("amount"),
            JsonTExpression.literal(JsonTValue.d64(0.0)))))
    .build();
```

`SchemaOperation.Filter` is a **row-skip signal**, not a hard failure. When a row is filtered out, callers should skip it and continue.

---

## Privacy & Encryption

JsonT supports field-level encryption through **privacy markers** (`~`) in the schema DSL and a pluggable `CryptoConfig` interface.

### Marking sensitive fields

In a DSL schema, prefix the type with `~` for any field that must be encrypted on the wire:

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

```java
import io.github.datakore.jsont.builder.*;
import io.github.datakore.jsont.model.*;

JsonTSchema schema = JsonTSchemaBuilder.straight("Person")
    .fieldFrom(JsonTFieldBuilder.scalar("name", ScalarType.STR))
    .fieldFrom(JsonTFieldBuilder.scalar("ssn",  ScalarType.STR).sensitive())
    .build();
```

### Schema-aware write (encrypt on output)

Use `RowWriter.writeRow(row, fields, crypto, writer)` instead of the plain `writeRow` overload when a schema is available. Sensitive fields holding plain text are encrypted; already-encrypted fields are re-encoded as `base64:` without another crypto call.

```java
import io.github.datakore.jsont.crypto.*;
import io.github.datakore.jsont.stringify.RowWriter;
import java.io.StringWriter;

CryptoConfig crypto = new PassthroughCryptoConfig();   // replace with real implementation
StringWriter out = new StringWriter();
RowWriter.writeRow(row, schema.fields(), crypto, out);
// SSN column → "base64:<b64>"; name column → plain string
```

Implement `CryptoConfig` for your key-management strategy:

```java
import io.github.datakore.jsont.crypto.*;

class AesGcmCrypto implements CryptoConfig {
    @Override
    public byte[] encrypt(String field, byte[] plaintext) throws CryptoError {
        // AES-GCM encrypt using field name for key selection
        throw new UnsupportedOperationException("implement me");
    }

    @Override
    public byte[] decrypt(String field, byte[] ciphertext) throws CryptoError {
        throw new UnsupportedOperationException("implement me");
    }
}
```

### Decrypt in a derived schema pipeline

Add a `Decrypt` operation to a derived schema to decrypt fields as part of the transform:

```java
import io.github.datakore.jsont.model.SchemaOperation;
import io.github.datakore.jsont.transform.RowTransformer;

JsonTSchema derived = JsonTSchemaBuilder.derived("PersonDecrypted", "Person")
    .operation(SchemaOperation.decrypt("ssn"))
    .build();

SchemaRegistry registry = SchemaRegistry.empty()
    .register(personSchema)
    .register(derived);

// Use transformWithCrypto — transform() without crypto throws DecryptFailed
JsonTRow result = RowTransformer.of(derived, registry)
    .transformWithCrypto(row, new PassthroughCryptoConfig());
// result.get(1) is now JsonTValue.text("123-45-6789")
```

### On-demand decrypt

Decrypt a single field without deriving a new schema:

```java
import io.github.datakore.jsont.crypto.*;
import io.github.datakore.jsont.model.*;

CryptoConfig crypto = new PassthroughCryptoConfig();

// Decrypt a value directly
JsonTValue val = JsonTValue.encrypted("hello".getBytes());
Optional<String>  text  = val.decryptStr("field_name", crypto);
Optional<byte[]>  bytes = val.decryptBytes("field_name", crypto);

// Decrypt by position in a row (throws IndexOutOfBoundsException for out-of-range)
JsonTRow row = JsonTRow.of(
    JsonTValue.text("Alice"),
    JsonTValue.encrypted("123-45-6789".getBytes())
);
Optional<String> ssn = row.decryptField(1, "ssn", crypto);
```

---

## Expression building

`JsonTExpression` is used in validation rules, filter predicates, and transform expressions.

```java
import io.github.datakore.jsont.model.*;

// age >= 18
JsonTExpression adult = JsonTExpression.binary(
    BinaryOp.GE,
    JsonTExpression.fieldName("age"),
    JsonTExpression.literal(JsonTValue.i32(18)));

// !(active) — logical NOT
JsonTExpression inactive = JsonTExpression.not(
    JsonTExpression.fieldName("active"));

// price * quantity  (used in a Transform)
JsonTExpression total = JsonTExpression.binary(
    BinaryOp.MUL,
    JsonTExpression.fieldName("price"),
    JsonTExpression.fieldName("quantity"));
```

---

## Error handling

Parse errors throw `JsonTError.Parse`. Build errors throw `BuildError`.

```java
import io.github.datakore.jsont.error.*;

try {
    JsonTNamespace ns = JsonT.parseNamespace(badInput);
} catch (JsonTError.Parse e) {
    System.err.println("Parse error: " + e.getMessage());
}

try {
    JsonTSchema schema = JsonTSchemaBuilder.straight("Order")
        // missing fields — will throw
        .build();
} catch (BuildError e) {
    System.err.println("Build error: " + e.getMessage());
}

// Crypto errors — checked exception thrown by CryptoConfig.encrypt/decrypt
try {
    Optional<String> plain = row.decryptField(1, "ssn", crypto);
} catch (CryptoError e) {
    System.err.println("Decryption failed: " + e.getMessage());
}

// Decrypt operation without CryptoConfig — unchecked Transform error
try {
    JsonTRow result = RowTransformer.of(derivedSchema, registry).transform(row);
} catch (JsonTError.Transform.DecryptFailed e) {
    System.err.println("Decrypt op requires CryptoConfig: " + e.getMessage());
}
```

---

## Performance notes

| Scenario | Memory | Notes |
|---|---|---|
| Parse rows from string (`parseRows`) | O(1) rows | `RowScanner` state machine; no parse tree |
| Parse rows from file (`parseRowsStreaming` / `rowIter`) | O(64 KB buf + 1 row) | Bulk character buffer; file size irrelevant |
| Streaming validation (`validateEach`) | O(1) | Per-row evaluation |
| Streaming validation with uniqueness | O(unique-keys) | Only key strings accumulate |
| Parallel validation (`validateStream`) | O(channel_cap) | Bounded queue; N worker threads |
| Buffered validation (`validateRows`) | O(clean-rows) | Output List allocation |
| Streaming write (`writeRow` / `writeRows`) | O(1) per row | Writes directly to `Writer` |

For large datasets, prefer:

1. `parseRowsStreaming` (or `rowIter`) over `parseRows` when reading from a file — constant 64 KB overhead vs O(file size)
2. `pipeline.validateEach(rowIter(...), ...)` for single-threaded O(1) end-to-end
3. `pipeline.validateStream(stream)` with multiple workers for multi-core throughput on 1 M+ rows
4. `writeRow` / `writeRows` with a `BufferedWriter` in a streaming loop for O(1) serialization

### Measured throughput (complex marketplace schema, 40 fields, validation rules)

| Rows | Parse | Parse + Validate | Parse + Validate + Transform |
|---|---|---|---|
| 1 M | 131 rows/ms | 84 rows/ms | 32 rows/ms |
| 10 M | 131 rows/ms | 78 rows/ms | 33 rows/ms |

At 10 M rows the pipeline operates without memory growth.
See [Performance.md](../../docs/Performance.md) for full benchmark details.
