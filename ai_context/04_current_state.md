# Current State

## Layer Status

* Core (Schema + Parsing + Validation): ✅ Complete
* Transform (Derived schemas): ✅ Complete
* Crypto (jsont-crypto): ✅ Complete (Rust + Java)
* Runtime Engine: ⚪ Not Started
* Service Layer: ⚪ Not Started

---

## Capability Status

* Parse: ✅ Complete
* Validate: ✅ Complete
* Transform: ✅ Complete
* Encrypt / Decrypt: ✅ Complete (Rust + Java — 3 algorithms × 2 KEK modes each)
* Cross-compat (Rust ↔ Java): ✅ Complete — crypto layer + full encrypted stream verified
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
* Privacy & Encryption — both Rust and Java, 3 algorithms (AES-256-GCM, ChaCha20-Poly1305, ASCON-128a) × 2 KEK modes (RSA public key, ECDH P-256)
* Encrypted stream wire format — `EncryptHeader` row + per-field payload (IV + SHA-256 digest + ciphertext)
* Cross-compat fixtures — `code/cross-compat/` contains committed keys, crypto-layer fixtures, and full encrypted `.jsont` files verified bidirectionally

---

## In Progress / Planned

* Rust implementation: SOLID refactors (command pattern for operation applicator, scope validators) — mirrors what was done in Java
* JOIN operations

---

## Recently Completed

* **Rust jsont-crypto — full implementation**
  * `CryptoContext` (version + enc_dek wire holder), `CipherSession` (per-stream plaintext DEK, zeroed on drop)
  * `EnvCryptoConfig` (reads RSA PEM keys from env vars), `EcdhCryptoConfig::of_keys(peer_pub_der, host_priv_pem)`
  * `CryptoConfig` trait: `wrap_dek` / `unwrap_dek` / `open_session` → `CipherSession`
  * `CipherSession`: `encrypt_field(plaintext)` → `EncryptedField { iv, enc_content }` (fresh random IV per call)
  * `write_encrypted_stream` — writes `EncryptHeader` row + encrypted data rows in one pass
  * `try_parse_encrypt_header` / `parse_field_payload` — header and payload parsing helpers
  * `ValidationPipeline::builder(schema).with_cipher_session(session).build()` — pipeline with decryption

* **Cross-compatibility tests — crypto layer** (`code/cross-compat/`)
  * Persistent key pairs: RSA 2048 (`rsa_2048_*.pem`) + ECDH P-256 (`ec_a_*`, `ec_b_*`)
  * Fixtures committed: `rust_rsa_chacha20.txt`, `rust_ecdh_chacha20.txt`, `java_rsa_chacha20.txt`, `java_ecdh_chacha20.txt`
  * Rust test: `code/rust/jsont-crypto/tests/cross_compat_tests.rs` (4 tests)
  * Java test: `code/java/jsont-crypto/src/test/.../CrossCompatTest.java` (4 tests)

* **Cross-compatibility tests — full encrypted stream** (`code/cross-compat/`)
  * Schema: `CricketMatchEncrypted` (11 fields, 2 sensitive: `teamACoachName~`, `teamBCoachName~`)
  * Committed fixtures: `rust-encrypted.jsont`, `java-encrypted.jsont` (10 rows each)
  * Rust test: `code/rust/jsont/tests/encrypted_cross_compat_tests.rs`
  * Java test: `code/java/jsont/src/test/.../EncryptedCrossCompatTest.java`
  * Verified: Rust encrypts → Java decrypts ✓ and Java encrypts → Rust decrypts ✓

* **Java SOLID refactors**
  * `OperationApplicator` → command pattern: `RowOperationHandler` + `FieldResolutionHandler` interfaces, one class per operation type in `handler/` subpackage
  * `JsonTSchema.validateWithParent` → `OperationScopeValidator` per operation type, orchestrated by `ScopeValidation`; model class now delegates in one line

* **ASCON-128a implementation** (Java + Rust)
  * Java: `AsconEngine` via BouncyCastle, `VERSION_ASCON_PUBKEY = 0x0018`, `VERSION_ASCON_ECDH = 0x0019`
  * Rust: `ascon-aead` crate (RustCrypto family)

* **EcdhCryptoConfig** (Java + Rust)
  * Java: `EcdhCryptoConfig.ofKeys(peerPublicKeyDer, hostPrivKeyPem)` — ECDH P-256 + HKDF KEK
  * Rust: `EcdhCryptoConfig::of_keys(peer_pub_der, host_priv_pem)`

* **Schema-bound RowWriter** (Java)
  * Constructor enforces: sensitive fields require `CryptoContext` — throws `IllegalArgumentException` at construction
  * `writeStream(rows, writer)` handles plain and encrypted schemas (emits `EncryptHeader` row automatically)

* **Polymorphic types (`anyOf`)** (both Rust and Java)
* **JSON interoperability** (both Rust and Java)
* **Schema validation at registry build** (both Rust and Java)

---

## Not Started

* Rust: SOLID refactors (command pattern for operation applicator, scope validators)
* Runtime engine
* Service abstraction layer
* UI / Studio

---

## Test Counts

* Java: **692 tests** (669 jsont + 23 jsont-crypto), 0 failures
* Rust: ~390 tests (jsont + jsont-crypto), 0 failures
