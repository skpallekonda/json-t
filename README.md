# JSON-T

**JSON-T** is a **schema-driven, positional data language** designed to reduce payload size and enforce strong, symmetric data validation — especially for **large datasets, batch APIs, and streaming use cases**.

Unlike JSON, where field names are repeated in every record, JSON-T defines structure once using a schema and encodes data as compact positional tuples.

---

## Why JSON-T?
JSON is flexible and human-readable, but it has well-known limitations at scale:

* Repeated field names inflate payload size
* Validation is external, duplicated, and inconsistent
* Schema drift is hard to detect
* Streaming validation is inefficient

JSON-T addresses these problems directly.

---
Nice — this is actually a **very strong origin story** for the project, and it *adds credibility* instead of sounding convoluted when written correctly.

Below is a **clean README section** you can drop in **as-is**.
It explains the idea without over-explaining, and positions JSON-T correctly among CSV / Protobuf / Avro / JSON.

---

## Design Inspiration

JSON-T came from trying to combine the **best properties of existing data formats** while avoiding their biggest limitations.

### 1. CSV — Positional & Compact

CSV is extremely compact because:

* Field names are not repeated
* Data is positional
* Encoding is simple and fast

However, CSV lacks:

* Nested structures
* Strong typing
* Validation
* Schema evolution

JSON-T borrows CSV’s **positional encoding efficiency**, but extends it with structure and validation.

---

### 2. Protocol Buffers — Strict Types

Protocol Buffers provide:

* Strong, explicit data types
* Deterministic serialization
* Reliable producer–consumer contracts

But they require:

* Code generation
* Binary tooling
* Language-specific bindings
* Opaque payloads

JSON-T adopts **strict typing and validation**, without requiring binary formats or code generation.

---

### 3. Avro — Schema-First Design

Avro popularized:

* Schema-first data exchange
* Clear producer/consumer contracts
* Efficient large-scale data transport

However:

* Schemas are often external
* Payloads are not human-friendly
* Tooling is heavy for small teams

JSON-T keeps the **schema-first model**, but embeds schema and data together in a **single, readable document**.

---

### 4. JSON — Human Readability

JSON is:

* Simple
* Human-readable
* Ubiquitous

But at scale:

* Field names dominate payload size
* Validation is external and inconsistent
* Schema drift is hard to detect

JSON-T preserves **JSON-like readability**, while eliminating its structural inefficiencies.

---

## The Result: JSON-T

JSON-T is the result of combining these ideas:

* CSV-like **positional compactness**
* Protobuf-like **strict typing**
* Avro-like **schema-first validation**
* JSON-like **human-readable text**

The result is a data format that is:

* Smaller than JSON at scale
* Safer than JSON by design
* Easier to validate symmetrically
* Still inspectable and tooling-friendly

> JSON-T is not trying to replace JSON everywhere.
> It is designed for **large, structured, validated data exchange**.

---

## Key Features

* **Schema-first design**
  Structure is defined once and reused for all data.

* **Positional encoding**
  Data is encoded as ordered tuples, eliminating repeated keys.

* **Strong validation**
  Types, optionality, constraints, and nullability are enforced by the language.

* **Smaller payloads**
  Typically **45–55% smaller than JSON** for large datasets.

* **Streaming-friendly**
  Records can be validated incrementally without loading full objects.

* **Text-based (not binary)**
  Human-inspectable, diff-friendly, and tooling-friendly.

---

## Example

### Schema + Data (JSON-T)

```js
{
  schemas: {
    Customer: {
      int: id,
      str: name (minlen=2),
      <Address>: address?,
      uuid: ref
    },
    Address: {
      str: line1,
      str: state,
      zip: zipCode
    }
  },
  data-schema: "Customer",
  data: [
    {1, "Sasi", {"123 Marine Dr", "CA", 62003}, 550e8400e29b41d4a716446655440000},
    {2, "Kumar", null, 550e8400e29b41d4a716446655440111}
  ]
}
```

### Equivalent JSON (conceptual)

```json
{
  "id": 1,
  "name": "Sasi",
  "address": {
    "line1": "123 Marine Dr",
    "state": "CA",
    "zipCode": "62003"
  },
  "ref": "550e8400-e29b-41d4-a716-446655440000"
}
```

---

## When to Use JSON-T

JSON-T is a good fit for:

* Large API responses (thousands+ records)
* Batch ingestion pipelines
* Event streams and logs
* Analytics exports
* Mobile or WAN-constrained environments
* Systems requiring strict producer/consumer contracts

JSON remains better for small, ad-hoc, or human-authored payloads.

---

## Validation Model

JSON-T validation is **built-in**, not optional:

* Required vs optional fields
* Primitive and structured types
* Value ranges and patterns
* Array size and nullability rules
* Positional correctness

If a document parses successfully, it is structurally valid.

---

## Tooling

This repository contains:

* A **Langium-based grammar**
* TypeScript/JavaScript lexer & parser
* AST generation
* Semantic validation hooks
* CLI and programmatic APIs (work in progress)

---

## Status

🚧 **Work in progress**

Current focus:

* Grammar stabilization
* Parser and AST validation
* Schema and data consistency checks

Future work:

* Binary encoding
* Schema evolution rules
* Streaming decoder
* JSON ↔ JSON-T converters

---

## Philosophy

> JSON is a notation.
> JSON-T is a data contract with a transport.

