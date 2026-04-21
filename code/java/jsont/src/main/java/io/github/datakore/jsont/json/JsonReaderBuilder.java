package io.github.datakore.jsont.json;

import io.github.datakore.jsont.model.JsonTSchema;

/**
 * Fluent builder for {@link JsonReader}. Obtain via {@link JsonReader#withSchema}.
 */
public final class JsonReaderBuilder {

    private final JsonTSchema schema;
    private JsonInputMode      mode          = JsonInputMode.NDJSON;
    private UnknownFieldPolicy unknownFields = UnknownFieldPolicy.SKIP;
    private MissingFieldPolicy missingFields = MissingFieldPolicy.USE_DEFAULT;

    JsonReaderBuilder(JsonTSchema schema) {
        this.schema = schema;
    }

    /**
     * Set the input mode. Default: {@link JsonInputMode#NDJSON}.
     *
     * @param mode the desired input mode
     * @return this builder
     */
    public JsonReaderBuilder mode(JsonInputMode mode) {
        this.mode = mode;
        return this;
    }

    /**
     * Set the policy for JSON keys absent from the schema. Default: {@link UnknownFieldPolicy#SKIP}.
     *
     * @param policy the unknown-field policy
     * @return this builder
     */
    public JsonReaderBuilder unknownFields(UnknownFieldPolicy policy) {
        this.unknownFields = policy;
        return this;
    }

    /**
     * Set the policy for schema fields absent from the JSON object. Default: {@link MissingFieldPolicy#USE_DEFAULT}.
     *
     * @param policy the missing-field policy
     * @return this builder
     */
    public JsonReaderBuilder missingFields(MissingFieldPolicy policy) {
        this.missingFields = policy;
        return this;
    }

    /**
     * Build the configured {@link JsonReader}.
     *
     * @return a new JsonReader
     */
    public JsonReader build() {
        return new JsonReader(schema, mode, unknownFields, missingFields);
    }
}
