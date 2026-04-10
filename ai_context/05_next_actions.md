# Next Actions

## P0 — Adoption Critical

1. ~~JSON interoperability~~ ✅ Complete — `JsonReader` / `JsonWriter` with `JsonInputMode`,
   `JsonOutputMode`, `MissingFieldPolicy`, `UnknownFieldPolicy` implemented in both Rust and Java.

---

## P1 — Security

1. **jsont-crypto implementation** (design complete — `ai_context/07_crypto_design.md`)

   Implement in both Rust (`jsont-crypto` crate) and Java (Maven module) in lockstep so
   wire compatibility is verified as each step lands.

   ### Step 1 — Module scaffold

   * Create `code/rust/jsont-crypto/` crate and `code/java/jsont-crypto/` Maven module
   * Add dependencies: RustCrypto family (Rust); Bouncy Castle (Java)
   * `jsont` core: change `CryptoConfig` / `CryptoError` import to point at `jsont-crypto`

   ### Step 2 — CryptoError

   * Define all variants: `KeyNotFound`, `InvalidKey`, `UnsupportedAlgorithm`,
     `UnsupportedKekMode`, `DekWrapFailed`, `DekUnwrapFailed`, `EncryptFailed`,
     `DecryptFailed`, `DigestMismatch`, `MalformedPayload`

   ### Step 3 — EnvCryptoConfig

   * Implement `CryptoConfig` that reads key material from env vars at call time
   * Auto-detect full PEM (`-----BEGIN`) vs stripped Base64-DER
   * Support both KEK modes: receiver public key (mode 0) and ECDH pre-established (mode 1)

   ### Step 4 — EncryptHeader in jsont core

   * Add built-in `EncryptHeader` schema (hardcoded, not user-definable)
   * Parser peeks at first row: if `type == "ENCRYPTED_HEADER"` → parse into `CryptoContext`
   * `CryptoContext { version: u16, enc_dek: Vec<u8> }` exposed to caller after parse

   ### Step 5 — Per-field payload encoding / decoding

   * Writer: assemble `len_iv + len_digest + iv + digest + enc_content` binary payload per sensitive field
   * Reader (promoteRow): decode base64 → store raw payload bytes as `Encrypted(bytes)` (unchanged from current; bytes now carry the full payload structure)

   ### Step 6 — On-demand decrypt with CryptoContext

   * Update `decrypt_field` / `decryptField` / `decryptBytes` / `decryptStr` signatures to accept `CryptoContext` alongside `CryptoConfig`
   * Parse payload bytes: extract `iv`, `digest`, `enc_content`
   * `CryptoConfig.unwrap_dek(version, enc_dek)` → raw DEK (ephemeral)
   * Decrypt, verify SHA-256 digest, return plaintext or `DigestMismatch`

   ### Step 7 — Tests

   * Wire compatibility test: encrypt in Rust, decrypt in Java (and vice versa)
   * Round-trip tests per algorithm version (AES-GCM, ChaCha20, ASCON)
   * CryptoError variant coverage
   * `EnvCryptoConfig` key format tests (full PEM, stripped Base64)
   * `CryptoContext` persistence round-trip (serialize, restore, decrypt)

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
