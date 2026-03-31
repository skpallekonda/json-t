package io.github.datakore.jsont.json;

/**
 * Policy for schema fields that are absent from the incoming JSON object.
 */
public enum MissingFieldPolicy {
    /**
     * Use the field's declared default value; fall back to {@code null} if
     * no default is declared.
     * Default — lenient, compatible with sparse JSON sources.
     */
    USE_DEFAULT,

    /**
     * Throw {@link io.github.datakore.jsont.error.JsonTError.Parse} if any
     * schema field is absent from the JSON object.
     */
    REJECT
}
