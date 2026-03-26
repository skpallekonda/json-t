/**
 * JsonT — single-module library with a fluent Rust-like API.
 *
 * <h2>Public packages</h2>
 * <ul>
 *   <li>{@code io.github.datakore.jsont}        — entry-point {@code JsonT} class</li>
 *   <li>{@code io.github.datakore.jsont.model}   — value types, expression tree, schema model</li>
 *   <li>{@code io.github.datakore.jsont.builder} — fluent builders + {@code SchemaRegistry}</li>
 *   <li>{@code io.github.datakore.jsont.error}   — exception hierarchy ({@code JsonTError}, {@code BuildError})</li>
 * </ul>
 *
 * <p>The following packages will be added in subsequent phases:
 * <ul>
 *   <li>{@code io.github.datakore.jsont.parse}      — row parsing</li>
 *   <li>{@code io.github.datakore.jsont.stringify}   — schema and row serialisation</li>
 *   <li>{@code io.github.datakore.jsont.validate}    — validation pipeline</li>
 *   <li>{@code io.github.datakore.jsont.transform}   — schema-driven row transformation</li>
 *   <li>{@code io.github.datakore.jsont.diagnostic}  — diagnostic sinks (console, file, memory)</li>
 * </ul>
 *
 * <p>{@code io.github.datakore.jsont.internal} is intentionally NOT exported —
 * all implementation classes are package-private and invisible to consumers.
 */
module io.github.datakore.jsont {

    // ── Public API ───────────────────────────────────────────────────────────
    exports io.github.datakore.jsont;
    exports io.github.datakore.jsont.model;
    exports io.github.datakore.jsont.builder;
    exports io.github.datakore.jsont.error;

    // ── Phases to come ───────────────────────────────────────────────────────
    // exports io.github.datakore.jsont.parse;
    // exports io.github.datakore.jsont.stringify;
    // exports io.github.datakore.jsont.validate;
    // exports io.github.datakore.jsont.transform;
    // exports io.github.datakore.jsont.diagnostic;

    // ── io.github.datakore.jsont.internal  is NOT exported ──────────────────

    requires org.antlr.antlr4.runtime;
}
