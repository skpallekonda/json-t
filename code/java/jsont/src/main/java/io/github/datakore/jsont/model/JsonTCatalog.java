package io.github.datakore.jsont.model;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A named group of schemas and enum definitions within a namespace.
 *
 * <p>Catalogs serve as logical partitions (e.g. one catalog per bounded context).
 * Created by {@code JsonTCatalogBuilder}.
 */
public final class JsonTCatalog {

    private final List<JsonTSchema> schemas;
    private final List<JsonTEnum> enums;

    /** Use {@code JsonTCatalogBuilder} for validated construction. */
    public JsonTCatalog(List<JsonTSchema> schemas, List<JsonTEnum> enums) {
        Objects.requireNonNull(schemas, "schemas");
        Objects.requireNonNull(enums, "enums");
        this.schemas = List.copyOf(schemas);
        this.enums = List.copyOf(enums);
    }

    /** All schemas in declaration order. */
    public List<JsonTSchema> schemas() { return schemas; }

    /** All enum definitions in declaration order. */
    public List<JsonTEnum> enums() { return enums; }

    /** Looks up a schema by name; returns {@link Optional#empty()} if absent. */
    public Optional<JsonTSchema> findSchema(String name) {
        return schemas.stream()
                .filter(s -> s.name().equals(name))
                .findFirst();
    }

    /** Looks up an enum by name; returns {@link Optional#empty()} if absent. */
    public Optional<JsonTEnum> findEnum(String name) {
        return enums.stream()
                .filter(e -> e.name().equals(name))
                .findFirst();
    }

    @Override
    public String toString() {
        return "catalog { " + schemas.size() + " schema(s), " + enums.size() + " enum(s) }";
    }
}
