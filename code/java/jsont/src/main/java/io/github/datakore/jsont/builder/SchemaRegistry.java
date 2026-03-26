package io.github.datakore.jsont.builder;

import io.github.datakore.jsont.model.JsonTCatalog;
import io.github.datakore.jsont.model.JsonTNamespace;
import io.github.datakore.jsont.model.JsonTSchema;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * An immutable, name-keyed registry of {@link JsonTSchema} instances.
 *
 * <p>Acts as the runtime resolution point for schema references in derived schemas
 * and transformations. Equivalent to Rust's {@code SchemaRegistry}.
 *
 * <pre>{@code
 *   // Build from a namespace parsed from disk
 *   SchemaRegistry registry = SchemaRegistry.fromNamespace(ns);
 *
 *   // Build manually and extend
 *   SchemaRegistry registry = SchemaRegistry.empty()
 *       .register(orderSchema)
 *       .register(summarySchema);
 *
 *   registry.resolve("Order");         // Optional<JsonTSchema>
 *   registry.names();                  // Set<String>
 * }</pre>
 */
public final class SchemaRegistry {

    private final Map<String, JsonTSchema> schemas;

    private SchemaRegistry(Map<String, JsonTSchema> schemas) {
        this.schemas = Map.copyOf(schemas);
    }

    // ─── Factory methods ──────────────────────────────────────────────────────

    /** Returns an empty registry. */
    public static SchemaRegistry empty() {
        return new SchemaRegistry(Map.of());
    }

    /**
     * Builds a registry from all schemas found in every catalog of the namespace.
     * Schemas declared later in the namespace override earlier ones with the same name.
     */
    public static SchemaRegistry fromNamespace(JsonTNamespace ns) {
        if (ns == null) throw new IllegalArgumentException("namespace must not be null");
        Map<String, JsonTSchema> map = new LinkedHashMap<>();
        for (JsonTCatalog catalog : ns.catalogs()) {
            for (JsonTSchema schema : catalog.schemas()) {
                map.put(schema.name(), schema);
            }
        }
        return new SchemaRegistry(map);
    }

    // ─── Mutation (returns new registry) ──────────────────────────────────────

    /**
     * Returns a new registry that additionally contains {@code schema}.
     * If a schema with the same name already exists it is replaced.
     */
    public SchemaRegistry register(JsonTSchema schema) {
        if (schema == null) throw new IllegalArgumentException("schema must not be null");
        Map<String, JsonTSchema> updated = new LinkedHashMap<>(schemas);
        updated.put(schema.name(), schema);
        return new SchemaRegistry(updated);
    }

    /**
     * Returns a new registry with all schemas from {@code other} merged in
     * (other's schemas win on name collision).
     */
    public SchemaRegistry merge(SchemaRegistry other) {
        if (other == null) throw new IllegalArgumentException("other must not be null");
        Map<String, JsonTSchema> merged = new LinkedHashMap<>(schemas);
        merged.putAll(other.schemas);
        return new SchemaRegistry(merged);
    }

    // ─── Lookup ───────────────────────────────────────────────────────────────

    /**
     * Resolves a schema by name.
     *
     * @param name schema name
     * @return the schema, or {@link Optional#empty()} if not registered
     */
    public Optional<JsonTSchema> resolve(String name) {
        return Optional.ofNullable(schemas.get(name));
    }

    /**
     * Resolves a schema by name, throwing if absent.
     *
     * @param name schema name
     * @return the schema
     * @throws IllegalArgumentException if not registered
     */
    public JsonTSchema resolveOrThrow(String name) {
        JsonTSchema s = schemas.get(name);
        if (s == null)
            throw new IllegalArgumentException("Schema '" + name + "' is not registered in this registry");
        return s;
    }

    /** Returns {@code true} if a schema with the given name is registered. */
    public boolean contains(String name) {
        return schemas.containsKey(name);
    }

    /** Returns an unmodifiable view of all registered schema names. */
    public Set<String> names() {
        return schemas.keySet();
    }

    /** Returns the number of registered schemas. */
    public int size() {
        return schemas.size();
    }

    /** Returns {@code true} if no schemas are registered. */
    public boolean isEmpty() {
        return schemas.isEmpty();
    }

    @Override
    public String toString() {
        return "SchemaRegistry" + schemas.keySet();
    }
}
