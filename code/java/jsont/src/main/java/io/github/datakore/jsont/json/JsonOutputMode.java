package io.github.datakore.jsont.json;

/**
 * Controls how JSON output is structured when writing with {@link JsonWriter}.
 */
public enum JsonOutputMode {
    /**
     * One JSON object per line (newline-delimited JSON).
     * Default mode — streaming-safe, no wrapping needed.
     */
    NDJSON,

    /**
     * All rows wrapped in a JSON array: {@code [{...}, {...}]}.
     */
    ARRAY
}
