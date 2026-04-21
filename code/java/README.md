# jsont (Java)

A schema-driven, positional data language for Java 17.

JsonT separates field declarations (defined once in a schema) from data (encoded as compact positional tuples). The result is a format that is more concise than JSON, strictly typed, and designed for high-throughput pipelines.

---

## Contents

1. [Core concepts](#core-concepts)
2. [Building schemas](#building-schemas)
3. [Parsing](#parsing)
4. [Stringification](#stringification)
5. [Validation](#validation)
6. [Derived schemas and transforms](#derived-schemas-and-transforms)
7. [Polymorphic fields (anyOf)](#polymorphic-fields-anyof)
8. [Privacy & Encryption](#privacy--encryption)
9. [Expression building](#expression-building)
10. [Error handling](#error-handling)
11. [Performance notes](#performance-notes)

---

## Core concepts

| Class | Description |
|---|---|
| `JsonTSchema` | Declares a named set of typed fields (`STRAIGHT`) or derives from another schema (`DERIVED`). |
| `JsonTRow` | One data record — an ordered list of `JsonTValue` whose positions map to schema fields. |
| `JsonTNamespace` | Top-level container: one or more `JsonTCatalog`s, each holding schemas and enums. |
| `ValidationPipeline` | Validates rows against a schema's field constraints and rules; routes diagnostics to sinks. |
| `RowWriter` | Schema-bound writer; handles plain and encrypted streams transparently. |
| `JsonT` | Static entry point for parse, stringify, and write operations. |

A `JsonTRow` is positional, not named. Field 0 corresponds to schema field 0, field 1 to schema field 1, and so on. Names exist only in the schema.

---

## Building schemas

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
            .unique("id")
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

Object, array, and polymorphic fields:

```java
// Scalar array field
JsonTFieldBuilder.scalar("tags", ScalarType.STR)
    .asArray().optional().minItems(1).maxItems(10);

// Object field (references another schema by name)
JsonTFieldBuilder.object("address", "Address");

// Polymorphic field (anyOf — resolved by discriminator at parse/validate time)
JsonTFieldBuilder.anyOf("payload", List.of("OrderPayload", "ReturnPayload"));
```

---

## Parsing

### Parse a namespace DSL document

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
```

### Parse data rows — in-memory string

```java
String data = "{1,\"Widget A\",10,9.99},{2,\"Widget B\",5,24.50}";

List<JsonTRow> rows = new ArrayList<>();
int count = JsonT.parseRows(data, rows::add);
```

### Parse data rows — streaming from a file

```java
try (var reader = new BufferedReader(new FileReader("orders.jsont"))) {
    int count = JsonT.parseRowsStreaming(reader, row -> {
        System.out.printf("row has %d fields%n", row.size());
    });
}
```

### Lazy row iterator

```java
try (RowIter iter = JsonT.rowIter(new BufferedReader(new FileReader("orders.jsont")))) {
    for (JsonTRow row : iter) {
        System.out.println(row);
    }
}
```

---

## Stringification

```java
import io.github.datakore.jsont.JsonT;
import io.github.datakore.jsont.stringify.StringifyOptions;

String compact = JsonT.stringify(schema);
String pretty  = JsonT.stringify(schema, StringifyOptions.pretty());
String nsText  = JsonT.stringify(namespace);
String wire    = JsonT.stringifyRow(row);   // "{1,\"Widget A\",10,9.99}"
```

### Zero-allocation row writers

```java
import io.github.datakore.jsont.JsonT;
import java.io.*;

// Schema-free (plain rows only)
try (var sw = new StringWriter()) {
    JsonT.writeRow(row, sw);
}

// Schema-bound (handles encryption transparently — see Privacy section)
try (CryptoContext ctx = CryptoContext.forEncrypt(AlgoVersion.AES_GCM, KekMode.PUBLIC_KEY, cfg);
     RowWriter writer = new RowWriter(schema, ctx, registry)) {
    writer.writeStream(rows, out);
}
```

---

## Validation

`ValidationPipeline` validates `JsonTRow` values against field-level constraints, row-level rules, and cross-row uniqueness constraints.

If the schema contains sensitive (`~`) fields, a `CryptoContext` must be supplied at build time — the builder throws `IllegalStateException` otherwise.

### Streaming mode (O(1) memory)

```java
import io.github.datakore.jsont.diagnostic.MemorySink;
import io.github.datakore.jsont.validate.*;

MemorySink sink = new MemorySink();
ValidationPipeline pipeline = new ValidationPipelineBuilder(orderSchema)
    .withoutConsole()
    .withSink(sink)
    .build();

pipeline.validateEach(parsedRows, cleanRow -> { /* emit */ });
pipeline.finish();
```

### Buffered mode

```java
List<JsonTRow> clean = pipeline.validateRows(parsedRows);
pipeline.finish();
```

### Parallel mode

```java
ValidationPipeline pipeline = new ValidationPipelineBuilder(orderSchema)
    .withoutConsole()
    .workers(Runtime.getRuntime().availableProcessors())
    .bufferCapacity(256)
    .build();

Stream<JsonTRow> cleanStream = pipeline.validateStream(rowStream);
```

---

## Derived schemas and transforms

A derived schema inherits its parent's field set and applies an ordered list of operations.

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
| `SchemaOperation.Decrypt` | `SchemaOperation.decrypt(fields...)` | Decrypt named sensitive fields in-place |

```java
JsonTSchema view = JsonTSchemaBuilder.derived("OrderView", "Order")
    .operation(SchemaOperation.exclude(FieldPath.single("quantity")))
    .operation(SchemaOperation.rename(RenamePair.of("price", "amount")))
    .operation(SchemaOperation.filter(
        JsonTExpression.binary(BinaryOp.GT,
            JsonTExpression.fieldName("amount"),
            JsonTExpression.literal(JsonTValue.d64(0.0)))))
    .build();
```

`SchemaOperation.Filter` raises `JsonTError.Transform.Filtered` as a row-skip signal — not a hard failure.

---

## Polymorphic fields (anyOf)

A field declared as `anyOf` accepts values from any of a listed set of schema variants. The correct variant is resolved by a discriminator field at parse/validate time.

### DSL syntax

```jsont
Event: {
  fields: {
    str: type,
    anyOf(OrderEvent, ReturnEvent): payload
  }
}
```

### Programmatic

```java
JsonTSchema eventSchema = JsonTSchemaBuilder.straight("Event")
    .fieldFrom(JsonTFieldBuilder.scalar("type", ScalarType.STR))
    .fieldFrom(JsonTFieldBuilder.anyOf("payload", List.of("OrderEvent", "ReturnEvent")))
    .build();
```

At validate time the pipeline resolves which variant the `payload` field carries, validates it against that variant's schema, and stores the result as a typed `JsonTValue`.

---

## Privacy & Encryption

JsonT supports field-level encryption through **privacy markers** (`~`) in the schema DSL and a pluggable `CryptoConfig` interface.

### Marking sensitive fields

```jsont
Person: {
  fields: {
    str:  name,
    ~str: ssn,
    ~str: cardNumber?
  }
}
```

```java
JsonTSchema schema = JsonTSchemaBuilder.straight("Person")
    .fieldFrom(JsonTFieldBuilder.scalar("name", ScalarType.STR))
    .fieldFrom(JsonTFieldBuilder.scalar("ssn",  ScalarType.STR).sensitive())
    .build();
```

### Schema-bound write (encrypt on output)

`RowWriter` is schema-bound. If any field in the schema is sensitive, a `CryptoContext` is required — the constructor throws `IllegalArgumentException` otherwise. The same `writeStream` call handles both plain and encrypted schemas.

```java
import io.github.datakore.jsont.crypto.*;
import io.github.datakore.jsont.stringify.RowWriter;

// RSA-OAEP key wrapping
CryptoConfig cfg = new PublicKeyCryptoConfig("JSONT_PUBLIC_KEY", "JSONT_PRIVATE_KEY");

// Or supply keys directly (useful in tests)
CryptoConfig cfg = PublicKeyCryptoConfig.ofKeys(publicKeyPem, privateKeyPem);

// ECDH-derived KEK (peer's DER public key + host's env-var private key)
CryptoConfig cfg = new EcdhCryptoConfig(peerPublicKeyDer, "JSONT_HOST_PRIV");

// Or supply the private key directly
CryptoConfig cfg = EcdhCryptoConfig.ofKeys(peerPublicKeyDer, hostPrivKeyPem);

try (CryptoContext ctx = CryptoContext.forEncrypt(AlgoVersion.AES_GCM, KekMode.PUBLIC_KEY, cfg);
     RowWriter writer = new RowWriter(schema, ctx)) {
    writer.writeStream(rows, out);
}
```

### Supported algorithms

| `AlgoVersion` | `KekMode` | Field cipher | DEK wrapping |
|---|---|---|---|
| `AES_GCM` | `PUBLIC_KEY` | AES-256-GCM | RSA-OAEP-SHA256 |
| `AES_GCM` | `ECDH` | AES-256-GCM | ECDH+HKDF-SHA256 |
| `CHACHA20_POLY1305` | `PUBLIC_KEY` | ChaCha20-Poly1305 | RSA-OAEP-SHA256 |
| `CHACHA20_POLY1305` | `ECDH` | ChaCha20-Poly1305 | ECDH+HKDF-SHA256 |
| `ASCON` | `PUBLIC_KEY` | ASCON-128a | RSA-OAEP-SHA256 |
| `ASCON` | `ECDH` | ASCON-128a | ECDH+HKDF-SHA256 |

### CryptoConfig implementations

| Class | Description |
|---|---|
| `PublicKeyCryptoConfig` | RSA-OAEP-SHA256 DEK wrap/unwrap via env-var keys or `ofKeys(pubPem, privPem)` |
| `EcdhCryptoConfig` | ECDH P-256 + HKDF-SHA256 via `new EcdhCryptoConfig(peerDer, envVar)` or `ofKeys(peerDer, privPem)` |
| `PassthroughCryptoConfig` | Identity (tests only — no real crypto) |

### Reading (decrypt on input)

```java
CryptoConfig cfg = PublicKeyCryptoConfig.ofKeys(publicKeyPem, privateKeyPem);

// forDecrypt unwraps the DEK from the EncryptHeader row immediately (fail-fast)
try (CryptoContext ctx = CryptoContext.forDecrypt(versionFromWire, encDekFromWire, cfg)) {
    // Use transformWithContext to decrypt inside a derived schema pipeline
    JsonTRow result = RowTransformer.of(derivedSchema, registry)
            .transformWithContext(row, ctx);

    // Or decrypt a single field on-demand
    Optional<String> ssn = row.decryptField(1, "ssn", ctx);
}
```

### Decrypt in a derived schema pipeline

```java
JsonTSchema derived = JsonTSchemaBuilder.derived("PersonDecrypted", "Person")
    .operation(SchemaOperation.decrypt("ssn"))
    .build();

JsonTRow result = RowTransformer.of(derived, registry)
        .transformWithContext(row, ctx);
// result.get(1) is now JsonTValue.text("123-45-6789")
```

### On-demand decrypt

```java
// Decrypt a value directly
JsonTValue val = JsonTValue.encrypted(envelope);
Optional<String>  text  = val.decryptStr("field_name", ctx);
Optional<byte[]>  bytes = val.decryptBytes("field_name", ctx);

// Decrypt by position in a row
Optional<String> ssn = row.decryptField(1, "ssn", ctx);
```

### ValidationPipeline with encryption

If the schema has sensitive fields, supply a `CryptoContext` before calling `build()`:

```java
try (CryptoContext ctx = CryptoContext.forDecrypt(version, encDek, cfg)) {
    ValidationPipeline pipeline = new ValidationPipelineBuilder(schema)
        .withoutConsole()
        .withCryptoContext(ctx)
        .build();

    pipeline.validateEach(rows, cleanRow -> { /* emit */ });
    pipeline.finish();
}
```

---

## Expression building

```java
import io.github.datakore.jsont.model.*;

// age >= 18
JsonTExpression adult = JsonTExpression.binary(
    BinaryOp.GE,
    JsonTExpression.fieldName("age"),
    JsonTExpression.literal(JsonTValue.i32(18)));

// !(active)
JsonTExpression inactive = JsonTExpression.not(
    JsonTExpression.fieldName("active"));

// price * quantity (used in a Transform)
JsonTExpression total = JsonTExpression.binary(
    BinaryOp.MUL,
    JsonTExpression.fieldName("price"),
    JsonTExpression.fieldName("quantity"));
```

---

## Error handling

```java
import io.github.datakore.jsont.error.*;
import io.github.datakore.jsont.crypto.CryptoError;

// Parse errors
try {
    JsonTNamespace ns = JsonT.parseNamespace(badInput);
} catch (JsonTError.Parse e) { ... }

// Build errors
try {
    JsonTSchema schema = JsonTSchemaBuilder.straight("Order").build(); // missing fields
} catch (BuildError e) { ... }

// RowWriter construction guard — thrown immediately, not at write time
try {
    new RowWriter(sensitiveSchema);           // throws — no CryptoContext
} catch (IllegalArgumentException e) { ... }

// ValidationPipelineBuilder guard — thrown at build(), not at validate time
try {
    new ValidationPipelineBuilder(sensitiveSchema).build(); // throws — no CryptoContext
} catch (IllegalStateException e) { ... }

// Crypto errors
try {
    Optional<String> plain = row.decryptField(1, "ssn", ctx);
} catch (CryptoError e) { ... }

// Decrypt operation without CryptoContext
try {
    RowTransformer.of(derivedSchema, registry).transform(row);
} catch (JsonTError.Transform.DecryptFailed e) { ... }
```

---

## Performance notes

| Scenario | Memory | Notes |
|---|---|---|
| Parse rows from string (`parseRows`) | O(1) rows | `RowScanner` state machine; no parse tree |
| Parse rows from file (`parseRowsStreaming` / `rowIter`) | O(64 KB buf + 1 row) | Bulk character buffer |
| Streaming validation (`validateEach`) | O(1) | Per-row evaluation |
| Streaming validation with uniqueness | O(unique-keys) | Only key strings accumulate |
| Parallel validation (`validateStream`) | O(channel_cap) | Bounded queue; N worker threads |
| Buffered validation (`validateRows`) | O(clean-rows) | Output List allocation |
| Streaming write (`writeStream`) | O(1) per row | Writes directly to `Writer` |

### Measured throughput (complex marketplace schema, 40 fields, validation rules)

| Rows | Parse | Parse + Validate | Parse + Validate + Transform |
|---|---|---|---|
| 1 M | 131 rows/ms | 84 rows/ms | 32 rows/ms |
| 10 M | 131 rows/ms | 78 rows/ms | 33 rows/ms |

See [Performance.md](../../docs/Performance.md) for full benchmark details.
