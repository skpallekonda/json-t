package io.github.datakore.jsont.model;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Top-level container for all catalogs, schemas, and enums in a JsonT document.
 *
 * <p>A namespace is the unit of schema sharing: all schema references across
 * catalogs within the same namespace are resolvable.
 *
 * <p>Built via {@code JsonTNamespaceBuilder} or parsed from text (parse phase):
 *
 * <pre>{@code
 *   // Programmatic construction
 *   JsonTNamespace ns = JsonTNamespaceBuilder.create()
 *       .baseUrl("https://api.example.com/v1")
 *       .catalog(JsonTCatalogBuilder.create()
 *           .schema(JsonTSchemaBuilder.straight("Order") ... .build())
 *           .build())
 *       .build();
 *
 *   ns.findSchema("Order");   // Optional<JsonTSchema>
 *   ns.findEnum("Status");    // Optional<JsonTEnum>
 * }</pre>
 */
public final class JsonTNamespace {

    private final String baseUrl;
    private final String version;
    private final String dataSchema;
    private final List<JsonTCatalog> catalogs;

    /** Use {@code JsonTNamespaceBuilder} for validated construction. */
    public JsonTNamespace(String baseUrl, String version, String dataSchema, List<JsonTCatalog> catalogs) {
        Objects.requireNonNull(catalogs, "catalogs");
        this.baseUrl = baseUrl == null ? "" : baseUrl;
        this.version = version == null ? "" : version;
        this.dataSchema = dataSchema == null ? "" : dataSchema;
        this.catalogs = List.copyOf(catalogs);
    }

    /** The base URL declared in the namespace header (may be empty). */
    public String baseUrl() { return baseUrl; }

    /** The version declared in the namespace header (may be empty). */
    public String version() { return version; }

    /** The main data entry schema (may be empty). */
    public String dataSchema() { return dataSchema; }

    /** All catalogs in declaration order (immutable). */
    public List<JsonTCatalog> catalogs() { return catalogs; }

    /**
     * Searches all catalogs for a schema with the given name.
     *
     * @return the first match found, or {@link Optional#empty()}
     */
    public Optional<JsonTSchema> findSchema(String name) {
        return catalogs.stream()
                .flatMap(c -> c.schemas().stream())
                .filter(s -> s.name().equals(name))
                .findFirst();
    }

    /**
     * Searches all catalogs for an enum with the given name.
     *
     * @return the first match found, or {@link Optional#empty()}
     */
    public Optional<JsonTEnum> findEnum(String name) {
        return catalogs.stream()
                .flatMap(c -> c.enums().stream())
                .filter(e -> e.name().equals(name))
                .findFirst();
    }

    /** Total number of schemas across all catalogs. */
    public long schemaCount() {
        return catalogs.stream().mapToLong(c -> c.schemas().size()).sum();
    }

    /** Total number of enums across all catalogs. */
    public long enumCount() {
        return catalogs.stream().mapToLong(c -> c.enums().size()).sum();
    }

    @Override
    public String toString() {
        return "namespace { baseUrl=" + baseUrl
                + ", catalogs=" + catalogs.size()
                + ", schemas=" + schemaCount()
                + ", enums=" + enumCount() + " }";
    }
}
