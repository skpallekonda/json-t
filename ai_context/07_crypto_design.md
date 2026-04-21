# jsont-crypto: Design Document

## Overview

`jsont-crypto` is a separate, independent module (Rust crate / Maven module) that owns the `CryptoConfig` interface, `CryptoContext`, `CipherSession`, and all concrete crypto implementations. It has **no dependency on `jsont` core**.

`jsont` core depends on `jsont-crypto` for the `CryptoConfig` trait and `CryptoContext`. The `EncryptHeader` schema and header row parsing live in `jsont` core (which already has access to all model types).

The wire format, binary framing, and API contract defined here are identical across the Rust and Java implementations — verified by committed cross-compatibility fixtures in `code/cross-compat/`.

**Status: fully implemented — Rust and Java.**

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
| `kek_mode` | 0 = receiver public key (RSA), 1 = ECDH pre-established shared key |

---

### Per-Field Payload (binary, then base64-encoded for the wire)

Each sensitive field value on the wire is the base64 encoding of:

```
len_iv:       u16      — byte count of iv
len_digest:   u32      — bit count of digest (256 = SHA-256; extensible to 384, 512)
iv:           byte[]   — unique nonce for this field (generated fresh per encryption)
digest:       byte[]   — SHA-256 of this field's original plaintext value
enc_content:  byte[]   — ciphertext + auth tag (length = remainder of payload bytes)
```

`enc_content` length is inferred: `total_payload_bytes − 2 − 4 − len_iv − (len_digest / 8)`

**Key properties:**
- The DEK is shared across all sensitive fields in the stream (written once in the header).
- Every field gets its own fresh random IV — `(DEK, IV)` pairs are never reused.
- The digest covers only that field's plaintext — fields are independently verifiable.
- The writer is single-pass (digest is per-field, not per-dataset).

---

## Module Ownership

```
jsont-crypto  (independent — zero dependency on jsont core)
  ├── CryptoConfig          trait / interface       ← DEK wrap/unwrap + session factory
  ├── CryptoContext         struct / class          ← version + enc_dek (wire holder, no DEK)
  ├── CipherSession         struct / class          ← plaintext DEK holder, zeroed on drop
  ├── EncryptedField        struct / record         ← (iv, enc_content) pair
  ├── EnvCryptoConfig       RSA impl (env vars)
  ├── EcdhCryptoConfig      ECDH P-256 + HKDF impl
  ├── PassthroughCryptoConfig  identity (tests)
  └── CryptoError           error type

jsont (core)
  ├── depends on jsont-crypto for CryptoConfig / CryptoContext / CipherSession
  ├── try_parse_encrypt_header(row) -> Option<CryptoContext>   ← built-in header parser
  ├── parse_field_payload(envelope, name) -> (iv, digest, enc) ← payload parser
  ├── assemble_field_payload(iv, digest, enc) -> Vec<u8>       ← payload builder
  ├── build_encrypt_header_row(ctx) -> JsonTRow                ← header row builder
  ├── write_encrypted_stream(rows, fields, cfg, version, out)  ← full stream writer
  ├── write_row_with_session(row, fields, session, w)          ← single row with DEK
  └── ValidationPipeline::builder(schema)
         .with_cipher_session(session)                         ← pipeline with decryption
         .build() -> Result<ValidationPipeline, SinkError>
```

---

## Types

### `CryptoContext`

Contains only data that was already on the wire — safe to store anywhere.
No plaintext key material.

**Rust:**
```rust
pub struct CryptoContext {
    pub version: u16,
    pub enc_dek: Vec<u8>,   // encrypted DEK exactly as read from the wire
}
impl CryptoContext {
    pub fn new(version: u16, enc_dek: Vec<u8>) -> Self;
    pub const VERSION_CHACHA_PUBKEY: u16 = 0x0010;
    pub const VERSION_CHACHA_ECDH:   u16 = 0x0011;
    // … AES_PUBKEY=0x0008, AES_ECDH=0x0009, ASCON_PUBKEY=0x0018, ASCON_ECDH=0x0019
}
```

**Java:**
```java
public final class CryptoContext implements AutoCloseable {
    // Factory methods (also manage the plaintext DEK):
    public static CryptoContext forEncrypt(AlgoVersion algo, KekMode kekMode, CryptoConfig config) throws CryptoError;
    public static CryptoContext forDecrypt(int version, byte[] encDek, CryptoConfig config) throws CryptoError;
    // Field-level ops (fresh random IV per encryptField call):
    public EncryptedField encryptField(byte[] plaintext) throws CryptoError;
    public byte[] decryptField(byte[] iv, byte[] encContent) throws CryptoError;
    // Wire accessors:
    public int    version();
    public byte[] encDek();   // defensive copy
    // close() zeros the DEK
}
```

> **Design note:** In Java, `CryptoContext` holds both the wire data and the plaintext DEK (AutoCloseable).
> In Rust, `CryptoContext` is wire-only; the plaintext DEK lives in `CipherSession` (returned by `cfg.open_session(&ctx)`).

---

### `CipherSession` (Rust only)

Short-lived wrapper that holds the plaintext DEK for one stream. DEK is zeroed via `Zeroizing` on drop.

```rust
pub struct CipherSession { /* version: u16, dek: Zeroizing<Vec<u8>> */ }

impl CipherSession {
    pub fn encrypt_field(&self, plaintext: &[u8]) -> Result<EncryptedField, CryptoError>;
    // Generates a fresh random IV per call (12B for ChaCha20/AES-GCM, 16B for ASCON)
    pub fn decrypt_field(&self, iv: &[u8], enc_content: &[u8]) -> Result<Vec<u8>, CryptoError>;
    pub fn version(&self) -> u16;
}
```

---

### `CryptoConfig` (trait / interface)

A behaviour contract, not a data holder. Reads key material from the environment (or HSM) at call time.

**Rust:**
```rust
pub trait CryptoConfig {
    fn wrap_dek(&self, version: u16, dek: &[u8]) -> Result<Vec<u8>, CryptoError>;
    fn unwrap_dek(&self, version: u16, enc_dek: &[u8]) -> Result<Vec<u8>, CryptoError>;
    fn open_session(&self, ctx: &CryptoContext) -> Result<CipherSession, CryptoError>;
    // open_session = unwrap_dek + construct CipherSession
}
```

**Java:**
```java
public interface CryptoConfig {
    byte[] wrapDek(int version, byte[] dek) throws CryptoError;
    byte[] unwrapDek(int version, byte[] encDek) throws CryptoError;
}
```

---

### `EnvCryptoConfig` (RSA — reads from env vars)

Reads RSA 2048 PEM key material from environment variables at call time.

```rust
// Rust
pub struct EnvCryptoConfig { pub_var: String, priv_var: String }
impl EnvCryptoConfig {
    pub fn new(pub_var: &str, priv_var: &str) -> Self;
}
```

```java
// Java
PublicKeyCryptoConfig.ofKeys(String pubPem, String privPem)
```

---

### `EcdhCryptoConfig` (ECDH P-256 + HKDF)

Accepts key bytes directly — no env vars required. ECDH secret is derived from `(host_priv, peer_pub)`.

```rust
// Rust
pub struct EcdhCryptoConfig { /* peer DER + host PEM */ }
impl EcdhCryptoConfig {
    pub fn of_keys(peer_pub_der: Vec<u8>, host_priv_pem: &str) -> Self;
}
```

```java
// Java
EcdhCryptoConfig.ofKeys(byte[] peerPublicKeyDer, String hostPrivKeyPem)
```

**ECDH role convention for cross-compat tests:**
- Rust = party A: `host_priv = ec_a_private.pem`, `peer_pub = ec_b_public.der`
- Java = party B: `host_priv = ec_b_private.pem`, `peer_pub = ec_a_public.der`
- ECDH symmetry: `ECDH(a_priv, b_pub) == ECDH(b_priv, a_pub)` ✓

---

## Pipeline Integration

### Writing an encrypted stream (Rust)

```rust
use jsont::{write_encrypted_stream, EnvCryptoConfig, CryptoContext, SchemaKind};
use std::io::{BufWriter, Write};

let cfg    = EnvCryptoConfig::new("JSONT_PUB", "JSONT_PRIV");
let fields = match &schema.kind {
    SchemaKind::Straight { fields } => fields.clone(),
    _ => panic!(),
};

let mut out = BufWriter::new(std::fs::File::create("output.jsont")?);
write_encrypted_stream(&rows, &fields, &cfg, CryptoContext::VERSION_CHACHA_PUBKEY, &mut out)?;
out.flush()?;
```

### Writing an encrypted stream (Java)

```java
PublicKeyCryptoConfig cfg = PublicKeyCryptoConfig.ofKeys(pubPem, privPem);
try (CryptoContext ctx = CryptoContext.forEncrypt(AlgoVersion.CHACHA20_POLY1305, KekMode.PUBLIC_KEY, cfg);
     BufferedWriter w  = Files.newBufferedWriter(path, StandardCharsets.UTF_8);
     RowWriter writer  = new RowWriter(schema, ctx)) {
    writer.writeStream(rows, w);
}
```

### Reading an encrypted stream (Rust)

```rust
use jsont::{try_parse_encrypt_header, parse_field_payload, EnvCryptoConfig,
            JsonTRow, JsonTValue, Parseable, ValidationPipeline};

let all_rows = Vec::<JsonTRow>::parse(&content)?;

let ctx            = try_parse_encrypt_header(&all_rows[0]).expect("EncryptHeader expected");
let cfg            = EnvCryptoConfig::new("JSONT_PUB", "JSONT_PRIV");
let pipeline_sess  = cfg.open_session(&ctx)?;

let pipeline = ValidationPipeline::builder(schema)
    .without_console()
    .with_cipher_session(pipeline_sess)
    .build()?;

let clean_rows = pipeline.validate_rows(all_rows[1..].to_vec());
pipeline.finish()?;

// Manual field decrypt (fresh session — pipeline consumed previous one)
let verify_sess = cfg.open_session(&ctx)?;
if let Some(JsonTValue::Encrypted(envelope)) = clean_rows[0].get(4) {
    let (iv, _digest, enc) = parse_field_payload(envelope, "teamACoachName")?;
    let plaintext = verify_sess.decrypt_field(&iv, &enc)?;
}
```

### Reading an encrypted stream (Java)

```java
List<JsonTRow> allRows = new ArrayList<>();
JsonT.parseRows(content, allRows::add);

EncryptHeaderParser.ParsedHeader header = EncryptHeaderParser.tryParse(allRows.get(0))
    .orElseThrow(() -> new AssertionError("EncryptHeader expected"));

PublicKeyCryptoConfig cfg = PublicKeyCryptoConfig.ofKeys(pubPem, privPem);
try (CryptoContext ctx = CryptoContext.forDecrypt(header.version(), header.encDek(), cfg)) {
    ValidationPipeline pipeline = ValidationPipeline.builder(schema)
        .withCryptoContext(ctx)
        .build();
    List<JsonTRow> cleanRows = pipeline.validateRows(dataRows);

    // Manual field decrypt
    JsonTValue val = cleanRows.get(0).values().get(4);
    FieldPayload.Parsed p = FieldPayload.parse(((JsonTValue.Encrypted) val).envelope(), "teamACoachName");
    byte[] plaintext = ctx.decryptField(p.iv(), p.encContent());
}
```

---

## Cross-Compatibility

### Key Pairs (`code/cross-compat/keys/`)

| File | Purpose |
|---|---|
| `rsa_2048_public.pem` | RSA-OAEP-SHA256 public key |
| `rsa_2048_private.pem` | RSA-OAEP-SHA256 private key |
| `ec_a_public.der` / `ec_a_private.pem` | ECDH party A (Rust role) |
| `ec_b_public.der` / `ec_b_private.pem` | ECDH party B (Java role) |

### Crypto-Layer Fixtures (`code/cross-compat/fixtures/`)

Committed fixture files for each combination:

| File | Generator | Content |
|---|---|---|
| `rust_rsa_chacha20.txt` | Rust | `version`, `enc_dek_hex`, `iv_hex`, `ciphertext_hex`, `plaintext_hex` |
| `java_rsa_chacha20.txt` | Java | same format |
| `rust_ecdh_chacha20.txt` | Rust | ECDH variant |
| `java_ecdh_chacha20.txt` | Java | ECDH variant |

### Full-Stream Fixtures (`code/cross-compat/`)

| File | Generator | Schema |
|---|---|---|
| `rust-encrypted.jsont` | Rust | `CricketMatchEncrypted` — 11 fields, 2 sensitive |
| `java-encrypted.jsont` | Java | same schema |

### Test Run Order

```sh
# Step 1 — generate Rust fixtures (skip if already committed)
cargo test --test cross_compat_tests          # jsont-crypto layer
cargo test --test encrypted_cross_compat_tests  # full stream

# Step 2 — Java reads Rust fixtures and generates Java fixtures
mvn test -Dtest=CrossCompatTest               # jsont-crypto layer
mvn test -Dtest=EncryptedCrossCompatTest      # full stream

# Step 3 — Rust verifies Java fixtures
cargo test --test cross_compat_tests
cargo test --test encrypted_cross_compat_tests
```

All `gen_*` tests skip silently if the output file already exists (committed).
All `verify_*` tests fail hard if the other language's fixture is missing.

---

## Responsibility Matrix

| Type | Contains | Holds plaintext DEK | Safe to store |
|---|---|---|---|
| `CryptoContext` (Rust) | version + enc_dek (wire) | No | Yes |
| `CipherSession` (Rust) | plaintext DEK (Zeroizing) | Yes — zeroed on drop | No — short-lived |
| `CryptoContext` (Java) | version + enc_dek + DEK | Yes — zeroed on close | No — AutoCloseable |
| `CryptoConfig` impls | Env var names / key bytes | No (reads at call time) | Yes |

---

## Algorithm Versions

| `algo_ver` bits | Algorithm | IV length | Key length | Notes |
|---|---|---|---|---|
| `0001` (1) | AES-256-GCM | 12 bytes | 32 bytes | NIST standard |
| `0010` (2) | ChaCha20-Poly1305 | 12 bytes | 32 bytes | Software-optimised |
| `0011` (3) | ASCON-128a | 16 bytes | 16 bytes (first 16 of DEK) | NIST SP 800-232 |
| `0000`, `0100`–`1111` | Reserved | — | — | For future algorithms |

---

## KEK Modes

| `kek_mode` bit | Mode | Key material needed |
|---|---|---|
| `0` | RSA public key (OAEP-SHA256) | Public key for wrap; private key for unwrap |
| `1` | ECDH P-256 + HKDF | Peer public DER + host private PEM; shared secret derived in-library |

---

## CryptoError Variants

| Variant | Trigger |
|---|---|
| `KeyNotFound(var_name)` | Env var not set or key store returned nothing |
| `InvalidKey(reason)` | PEM/DER parse failed, wrong key type, or wrong size |
| `UnsupportedAlgorithm(version)` | `algo_ver` bits point to an unknown cipher |
| `UnsupportedKekMode(version)` | `kek_mode` bit points to an unknown mode |
| `DekWrapFailed(reason)` | Encrypting the raw DEK with the KEK failed |
| `DekUnwrapFailed(reason)` | Decrypting `enc_dek` with the KEK failed (wrong key, corrupt bytes) |
| `EncryptFailed(reason)` | AEAD encryption of a field value failed |
| `DecryptFailed(reason)` | AEAD decryption failed — auth tag mismatch or corrupt IV |
| `DigestMismatch(field)` | Auth tag passed but SHA-256(plaintext) ≠ stored digest |
| `MalformedPayload(field, reason)` | Per-field binary payload cannot be parsed |

`DigestMismatch` is distinct from `DecryptFailed`: the ciphertext was cryptographically valid but the plaintext was altered before encryption.

---

## Dependencies

### Rust — RustCrypto family

```toml
aes-gcm          = "0.10"   # AES-256-GCM        (algo_ver = 1)
chacha20poly1305 = "0.10"   # ChaCha20-Poly1305  (algo_ver = 2)
ascon-aead       = "0.2"    # ASCON-128a         (algo_ver = 3)
rsa              = "0.9"    # RSA key wrap       (kek_mode = 0)
p256             = "0.13"   # ECDH P-256         (kek_mode = 1)
hkdf             = "0.12"   # HKDF key derivation
sha2             = "0.10"   # SHA-256 digest
rand             = "0.8"    # IV generation
pkcs8            = "0.10"   # Key deserialization
base64           = "0.22"
zeroize          = "1"      # DEK zeroing
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
    <artifactId>bcpkix-jdk18on</artifactId>
    <version>1.78</version>
</dependency>
```

---

## Key Format

### Accepted from environment variables / `ofKeys`

| Format | Detection | Example |
| --- | --- | --- |
| Full PEM | Value starts with `-----` | `-----BEGIN PRIVATE KEY-----\nMIIE...\n-----END PRIVATE KEY-----` |
| Stripped Base64 (DER) | Anything else | `MIIEvAIBADAN...` (headers and newlines removed) |

Stripped Base64 is the recommended format for container environments (Docker, Kubernetes) where multi-line env var values cause tooling issues.
