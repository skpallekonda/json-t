package io.github.datakore.jsont.json;

/**
 * Policy for JSON keys that have no matching field in the schema.
 */
public enum UnknownFieldPolicy {
    /**
     * Silently ignore unknown JSON fields.
     * Default — tolerant of extra data from upstream systems.
     */
    SKIP,

    /**
     * Throw {@link io.github.datakore.jsont.error.JsonTError.Parse} if an
     * unknown JSON field is encountered.
     */
    REJECT
}
