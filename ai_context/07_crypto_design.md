# jsont-crypto: Design Document

## Overview

`jsont-crypto` is a separate, independent module (Rust crate / Maven module) that owns the `CryptoConfig` interface, `CryptoContext`, and all concrete crypto implementations. It has **no dependency on `jsont` core**.

`jsont` core depends on `jsont-crypto` for the `CryptoConfig` interface. The `EncryptHeader` schema and header row parsing live in `jsont` core (which already has access to all model types).

The wire format, binary framing, and API contract defined here must be identical across the Rust and Java implementations so that data encrypted by one can be decrypted by the other.

---

## Wire Format

### Stream Layout

An encrypted JsonT stream has exactly one `EncryptHeader` row as the first row, followed by normal data rows.

```
{"ENCRYPTED_HEADER", <version:u16>, <length:u32>, "<base64:enc_dek>"}   ← header row
{"Alice",  "<base64:fp>",  30,  "<base64:fp>"}                          ← data row 1
{"Bob",    "<base64:fp>",  25,  "<base64:fp>"}                          ← data row 2
```

Plain fields are written as-is. Sensitive fields (`~` marker) carry a base64-encoded per-field payload (`<base64:fp>`).

The reader peeks at the first row's `type` field. If it equals `"ENCRYPTED_HEADER"` the stream is encrypted; otherwise it is a plain stream.

---

### EncryptHeader Schema (built-in, hardcoded in `jsont` core)

```
SCHEMA EncryptHeader
  str    : type     constant "ENCRYPTED_HEADER"
  u16    : version
  u32    : length                               ← byte count of enc_dek
  base64 : enc_dek
```

`length` is a live field (not a constant) to support different KEK algorithms producing different-sized wrapped keys.

---

### version Field Bit Layout (u16, MSB → LSB)

```
Bit 15      Bits 14-11    Bits 10-7     Bits 6-3      Bits 2-1   Bit 0
─────────   ───────────   ───────────   ───────────   ────────   ────────
reserved    format_ver    cert_ver      algo_ver       unused     kek_mode
(0)         (4 bits)      (4 bits)      (4 bits)       (2 bits)   (1 bit)
```

| Field | Meaning |
|---|---|
| `format_ver` | Version of the EncryptedPayload binary framing itself |
| `cert_ver` | Which of the receiver's key pairs was used to wrap the DEK (0 = unversioned) |
| `algo_ver` | Cipher algorithm: 1=AES-GCM, 2=ChaCha20-Poly1305, 3=ASCON |
| `kek_mode` | 0 = receiver public key, 1 = ECDH pre-established shared key |

---

### Per-Field Payload (binary, then base64-encoded for the wire)

Each sensitive field value on the wire is the base64 encoding of:

```
len_iv:       u16      — byte count of iv
len_digest:   u32      — bit count of digest (256 = SHA-256; extensible to 384, 512)
iv:           byte[]   — unique nonce for this field (generated fresh per encryption)
digest:       byte[]   — hash of this field's original plaintext value
enc_content:  byte[]   — ciphertext (length = remainder of payload bytes)
```

`enc_content` length is inferred: `total_payload_bytes − 2 − 4 − len_iv − (len_digest / 8)`

**Key properties:**
- The DEK is shared across all sensitive fields in the stream (written once in the header).
- Every field gets its own IV — reusing an IV with the same DEK under AES-GCM is catastrophic.
- The digest covers only that field's plaintext — fields are independently verifiable.
- The writer is single-pass (digest is per-field, not per-dataset).

---

## Module Ownership

```
jsont-crypto  (independent — zero dependency on jsont core)
  ├── CryptoConfig          interface / trait       ← moved from jsont.crypto
  ├── CryptoError           error type              ← moved from jsont.crypto
  ├── CryptoContext         struct / record         ← new
  ├── EnvCryptoConfig       default implementation  ← new
  └── PassthroughCryptoConfig  identity (tests)     ← moved from jsont.crypto

jsont (core)
  ├── depends on jsont-crypto for CryptoConfig / CryptoError
  ├── EncryptHeader schema          built-in, hardcoded  ← new, lives here
  ├── RowWriter.writeRow(...)       unchanged call site, uses CryptoConfig
  ├── OperationApplicator           unchanged call site, uses CryptoConfig
  └── JsonTValue.decryptBytes/Str   unchanged call site, uses CryptoConfig
```

Call sites in `jsont` core do not change behaviour — only the import package of `CryptoConfig` and `CryptoError` changes from `jsont.crypto` to `jsont-crypto`.

---

## Types

### `CryptoContext`

Produced by the parser from the header row. Safe to store anywhere — contains only data that was already on the wire. No plaintext key material.

```rust
pub struct CryptoContext {
    pub version: u16,
    pub enc_dek: Vec<u8>,   // encrypted DEK exactly as read from the wire
}
```

```java
public record CryptoContext(short version, byte[] encDek) {}
```

---

### `CryptoConfig` (trait / interface)

A behaviour contract, not a data holder. The implementor reads key material from the environment (or HSM, key store, etc.) at call time. Key bytes are never stored as fields and are dropped when the method returns.

**Rust:**
```rust
pub trait CryptoConfig {
    /// Unwrap enc_dek → raw DEK. version carries kek_mode and algo_ver — implementor
    /// interprets the bits and uses the appropriate key from the environment.
    fn unwrap_dek(&self, version: u16, enc_dek: &[u8]) -> Result<Vec<u8>, CryptoError>;

    /// Wrap raw DEK → enc_dek for writing the EncryptHeader.
    fn wrap_dek(&self, version: u16, dek: &[u8]) -> Result<Vec<u8>, CryptoError>;

    /// Encrypt one field value. Returns (iv, enc_content).
    fn encrypt_field(&self, dek: &[u8], plaintext: &[u8]) -> Result<(Vec<u8>, Vec<u8>), CryptoError>;

    /// Decrypt one field value. Returns plaintext.
    fn decrypt_field(&self, dek: &[u8], iv: &[u8], enc_content: &[u8]) -> Result<Vec<u8>, CryptoError>;
}
```

**Java:**
```java
public interface CryptoConfig {
    byte[] unwrapDek(short version, byte[] encDek) throws CryptoError;
    byte[] wrapDek(short version, byte[] dek) throws CryptoError;
    EncryptedField encryptField(byte[] dek, byte[] plaintext) throws CryptoError;
    byte[] decryptField(byte[] dek, byte[] iv, byte[] encContent) throws CryptoError;

    record EncryptedField(byte[] iv, byte[] encContent) {}
}
```

---

### `EnvCryptoConfig` (default built-in implementation)

Reads key material from environment variables at call time. The caller supplies only the env var names — no key bytes stored in the object.

```rust
pub struct EnvCryptoConfig {
    pub private_key_var: String,   // e.g. "JSONT_PRIVATE_KEY"
    pub public_key_var:  String,   // e.g. "JSONT_RECEIVER_PUBLIC_KEY"
}
```

```java
public record EnvCryptoConfig(String privateKeyVar, String publicKeyVar) implements CryptoConfig {}
```

---

### `PassthroughCryptoConfig` (tests only)

Identity implementation: encrypt = identity, decrypt = identity. Already exists in the core module for testing. Carries over to `jsont-crypto` tests.

---

## Responsibility Matrix

| Type | Contains | Holds key material | Safe to store | Needed at parse | Needed at decrypt |
|---|---|---|---|---|---|
| `CryptoContext` | version + enc_dek (from wire) | No | Yes | Yes | Yes |
| `CryptoConfig` | Env var names only | No (reads at call time) | Yes | No | Yes |

---

## Pipeline Integration

### Writing an encrypted stream

```
1. CryptoConfig.wrap_dek(version, raw_dek) → enc_dek
2. Write EncryptHeader row: {"ENCRYPTED_HEADER", version, len, base64(enc_dek)}
3. For each data row:
     For each sensitive field:
       a. digest = SHA-256(plaintext_bytes)
       b. CryptoConfig.encrypt_field(dek, plaintext) → (iv, enc_content)
       c. Assemble payload: len_iv + len_digest + iv + digest + enc_content
       d. base64(payload) → wire value
     Write row with plain fields as-is, sensitive fields as base64 payloads
```

### Reading an encrypted stream

```
1. Parser reads row 1
   └─ type == "ENCRYPTED_HEADER"?
        └─ Yes → CryptoContext { version, enc_dek }   exposed to caller
        └─ No  → plain stream, no CryptoContext

2. For each subsequent data row:
   └─ ValidationPipeline.promoteRow (existing pipeline)
        └─ sensitive field: base64 string → decode → store raw payload bytes as Encrypted(bytes)
        └─ no decryption here — payload bytes kept intact

3. On-demand decrypt (any time after parsing, caller drives this):
   └─ row.decrypt_field(index, field_name, &crypto_context, &crypto_config)
        └─ get Encrypted(payload_bytes) from row
        └─ parse: len_iv, len_digest, iv, digest, enc_content
        └─ raw_dek = crypto_config.unwrap_dek(context.version, context.enc_dek)  ← ephemeral
        └─ plaintext = crypto_config.decrypt_field(raw_dek, iv, enc_content)
        └─ verify: SHA-256(plaintext) == digest  → error if mismatch
        └─ raw_dek dropped
        └─ return plaintext
```

### CryptoContext lifetime

The caller obtains `CryptoContext` from the parser and owns its lifetime. It may be:
- Kept in memory for the duration of a session
- Persisted to a store for later use (safe — no plaintext secrets)
- Shared across services

`CryptoConfig` (with env var references to private keys) is only needed at the moment of `decrypt_field`. The two are always supplied together at that call site.

---

## Algorithm Versions

| `algo_ver` bits | Algorithm | Notes |
|---|---|---|
| `0001` (1) | AES-256-GCM | Default |
| `0010` (2) | ChaCha20-Poly1305 | Software-optimised |
| `0011` (3) | ASCON | NIST SP 800-232; constrained devices |
| `0000`, `0100`–`1111` | Reserved | For future algorithms |

---

## KEK Modes

| `kek_mode` bit | Mode | Key material needed |
|---|---|---|
| `0` | Receiver public key | Public key for wrap; private key for unwrap |
| `1` | ECDH pre-established | Shared secret derived out-of-band; supplied via env var |

For ECDH (mode 1): the client must have completed key exchange before calling the library. The shared secret is provided via the env var named in `CryptoConfig`. The library does not perform ECDH negotiation itself.

---

## CryptoError Variants

Lives in `jsont-crypto`. All error types carry a human-readable `reason` string.

| Variant | Trigger |
|---|---|
| `KeyNotFound(var_name)` | Env var not set or key store returned nothing |
| `InvalidKey(reason)` | PEM/DER parse failed, wrong key type, or wrong size |
| `UnsupportedAlgorithm(version)` | `algo_ver` bits point to an unknown cipher |
| `UnsupportedKekMode(version)` | `kek_mode` bit points to an unknown mode |
| `DekWrapFailed(reason)` | Encrypting the raw DEK with the KEK failed |
| `DekUnwrapFailed(reason)` | Decrypting `enc_dek` with the KEK failed (wrong key, corrupt bytes) |
| `EncryptFailed(field, reason)` | AE encryption of a field value failed |
| `DecryptFailed(field, reason)` | AE decryption failed — auth tag mismatch or corrupt IV |
| `DigestMismatch(field)` | Auth tag passed but SHA-256(plaintext) ≠ stored digest — content tampered |
| `MalformedPayload(field, reason)` | Per-field binary payload cannot be parsed (truncated, bad lengths) |

`DigestMismatch` is distinct from `DecryptFailed`: the ciphertext was cryptographically valid but the plaintext was altered before encryption. Callers may treat these differently.

---

## Dependencies

### Rust — RustCrypto family

`ring` is not viable: it has no ASCON support. RustCrypto covers all three algorithm versions with a consistent API and no C FFI.

```toml
aes-gcm          = "0.10"   # AES-256-GCM        (algo_ver = 1)
chacha20poly1305 = "0.10"   # ChaCha20-Poly1305  (algo_ver = 2)
ascon-aead       = "0.4"    # ASCON-128a         (algo_ver = 3)
rsa              = "0.9"    # RSA key wrap       (kek_mode = 0)
p256             = "0.13"   # ECDH P-256         (kek_mode = 1)
sha2             = "0.10"   # SHA-256 digest
rand             = "0.8"    # IV generation
pem              = "3"      # PEM parsing
pkcs8            = "0.10"   # Key deserialization
```

### Java — Bouncy Castle

```xml
<dependency>
    <groupId>org.bouncycastle</groupId>
    <artifactId>bcprov-jdk18on</artifactId>
    <version>1.78</version>
</dependency>
<dependency>
    <groupId>org.bouncycastle</groupId>
    <artifactId>bcpkix-jdk18on</artifactId>  <!-- PEM / PKCS8 parsing -->
    <version>1.78</version>
</dependency>
```

---

## Key Format

### Accepted from environment variables

| Format | Detection | Example |
| --- | --- | --- |
| Full PEM | Value starts with `-----` | `-----BEGIN PRIVATE KEY-----\nMIIE...\n-----END PRIVATE KEY-----` |
| Stripped Base64 (DER) | Anything else | `MIIEvAIBADAN...` (headers and newlines removed) |

Auto-detection: if the env var value starts with `-----BEGIN`, parse as full PEM. Otherwise treat as Base64-encoded DER. Both BouncyCastle and the RustCrypto `pem`/`pkcs8` crates handle this with minimal branching.

Stripped Base64 is the recommended format for container environments (Docker, Kubernetes) where multi-line env var values cause tooling issues. Newline normalization (`\n` literal → actual newline) is applied before parsing in both cases.
