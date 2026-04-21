# Contributing to JSON-T

Thank you for your interest in contributing. JSON-T has two production implementations — Rust (reference) and Java 17 (mirror) — that must remain in sync at the grammar level.

---

## Prerequisites

| Tool | Minimum version |
|---|---|
| Rust toolchain (`rustup`) | stable |
| JDK | 17 |
| Maven | 3.9 |
| Git | any recent |

---

## Building

**Rust:**
```sh
cd code/rust
cargo build
```

**Java:**
```sh
cd code/java
mvn clean package -DskipTests
```

---

## Running Tests

**Rust (unit + integration):**
```sh
cd code/rust
cargo test
```

**Rust (performance benchmarks — opt-in only):**
```sh
cd code/rust/jsont
cargo test --release --features bench bench_wct -- --nocapture
```

**Java (unit + integration):**
```sh
cd code/java
mvn test
```

**Java (benchmark suite — opt-in only):**
```sh
cd code/java
mvn test -Pbenchmark
```

### Cross-compatibility tests

The `code/cross-compat/` directory holds persistent key pairs and committed fixture files that verify Rust and Java produce wire-compatible output. Run them in this order when adding encryption changes:

```sh
# 1. Rust generates its fixtures (no-ops if files already exist)
cd code/rust
cargo test --test cross_compat_tests            # crypto layer (jsont-crypto)
cargo test --test encrypted_cross_compat_tests  # full encrypted stream (jsont)

# 2. Java reads Rust fixtures and generates Java fixtures
cd code/java
mvn test -Dtest=CrossCompatTest                 # crypto layer
mvn test -Dtest=EncryptedCrossCompatTest        # full encrypted stream

# 3. Rust verifies Java fixtures
cd code/rust
cargo test --test cross_compat_tests
cargo test --test encrypted_cross_compat_tests
```

`gen_*` tests skip silently when the output file already exists.
`verify_*` tests fail hard when the other language's fixture is missing — this is intentional.

---

## Contribution Guidelines

### Grammar changes

The JsonT DSL grammar is the source of truth for both implementations. Any grammar change must be reflected in **both**:
- `code/rust/jsont/src/jsont.pest` (PEG — Rust)
- `code/java/jsont/src/main/antlr4/.../JsonTSchema.g4` (ANTLR4 — Java)

Open an issue to discuss grammar changes before opening a PR — breaking grammar changes require coordinated updates across both parsers and all documentation.

### Code style

- **Rust**: 4-space indentation, K&R braces, comments explain *why* not *what*.
- **Java 17**: Records and fluent builders (`no set` prefix), no Lombok, no reflection, Java 17 features only.
- Do not add docstrings or comments to obvious methods.
- Do not introduce new external dependencies without prior discussion.

### Keeping implementations in sync

Both implementations must produce identical output for the same input schema and data. The `code/cross-compat/` directory contains cross-compatibility test fixtures used to verify this. Any new feature must include cross-compat fixtures.

---

## Pull Request Process

1. Fork the repo and create a branch from `main`.
2. Make your changes with tests.
3. Ensure all tests pass (`cargo test` and `mvn test`).
4. Open a PR against `main` with a clear description of the change and why.
5. Link any related issues.

PRs that touch the grammar, public API surface, or performance-sensitive paths will receive closer review.

---

## Reporting Issues

Use [GitHub Issues](https://github.com/datakore/json-t/issues). Include:
- A minimal reproducible schema/data snippet
- The actual vs. expected behaviour
- Which implementation (Rust / Java / both)

---

## License

By contributing, you agree that your contributions will be licensed under the [MIT License](LICENSE).
