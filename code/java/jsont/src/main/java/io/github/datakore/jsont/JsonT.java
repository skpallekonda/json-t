package io.github.datakore.jsont;

import io.github.datakore.jsont.builder.JsonTSchemaBuilder;

/**
 * Entry point for the JsonT library.
 *
 * <p>Static utilities for row parsing and row writing will be added in the
 * <em>parse</em> phase. Schema building starts with {@link JsonTSchemaBuilder}:
 *
 * <pre>{@code
 *   JsonTSchema order = JsonTSchemaBuilder.straight("Order")
 *       .fieldFrom(JsonTFieldBuilder.scalar("id",      ScalarType.I64))
 *       .fieldFrom(JsonTFieldBuilder.scalar("product", ScalarType.STR).minLength(2))
 *       .fieldFrom(JsonTFieldBuilder.scalar("qty",     ScalarType.I32).minValue(1).maxValue(999))
 *       .fieldFrom(JsonTFieldBuilder.scalar("price",   ScalarType.D64).minValue(0.01))
 *       .build();
 * }</pre>
 */
public final class JsonT {
    private JsonT() {}
}
