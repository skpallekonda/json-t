# Current State

## Layer Status

* Core (Schema + Parsing + Validation): ✅ Complete
* Transform (Derived schemas): ✅ Complete
* Crypto (jsont-crypto): 🔵 Design complete — implementation not started
* Runtime Engine: ⚪ Not Started
* Service Layer: ⚪ Not Started

---

## Capability Status

* Parse: ✅ Complete
* Validate: ✅ Complete
* Transform: ✅ Complete
* Encrypt / Decrypt: 🔵 Design complete — implementation not started
* Execute: ⚪ Not Started
* Expose: ⚪ Not Started

---

## Stable Areas

* Schema definition
* Parsing
* Validation
* Transform operations
* Polymorphic types (`anyOf`)
* Wire format (plain base64 for sensitive fields — no prefix)
* JSON interoperability (`JsonReader` / `JsonWriter`, both Rust and Java)

---

## In Progress / Planned

* jsont-crypto module implementation (design documented in `ai_context/07_crypto_design.md`)
* JOIN operations

---

## Recently Completed

* **Polymorphic types (`anyOf`)** (both Rust and Java)
  * `anyOf` field type — field accepts any of a declared set of schema variants
  * Discriminated union resolution at parse/validate time
  * Transform and stringify support for polymorphic rows

* **JSON interoperability** (both Rust and Java)
  * `JsonReader` — JSON → `JsonTRow` with schema mapping; `JsonInputMode` (object / array)
  * `JsonWriter` — `JsonTRow` → JSON; `JsonOutputMode` (NDJSON / array)
  * `MissingFieldPolicy` and `UnknownFieldPolicy` for field handling at boundaries
  * Round-trip: JsonT wire → JSON → JsonT wire verified byte-for-byte identical

* **Q2 — Schema validation at registry build** (both Rust and Java)
  * `SchemaRegistry::from_namespace` / `SchemaRegistry.fromNamespace` returns `Result`/throws
  * Full registry populated before any schema is validated (forward references resolved)
  * `SchemaResolver` interface introduced to break circular dependency in Java

* **Q1 — Wire format: plain base64 for sensitive fields** (both Rust and Java)
  * Removed `base64:` prefix from encrypted field wire values
  * `~` schema marker is now the sole authority for identifying encrypted fields
  * Invalid base64 at a sensitive field position → `FormatViolation` (row rejected)
  * All tests updated; 383 Rust + 665 Java tests passing

* **Privacy markers (`~` DSL suffix) — Steps 7–10** (both Rust and Java)
  * `Encrypted` value variant — raw ciphertext bytes in memory, plain base64 on wire
  * `CryptoConfig` trait/interface + `PassthroughCryptoConfig`
  * `write_row_with_schema` / `RowWriter.writeRow(row, fields, crypto, writer)` — schema-aware stringify
  * `transform_with_crypto` — Decrypt pipeline operation with pluggable crypto
  * On-demand decrypt API: `decrypt_bytes`, `decrypt_str` on values; `decrypt_field_str` / `decryptField` on rows

---

## Not Started

* jsont-crypto implementation
* Runtime engine
* Service abstraction layer
* UI / Studio
