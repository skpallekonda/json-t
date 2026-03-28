# jsont-rust: Engineering Constitution

## 📂 Project Structure
- **Rust:** `code/rust/jsont` (Reference)
- **Java 17:** `code/java/jsont` (Mirror)

## 🏗️ Design Philosophy (HARD CONSTRAINTS)
- **JsonT Grammar** must be same, irrespective of language implementation
- **SOLID & Stateless:** Prioritize Single Responsibility. Avoid internal state.
- **Thread-Safety:** Any essential state must use thread-safe primitives (Atomic/Concurrent in Java, Mutex/Arc in Rust).
- **Fluid & Idempotent:** Use Fluid APIs and Builder patterns. Ensure operations are idempotent.
- **Constraint Protocol:** If a task requires violating these principles, **stop and ask for confirmation** on alternate options before proceeding.

## ✍️ Manual Style Guide
- **Comments:** Explain *why*, not *what*. No boilerplate/docstrings for obvious methods.
- **Names:** Use `ctx`, `cfg`, `err`, `val` for local variables.
- **TODO (sasi):** [Enter any specific library bans or mandatory crates here]
- **Style:** 4-space indentation; K&R braces (`{` on same line as declaration/control statement); trivial single-expression methods collapsed to one line (`@Override public String toString() { return "null"; }`); no blank lines between adjacent single-line record fields.

## Use Codegraph
- Always use codegraph to understand the codebase, read `.codegraph/CLAUDE.md` for instructions.

## 🚨 Safety Valve
- Monitor usage. If > 95%, execute the Panic Save to `STATE.md` and stop.
