# Current State

## Layer Status

* Core (Schema + Parsing + Validation): ✅ Complete
* Transform (Derived schemas): ✅ Complete
* Runtime Engine: ⚪ Not Started
* Service Layer: ⚪ Not Started

---

## Capability Status

* Parse: ✅ Complete
* Validate: ✅ Complete
* Transform: ✅ Complete
* Execute: ⚪ Not Started
* Expose: ⚪ Not Started

---

## Stable Areas

* Schema definition
* Parsing
* Validation
* Transform operations

---

## In Progress / Planned

* JOIN operations

## Recently Completed

* Privacy markers (`~` DSL suffix) — Steps 7–10 complete in both Rust and Java
  * `Encrypted` value variant + `base64:` wire format
  * `CryptoConfig` trait/interface + `PassthroughCryptoConfig`
  * `write_row_with_schema` / `RowWriter.writeRow(row, fields, crypto, writer)` — schema-aware stringify
  * `transform_with_crypto` — Decrypt pipeline operation with pluggable crypto
  * On-demand decrypt API: `decrypt_bytes`, `decrypt_str` on values; `decrypt_field_str` / `decryptField` on rows
  * 15 tests in `crypto_tests.rs` + `CryptoTests.java` — all passing

---

## Not Started

* Runtime engine
* Service abstraction layer
* UI / Studio
