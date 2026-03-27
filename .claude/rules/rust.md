# Rust Implementation Rules (Scope: code/rust/**/*)

## 🦀 Patterns
- **Fluidity:** Use the `Builder` pattern for complex Structs.
- **Thread-Safety:** Favor `Send + Sync` traits.
- **Primary Crates:** `pest = "2"` (PEG parser), `pest_derive = "2"` (grammar macros), `regex = "1"` (pattern constraint validation), `rust_decimal = "1"` with `serde` feature (D128 arbitrary-precision decimal), `thiserror = "1"` (ergonomic error types). No `serde`, `tokio`, or async runtime crate is present — do not add them without explicit request.
- **Sync/Async:** Always prefer non-blocking I/O and async operations where appropriate, but ensure that the API is easy to use for both sync and async contexts.
- **Error Handling:** Use `Result` and `Option` types for error handling, and provide clear and concise error messages.
- **Documentation:** Always provide clear and concise documentation for the code, and ensure that the code is well-commented.
- **Performance:** Always prefer performance-optimized solutions, and ensure that the code is well-optimized.