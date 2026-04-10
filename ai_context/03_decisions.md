# Decisions

## D1: Schema-first design

* Context: Need strong validation and contracts
* Decision: Define structure before data
* Why: Enables symmetry and consistency
* Tradeoff: Less flexible than JSON

---

## D2: Positional encoding

* Context: JSON payload size inefficiency
* Decision: Use ordered tuples
* Why: Reduce payload size and improve streaming
* Tradeoff: Reduced readability

---

## D3: Streaming-first architecture

* Context: Target large datasets and pipelines
* Decision: Design for O(1) memory processing
* Why: Scalability and performance
* Tradeoff: More complex APIs

---

## D4: Capability separation

* Context: Avoid monolithic processing pipeline
* Decision: Separate parse, validate, transform
* Why: Flexibility and composability
* Tradeoff: Slightly more surface area

---

## D5: Single bundle distribution

* Context: Ease of adoption
* Decision: Ship as one package
* Why: Simplicity for users
* Tradeoff: Requires internal discipline to maintain separation

---

## D6: Crypto as a separate independent module

* Context: Not all users need encryption; crypto crates are heavy
* Decision: `jsont-crypto` is a standalone module with no dependency on `jsont` core; `jsont` core depends on `jsont-crypto` for the `CryptoConfig` interface only
* Why: Keeps the core lean; crypto is opt-in; avoids circular dependency
* Tradeoff: `jsont` core has a compile-time dependency on `jsont-crypto` even for non-crypto users (interface only, no cipher code pulled in)

---

## D7: Envelope encryption with versioned binary framing

* Context: Need algorithm agility and forward compatibility
* Decision: 16-bit version field in `EncryptHeader` encodes format version, receiver cert version, algorithm version, and KEK mode; one DEK per stream encrypted once in the header; per-field payload carries IV + digest + ciphertext
* Why: Old ciphertext never breaks when a new algorithm is added; receiver can hold multiple key versions simultaneously
* Tradeoff: Slightly larger per-field payload (IV + digest bytes per field)

---

## D8: CryptoContext stores encrypted DEK, not plaintext

* Context: CryptoContext may be persisted or shared by the caller
* Decision: `CryptoContext` holds `version` + `enc_dek` exactly as read from the wire; raw DEK is derived ephemerally at decrypt time and dropped immediately
* Why: No plaintext key material ever persists; CryptoContext is safe to store anywhere
* Tradeoff: One DEK unwrap call per `decrypt_field` invocation (acceptable given typical decrypt frequency)

---

## D9: Key material supplied via environment variables, not stored in config objects

* Context: Storing private keys as object fields is a security anti-pattern
* Decision: `CryptoConfig` holds only env var names; implementations read key bytes at call time and discard them after use; `CryptoConfig` itself is safe to store
* Why: Private key material never lives on the heap as a reachable object field
* Tradeoff: Env var read on every decrypt call (negligible cost; can be cached by the OS)

---

## D10: RustCrypto family for Rust; Bouncy Castle for Java

* Context: Need AES-GCM, ChaCha20-Poly1305, and ASCON in both implementations
* Decision: Rust uses RustCrypto (`aes-gcm`, `chacha20poly1305`, `ascon-aead`, `rsa`, `p256`, `sha2`, `rand`, `pem`, `pkcs8`); Java uses Bouncy Castle (`bcprov-jdk18on`, `bcpkix-jdk18on`)
* Why: `ring` has no ASCON support; RustCrypto is the only Rust option covering all three; Bouncy Castle is the standard for non-JCA algorithms in Java
* Tradeoff: RustCrypto adds several crate dependencies; Bouncy Castle is a large JAR

---

## D11: PEM with stripped Base64-DER fallback for key format

* Context: Multi-line PEM values cause issues in container env var tooling
* Decision: Accept full PEM (starts with `-----BEGIN`) or stripped Base64-encoded DER (headers and newlines removed); auto-detect by inspecting the first characters
* Why: Full PEM for developer ergonomics; stripped Base64 for container/CI environments
* Tradeoff: Minimal detection logic required in both Rust and Java implementations
