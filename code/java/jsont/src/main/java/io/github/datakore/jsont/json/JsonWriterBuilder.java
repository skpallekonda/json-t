package io.github.datakore.jsont.json;

import io.github.datakore.jsont.model.JsonTSchema;

/**
 * Fluent builder for {@link JsonWriter}. Obtain via {@link JsonWriter#withSchema}.
 */
public final class JsonWriterBuilder {

    private final JsonTSchema schema;
    private JsonOutputMode mode   = JsonOutputMode.NDJSON;
    private boolean        pretty = false;

    JsonWriterBuilder(JsonTSchema schema) {
        this.schema = schema;
    }

    /**
     * Set the output mode. Default: {@link JsonOutputMode#NDJSON}.
     *
     * @param mode the desired output mode
     * @return this builder
     */
    public JsonWriterBuilder mode(JsonOutputMode mode) {
        this.mode = mode;
        return this;
    }

    /**
     * Enable pretty-printed (indented) output. Default: compact.
     *
     * @return this builder
     */
    public JsonWriterBuilder pretty() {
        this.pretty = true;
        return this;
    }

    /**
     * Build the configured {@link JsonWriter}.
     *
     * @return a new JsonWriter
     */
    public JsonWriter build() {
        return new JsonWriter(schema, mode, pretty);
    }
}
