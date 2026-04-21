package io.github.datakore.jsont.json;

/**
 * Controls how JSON input is structured when reading with {@link JsonReader}.
 */
public enum JsonInputMode {
    /**
     * One JSON object per line (newline-delimited JSON).
     * Default mode — true O(1) streaming.
     */
    NDJSON,

    /**
     * A JSON array of objects: {@code [{...}, {...}]}.
     * The entire input is buffered before parsing.
     */
    ARRAY,

    /**
     * A single JSON object; produces exactly one row.
     */
    OBJECT
}
