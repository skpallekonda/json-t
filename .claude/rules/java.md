# Java Implementation Rules (Scope: code/java/**/*)

## ☕ Patterns (Java 17)
- **Fluid Design:** Use `records` and fluent builders (no `set` prefix).
- **Idempotency:** Ensure service methods are side-effect free on retry.
- **Reflections:** Avoid using reflections, use explicit method calls instead.  Where possible, use micronaut styled annotation processing, that avoids reflections, and provide static compile time checkable code.
- **Sync/Async:** Always prefer non-blocking I/O and async operations where appropriate, but ensure that the API is easy to use for both sync and async contexts.
- **Lombok:** Avoid lombok, use records instead.
- **Documentation:** Always provide clear and concise documentation for the code, and ensure that the code is well-commented.
- **JSON Library:** None — this project IS the JSON-T parser/serializer. Grammar parsing uses ANTLR4 runtime `4.13.0` (visitor pattern enabled, listener disabled). No Jackson, Gson, or other external JSON library is present or needed.
