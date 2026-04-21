package io.github.datakore.jsont.builder;

import io.github.datakore.jsont.model.JsonTSchema;

import java.util.Optional;

/**
 * Minimal read-only view of a schema registry used by internal utilities.
 *
 * <p>{@link SchemaRegistry} implements this interface, so all existing call
 * sites that pass a {@code SchemaRegistry} continue to compile unchanged.
 * Internal validators that need to look up schemas without holding a full
 * {@code SchemaRegistry} instance can implement this interface directly.
 */
public interface SchemaResolver {

    /** Returns {@code true} if a schema with {@code name} is registered. */
    boolean contains(String name);

    /** Returns the schema registered under {@code name}, or empty. */
    Optional<JsonTSchema> resolve(String name);

    /**
     * Returns the schema registered under {@code name}.
     *
     * @throws IllegalArgumentException if not registered
     */
    JsonTSchema resolveOrThrow(String name);
}
