# jsont-rust: Engineering Constitution

## Project Structure

- **Rust:** `code/rust/jsont` (Reference implementation)
- **Java 17:** `code/java/jsont` (Mirror)

## Context Routing — Load Only What's Needed

| Task type | Files to load |
| --- | --- |
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
