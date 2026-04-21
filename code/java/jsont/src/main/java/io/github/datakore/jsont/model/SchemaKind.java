package io.github.datakore.jsont.model;

/**
 * Distinguishes whether a schema was built from scratch or derived from another.
 *
 * <ul>
 *   <li>{@code STRAIGHT} — declares its own fields and optional validation block.</li>
 *   <li>{@code DERIVED} — takes a parent schema and applies a sequence of
 *       {@link SchemaOperation}s to produce a projection or transformation.</li>
 * </ul>
 */
public enum SchemaKind {
    STRAIGHT,
    DERIVED;
}
