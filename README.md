# JSON-T

**JSON-T** is a **schema-driven, positional data language** designed to reduce payload size and enforce strong,
symmetric data validation — especially for **large datasets, batch APIs, and streaming use cases**.

Unlike JSON, where field names are repeated in every record, JSON-T defines structure once using a schema and encodes
data as compact positional tuples.

---

## Why JSON-T?

JSON is flexible and human-readable, but it has well-known limitations at scale:

- Repeated field names inflate payload size
- Validation is external, duplicated, and inconsistent
- Schema drift is hard to detect
- Streaming validation is inefficient

JSON-T addresses these problems directly.

---

## Design Inspiration

JSON-T combines the best properties of existing data formats while avoiding their limitations:

- **JSON**: Retains support for nested structures, complex objects, and arrays.
- **CSV**: Adopts positional fields and removes repeating identifiers to significantly reduce payload size.
- **Protocol Buffers**: Utilizes multiple scalar types (boolean, number, string) with strict typing.
- **Apache Avro**: Supports embedding schemas alongside data for self-describing catalogs.
- **BSON**: Provides a foundation for rich data types and efficient traversal.

---

## Key Features

- **Schema-first design** — Structure is defined once and reused for all data.
- **Positional encoding** — Data is encoded as ordered tuples, eliminating repeated keys.
- **Strong validation** — Types, optionality, constraints, and nullability enforced by the language.
- **Smaller payloads** — Typically 45–55% smaller than JSON for large datasets.
- **Streaming-friendly** — Records can be validated incrementally without loading the full dataset.
- **Text-based** — Human-inspectable, diff-friendly, and tooling-friendly.
- **Derived schemas** — A schema can be projected, filtered, and transformed from a parent schema.
- **Field-level encryption** — Privacy markers (`~`) designate sensitive fields; encrypted values travel as `base64:` tokens through the pipeline and are only decrypted on demand.

> JSON-T is not trying to replace JSON everywhere.
> It is designed for **large, structured, validated data exchange**.

---

## Quick Example

### Schema Definition

```jsont
{
  namespace: {
    baseUrl: "https://api.example.com/v1",
    version: "1.0",
    catalogs: [
      {
        schemas: [
          // Straight schema — declares its own fields
          Order: {
            fields: {
              i64:   id,
              str:   product  [(minLength=2, maxLength=80)],
              i32:   quantity [(minValue=1, maxValue=999)],
              d64:   price    [(minValue=0.01)],
              <Status>: status
            },
            validations: {
              rules   { price > 0, quantity > 0 }
              unique  { (id) }
            }
          },

          // Derived schema — projects and transforms Order
          OrderSummary: FROM Order {
            operations: (
              project(id, product, price),
              filter price > 10,
              transform price = price * 1.1
            )
          }
        ],
        enums: [
          Status: [PENDING, CONFIRMED, SHIPPED, CANCELLED]
        ]
      }
    ],
    data-schema: Order
  }
}
```

### Data Payload

```jsont
{1,"Widget A",10,9.99,CONFIRMED},
{2,"Widget B",5,24.50,SHIPPED},
{3,"Widget C",1,99.00,PENDING}
```

---

## Documentation

| Document | Description |
|---|---|
| [Language Specification](docs/language-spec.md) | Grammar, schema syntax, derived schemas, operations, expressions |
| [Scalar Types](docs/Types.md) | All built-in scalar types with constraints and formats |
| [Rust API](code/rust/README.md) | Building schemas, parsing, validating, and transforming in Rust |
| [Java API](code/java/README.md) | Building schemas, parsing, validating, and transforming in Java |
| [Performance](docs/Performance.md) | Benchmark results and optimization notes |

---

## Implementations

JSON-T has two production implementations that share the same grammar and wire format, including cross-verified encrypted streams.

| | Rust | Java 17 |
|---|---|---|
| **Source** | `code/rust/jsont/` | `code/java/jsont/` |
| **Crypto** | `code/rust/jsont-crypto/` | `code/java/jsont-crypto/` |
| **Parser** | `pest` PEG (schema) + hand-written row scanner | ANTLR4 (schema) + hand-written row scanner |
| **Throughput (10 M rows)** | ~239 rows/ms streaming parse | ~131 rows/ms streaming parse |
| **Streaming parse memory** | O(1) — ~14 KB regardless of file size | O(1) — bulk 64 KB buffer per reader |
| **Thread model** | Single-threaded streaming or parallel `sync_channel` pipeline | Single-threaded streaming or parallel worker-per-core pipeline |
| **Encryption** | ChaCha20-Poly1305, AES-256-GCM, ASCON-128a × RSA + ECDH | Same algorithms via BouncyCastle |

---

## Common Patterns

### Parse a schema DSL document

**Rust:**
```rust
use jsont::{Parseable, JsonTNamespace};

let ns = JsonTNamespace::parse(source)?;
```

**Java:**
```java
import io.github.datakore.jsont.JsonT;
import io.github.datakore.jsont.model.JsonTNamespace;

JsonTNamespace ns = JsonT.parseNamespace(source);
```

---

### Stream rows from a file (O(1) memory)

**Rust:**
```rust
use std::fs::File;
use std::io::BufReader;
use jsont::parse_rows_streaming;

let file = File::open("orders.jsont")?;
parse_rows_streaming(BufReader::new(file), |row| {
    // process each row inline — no accumulation
})?;
```

**Java:**
```java
import io.github.datakore.jsont.JsonT;
import java.io.*;

try (var reader = new BufferedReader(new FileReader("orders.jsont"))) {
    JsonT.parseRowsStreaming(reader, row -> {
        // process each row inline — no accumulation
    });
}
```

---

### Build a schema programmatically

**Rust:**
```rust
use jsont::{JsonTFieldBuilder, JsonTSchemaBuilder, ScalarType};

let schema = JsonTSchemaBuilder::straight("Order")
    .field_from(JsonTFieldBuilder::scalar("id",       ScalarType::I64))?
    .field_from(JsonTFieldBuilder::scalar("product",  ScalarType::Str).min_length(2))?
    .field_from(JsonTFieldBuilder::scalar("quantity", ScalarType::I32)
        .min_value(1.0).max_value(999.0))?
    .field_from(JsonTFieldBuilder::scalar("price",    ScalarType::D64).min_value(0.01))?
    .build()?;
```

**Java:**
```java
import io.github.datakore.jsont.builder.*;
import io.github.datakore.jsont.model.ScalarType;

JsonTSchema schema = JsonTSchemaBuilder.straight("Order")
    .fieldFrom(JsonTFieldBuilder.scalar("id",       ScalarType.I64))
    .fieldFrom(JsonTFieldBuilder.scalar("product",  ScalarType.STR).minLength(2))
    .fieldFrom(JsonTFieldBuilder.scalar("quantity", ScalarType.I32).minValue(1).maxValue(999))
    .fieldFrom(JsonTFieldBuilder.scalar("price",    ScalarType.D64).minValue(0.01))
    .build();
```

---

### Validate a stream of rows

**Rust:**
```rust
use jsont::{ValidationPipeline, RowIter};
use std::fs::File;
use std::io::BufReader;

let pipeline = ValidationPipeline::builder(schema)
    .without_console()
    .build();

pipeline.validate_each(
    RowIter::new(BufReader::new(File::open("orders.jsont")?)),
    |clean_row| { /* emit */ }
);
pipeline.finish()?;
```

**Java:**
```java
import io.github.datakore.jsont.validate.ValidationPipeline;
import io.github.datakore.jsont.JsonT;
import java.io.*;

ValidationPipeline pipeline = ValidationPipeline.builder(schema)
    .withoutConsole()
    .build();

try (var reader = new BufferedReader(new FileReader("orders.jsont"))) {
    var rows = new ArrayList<JsonTRow>();
    JsonT.parseRowsStreaming(reader, rows::add);
    pipeline.validateEach(rows, cleanRow -> { /* emit */ });
}
pipeline.finish();
```

---

---

## Privacy & Encryption

Mark sensitive fields with `~` in the schema. On write they are encrypted (ChaCha20-Poly1305, AES-256-GCM, or ASCON-128a) with a per-stream DEK wrapped by RSA-OAEP or ECDH. Encrypted values travel as opaque `base64:` tokens and are only decrypted on demand.

### Schema with sensitive fields

```jsont
Person: {
  fields: {
    str:  name,
    ~str: ssn,          // always encrypted on wire
    ~str: cardNumber?   // optional, encrypted when present
  }
}
```

### Write an encrypted stream

**Rust:**
```rust
use jsont::{write_encrypted_stream, EnvCryptoConfig, CryptoContext, SchemaKind};
use std::io::{BufWriter, Write};

// EnvCryptoConfig reads RSA PEM keys from environment variables.
let cfg = EnvCryptoConfig::new("JSONT_PUBLIC_KEY", "JSONT_PRIVATE_KEY");
let fields = match &schema.kind {
    SchemaKind::Straight { fields } => fields.clone(),
    _ => panic!("expected straight schema"),
};
let mut out = BufWriter::new(std::fs::File::create("people.jsont")?);
write_encrypted_stream(&rows, &fields, &cfg, CryptoContext::VERSION_CHACHA_PUBKEY, &mut out)?;
out.flush()?;
// Wire: {"ENCRYPTED_HEADER",16,256,"<base64:enc_dek>"}
//       {"Alice","<base64:field_payload>"}
```

**Java:**
```java
import io.github.datakore.jsont.crypto.*;
import io.github.datakore.jsont.stringify.RowWriter;

PublicKeyCryptoConfig cfg = PublicKeyCryptoConfig.ofKeys(pubPem, privPem);
try (CryptoContext ctx = CryptoContext.forEncrypt(AlgoVersion.CHACHA20_POLY1305, KekMode.PUBLIC_KEY, cfg);
     BufferedWriter w  = Files.newBufferedWriter(Path.of("people.jsont"), StandardCharsets.UTF_8);
     RowWriter writer  = new RowWriter(schema, ctx)) {
    writer.writeStream(rows, w);
}
```

### Read and decrypt an encrypted stream

**Rust:**
```rust
use jsont::{try_parse_encrypt_header, parse_field_payload,
            EnvCryptoConfig, JsonTRow, JsonTValue, Parseable, ValidationPipeline};

let all_rows = Vec::<JsonTRow>::parse(&std::fs::read_to_string("people.jsont")?)?;
let ctx      = try_parse_encrypt_header(&all_rows[0]).expect("EncryptHeader expected");
let cfg      = EnvCryptoConfig::new("JSONT_PUBLIC_KEY", "JSONT_PRIVATE_KEY");

let pipeline = ValidationPipeline::builder(schema)
    .without_console()
    .with_cipher_session(cfg.open_session(&ctx)?)
    .build()?;
let clean = pipeline.validate_rows(all_rows[1..].to_vec());
pipeline.finish()?;

// Decrypt one field
let verify = cfg.open_session(&ctx)?;
if let Some(JsonTValue::Encrypted(env)) = clean[0].get(1) {
    let (iv, _, enc) = parse_field_payload(env, "ssn")?;
    let ssn = String::from_utf8(verify.decrypt_field(&iv, &enc)?)?;
}
```

**Java:**
```java
import io.github.datakore.jsont.crypto.*;
import io.github.datakore.jsont.internal.crypto.*;
import io.github.datakore.jsont.validate.ValidationPipeline;

List<JsonTRow> allRows = new ArrayList<>();
JsonT.parseRows(content, allRows::add);

EncryptHeaderParser.ParsedHeader header = EncryptHeaderParser.tryParse(allRows.get(0)).orElseThrow();
PublicKeyCryptoConfig cfg = PublicKeyCryptoConfig.ofKeys(pubPem, privPem);

try (CryptoContext ctx = CryptoContext.forDecrypt(header.version(), header.encDek(), cfg)) {
    List<JsonTRow> clean = ValidationPipeline.builder(schema)
        .withCryptoContext(ctx)
        .build()
        .validateRows(allRows.subList(1, allRows.size()));

    // Decrypt one field
    JsonTValue.Encrypted enc = (JsonTValue.Encrypted) clean.get(0).values().get(1);
    FieldPayload.Parsed p = FieldPayload.parse(enc.envelope(), "ssn");
    String ssn = new String(ctx.decryptField(p.iv(), p.encContent()), StandardCharsets.UTF_8);
}
```

### Decrypt in a derived schema pipeline

```jsont
PersonDecrypted: FROM Person {
  operations: (
    decrypt(ssn)
  )
}
```

**Rust:**
```rust
let result = derived_schema.transform_with_crypto(row, &registry, &crypto_config)?;
```

**Java:**
```java
JsonTRow result = RowTransformer.of(derivedSchema, registry)
    .transformWithCrypto(row, cryptoConfig);
```

---

## When to Use JSON-T

JSON-T is a good fit for:

- Large API responses (thousands+ records)
- Batch ingestion pipelines
- Event streams and logs
- Analytics exports
- Mobile or WAN-constrained environments
- Systems requiring strict producer/consumer contracts

JSON remains better for small, ad-hoc, or human-authored payloads.

---

## Status

Both implementations — Rust and Java — are production-ready for:

- Parsing, validation, and transformation of large datasets
- JSON interoperability (bidirectional NDJSON / Array / Object modes)
- Field-level envelope encryption — 3 algorithms (ChaCha20-Poly1305, AES-256-GCM, ASCON-128a) × 2 KEK modes (RSA, ECDH)
- Encrypted stream wire compatibility verified bidirectionally (Rust-encrypted ↔ Java-decrypted and vice versa)
- On-demand decryption via the `CipherSession` / `CryptoContext` APIs

Benchmarked at 10 M rows on realistic schemas with no memory growth.

See [Performance.md](docs/Performance.md) for benchmark details.

---

## Philosophy

> JSON is a notation.
> JSON-T is a data contract with a transport.
