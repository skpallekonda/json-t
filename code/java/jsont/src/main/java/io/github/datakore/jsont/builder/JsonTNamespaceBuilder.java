package io.github.datakore.jsont.builder;

import io.github.datakore.jsont.error.BuildError;
import io.github.datakore.jsont.model.JsonTCatalog;
import io.github.datakore.jsont.model.JsonTNamespace;

import java.util.ArrayList;
import java.util.List;

/**
 * Fluent builder for {@link JsonTNamespace}.
 *
 * <pre>{@code
 * JsonTNamespace ns = JsonTNamespaceBuilder.create()
 *         .baseUrl("https://api.example.com/v1")
 *         .catalog(JsonTCatalogBuilder.create()
 *                 .schema(JsonTSchemaBuilder.straight("Order")
 *                         .fieldFrom(JsonTFieldBuilder.scalar("id", ScalarType.I64))
 *                         .fieldFrom(JsonTFieldBuilder.scalar("product", ScalarType.STR))
 *                         .build())
 *                 .build())
 *         .build();
 * }</pre>
 */
public final class JsonTNamespaceBuilder {

    private String baseUrl = "";
    private String version = "";
    private String dataSchema = "";
    private final List<JsonTCatalog> catalogs = new ArrayList<>();

    private JsonTNamespaceBuilder() {
    }

    /** Returns a new, empty namespace builder. */
    public static JsonTNamespaceBuilder create() {
        return new JsonTNamespaceBuilder();
    }

    // ─── Configuration ────────────────────────────────────────────────────────

    /**
     * Sets the base URL for schema resolution (informational; used in stringify
     * output).
     *
     * @param url the base URL (may be empty but not null)
     */
    public JsonTNamespaceBuilder baseUrl(String url) {
        this.baseUrl = url == null ? "" : url;
        return this;
    }

    /**
     * Sets the version for schema resolution (informational; used in stringify
     * output).
     *
     * @param version the version (may be empty but not null)
     */
    public JsonTNamespaceBuilder version(String version) {
        this.version = version == null ? "" : version;
        return this;
    }

    /**
     * Sets the main data entry schema.
     *
     * @param dataSchema the main data entry schema (may be empty but not null)
     */
    public JsonTNamespaceBuilder dataSchema(String dataSchema) {
        this.dataSchema = dataSchema == null ? "" : dataSchema;
        return this;
    }

    // ─── Catalogs ─────────────────────────────────────────────────────────────

    /**
     * Adds an already-built catalog.
     *
     * @param catalog the catalog to add
     */
    public JsonTNamespaceBuilder catalog(JsonTCatalog catalog) {
        if (catalog == null)
            throw new IllegalArgumentException("catalog must not be null");
        catalogs.add(catalog);
        return this;
    }

    /**
     * Builds and adds a catalog from the supplied builder.
     *
     * @param builder catalog builder (already configured)
     * @throws BuildError if the catalog is misconfigured
     */
    public JsonTNamespaceBuilder catalog(JsonTCatalogBuilder builder) throws BuildError {
        if (builder == null)
            throw new BuildError("Catalog builder must not be null");
        return catalog(builder.build());
    }

    // ─── Build ────────────────────────────────────────────────────────────────

    /**
     * Validates state and constructs an immutable {@link JsonTNamespace}.
     *
     * @throws BuildError if no catalogs have been added
     */
    public JsonTNamespace build() throws BuildError {
        if (catalogs.isEmpty())
            throw new BuildError("Namespace must contain at least one catalog");
        return new JsonTNamespace(baseUrl, version, dataSchema, catalogs);
    }
}
