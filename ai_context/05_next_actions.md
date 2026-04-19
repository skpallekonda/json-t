# Next Actions

## P0 — Adoption Critical

1. ~~JSON interoperability~~ ✅ Complete

---

## P1 — Security / Rust Parity

1. **Rust implementation: apply Java's design changes** (next session)

   The Java implementation was refactored for SOLID principles. The Rust implementation needs
   the same treatment before the two diverge further.

   ### Step 1 — Command pattern for operation applicator

   * Current: large `match` / `if-let` chain in the operation applicator
   * Target: one handler type per `SchemaOperation` variant — a trait `RowOperationHandler`
     with `supports` + `apply`, and a `FieldResolutionHandler` trait for field-name shape changes
   * Registry: `OperationApplicator` holds a `Vec<Box<dyn RowOperationHandler>>` and dispatches

   ### Step 2 — Scope validators for derived-schema validation

   * Current: inline `match` in `validateWithParent` / equivalent Rust function
   * Target: `OperationScopeValidator` trait, one struct per operation type, orchestrated by `ScopeValidation::validate`

   ### Step 3 — ASCON-128a field cipher + ECDH DEK wrapping (Rust)

   * Java: implemented via BouncyCastle `AsconEngine`
   * Rust: use `ascon-aead` crate (RustCrypto family) for ASCON-128a
   * Key: first 16 bytes of the 32-byte DEK; nonce: 16 bytes; tag: 16 bytes
   * ECDH DEK wrapping: add ASCON branch alongside AES-GCM and ChaCha20

   ### Step 4 — EcdhCryptoConfig `ofKeys` equivalent (Rust)

   * Add a constructor/builder variant that accepts key bytes directly (no env-var requirement)
   * Enables in-process testing without environment setup

   ### Step 5 — Wire compatibility tests

   * Encrypt in Rust, decrypt in Java (and vice versa) for all 3 algorithms
   * Verify `EncryptHeader` round-trip, per-field payload structure, and digest verification

   ### Important: apply SOLID principles throughout

   The user confirmed that SOLID principles and appropriate design patterns are the standard
   for this codebase. When touching Rust code, apply the same command/strategy patterns
   as the Java refactor. Do not use large match blocks as the sole structure for extensible logic.

---

## P2 — Data Capabilities

1. JOIN operations across schemas

---

## P3 — Platform Evolution

1. Runtime engine design
2. Playground / developer experience

---

## Notes

* Prioritize adoption over new abstractions
* Avoid moving to runtime before core adoption stabilizes
* jsont-crypto wire format is frozen by design — changes require a new `format_ver` bit
* ECDH host public key: the current ECDH implementation is mathematically correct — the host
  public key is implicit in the private key. Including peer public key bytes in HKDF info
  is a future security enhancement for stronger key binding, not a correctness fix.
