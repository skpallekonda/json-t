# Current State

## Layer Status

* Core (Schema + Parsing + Validation): ✅ Complete
* Transform (Derived schemas): ✅ Complete
* Crypto (jsont-crypto): ✅ Complete
* Runtime Engine: ⚪ Not Started
* Service Layer: ⚪ Not Started

---

## Capability Status

* Parse: ✅ Complete
* Validate: ✅ Complete
* Transform: ✅ Complete
* Encrypt / Decrypt: ✅ Complete (Java) — Rust pending
* Execute: ⚪ Not Started
* Expose: ⚪ Not Started

---

## Stable Areas

* Schema definition
* Parsing
* Validation
* Transform operations (command-pattern handlers per operation type)
* Polymorphic types (`anyOf`)
* Wire format (plain base64 for sensitive fields — no prefix)
* JSON interoperability (`JsonReader` / `JsonWriter`, both Rust and Java)
* Privacy & Encryption (Java complete — 3 algorithms × 2 KEK modes)

---

## In Progress / Planned

* Rust implementation: apply same design changes as Java (SOLID / command pattern)
* JOIN operations

---

## Recently Completed

* **Java SOLID refactors**
  * `OperationApplicator` → command pattern: `RowOperationHandler` + `FieldResolutionHandler` interfaces, one class per operation type in `handler/` subpackage
  * `JsonTSchema.validateWithParent` → `OperationScopeValidator` per operation type, orchestrated by `ScopeValidation`; model class now delegates in one line

* **ASCON-128a implementation** (Java)
  * Field cipher in `CryptoContext` (`encryptAscon128a` / `decryptAscon128a`) — 16-byte nonce, first 16 bytes of 32-byte DEK as key
  * DEK wrapping in `EcdhCryptoConfig` (`ascon128aEncrypt` / `ascon128aDecrypt`)
  * `VERSION_ASCON_ECDH = 0x0019` constant added to `CryptoContext`
  * `CryptoContextTest` updated: ASCON round-trip test + unknown-algo test replace the old placeholder

* **EcdhCryptoConfig enhancements** (Java)
  * `ofKeys(peerPublicKeyDer, hostPrivKeyPem)` factory for tests and programmatic use
  * `EcdhCryptoConfigTest`: 6 tests covering all 3 algorithms (AES-GCM, ChaCha20, ASCON) + wrong-key rejection + full `CryptoContext` round-trip + missing env-var

* **Schema-bound RowWriter** (Java)
  * Constructor enforces: sensitive fields require `CryptoContext` — throws `IllegalArgumentException` at construction, not at write time
  * `writeStream(rows, writer)` handles plain and encrypted schemas with one call (emits `EncryptHeader` row automatically)
  * `ValidationPipelineBuilder.build()` throws `IllegalStateException` if schema has sensitive fields but no `CryptoContext`

* **Polymorphic types (`anyOf`)** (both Rust and Java)
* **JSON interoperability** (both Rust and Java)
* **Schema validation at registry build** (both Rust and Java)
* **Wire format: plain base64 for sensitive fields** (both Rust and Java)
* **Privacy markers (`~` DSL suffix)** (both Rust and Java)

---

## Not Started

* Rust: SOLID refactors (command pattern for operation applicator, scope validators)
* Rust: ASCON-128a field cipher + ECDH DEK wrapping
* Runtime engine
* Service abstraction layer
* UI / Studio

---

## Test Counts

* Java: **692 tests** (669 jsont + 23 jsont-crypto), 0 failures
* Rust: 383 tests, 0 failures
