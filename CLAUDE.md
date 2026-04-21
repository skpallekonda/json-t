# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**JSON-T** is a schema-driven, positional data language — like CSV with strong typing and validation. Payloads are 45-55% smaller than JSON. Both implementations are production-quality and must remain functionally equivalent.

- **Rust** (`code/rust/jsont/`) — reference implementation, PEG parser via `pest`
- **Java 17** (`code/java/jsont/`) — mirror implementation, ANTLR4 parser

## Build & Test Commands

### Rust
```bash
# from code/rust/
cargo build                                              # debug build
cargo build --release                                    # release build
cargo test                                               # all tests (excludes benchmarks)
cargo test --test validation_tests                       # single test file
cargo test --test validation_tests -- test_fn_prefix     # single test function
cargo test -- --nocapture                                # with stdout
cargo test --features bench bench_wct -- --nocapture    # benchmark suite
cargo clippy -- -D warnings                              # lint (warnings as errors)
cargo fmt -- --check                                     # format check
```

### Java
```bash
# from code/java/
mvn clean test                          # all tests (excludes @Tag("benchmark"))
mvn test -Dtest=ValidationTest          # single test class
mvn test -Dtest=ValidationTest#method   # single test method
mvn test -Pbenchmark                    # benchmark tests only
mvn clean package                       # build JAR
```

## Architecture

### Three-Layer Model

```
Foundation Layer  →  parse · validate · transform   (implemented)
Execution Layer   →  pipeline runtime               (planned)
Interface Layer   →  service/API definitions        (planned)
```

Always target the **Foundation Layer** unless explicitly asked to touch Execution or Interface.

### Data Flow

```
DSL text ──► Schema (parse) ──► Rows (stream) ──► Validate ──► DiagnosticSink
                                                      │
                                              DerivedSchema (transform)
                                                      │
                                               Filtered Rows
```

Row parsing is **O(1) memory** — the scanner yields one row at a time; never buffers the full dataset.

### Grammar Files

| Impl | File | Technology |
|------|------|-----------|
| Rust | `code/rust/jsont/src/jsont.pest` | Pest PEG (compile-time) |
| Java | `code/java/jsont/src/main/antlr4/.../JsonTSchema.g4` | ANTLR4 (build-time, visitor only) |

Both grammars implement the same language spec (`docs/language-spec.md`).

### Key Concepts

- **Straight schema** — explicit field declarations with constraints
- **Derived schema** — inherits parent schema + applies operations (`Project`, `Exclude`, `Rename`, `Filter`, `Transform`, `Decrypt`)
- **Privacy marker** (`~`) — field-level encryption; wire format is `base64:<ciphertext>`; pluggable `CryptoConfig`
- **Validation block** — row-level rules, unique constraints, conditional requirements
- **27 scalar types** — numeric (I16–D128), temporal, string-like (Uri/Uuid/Email), binary (Base64/Hex/Oid)

## Context Routing — Load Only What's Needed

| Task type | Files to load |
|-----------|---------------|
| Unfamiliar / general question | `ai_context/00_summary.md` |
| Design / architecture | `ai_context/01_principles.md` + `ai_context/03_decisions.md` |
| Current status | `ai_context/04_current_state.md` |
| Planning / priorities | `ai_context/05_next_actions.md` |
| Code / implementation | `ai_context/06_technical_map.md` |
| Feature roadmap | `ai_context/99_features.md` |

Never load all files. Start with one; expand only if the answer isn't there.

## Code Query Protocol

For any coding task (add feature, fix bug, modify API, understand behavior):

1. **Read `ai_context/06_technical_map.md` first** — it contains full API signatures, types, builders, pipeline patterns, and error hierarchies for both Rust and Java.
2. **Only open source files** if the technical map is missing a specific detail (e.g., internal algorithm, private helper, exact line to edit).
3. **Do not open source files speculatively** — the map covers all public interfaces.

## Hard Constraints

- Schema-first, streaming-first — never load full datasets into memory
- Core layer first; don't introduce Transform/Runtime unless explicitly needed
- Prefer minimal, incremental changes — no over-engineering
- Ask before introducing new abstractions or changing architecture scope

## Safety Valve

If context usage > 95%, save state to `STATE.md` and stop.
