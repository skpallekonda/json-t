package io.github.datakore.jsont.builder;

import io.github.datakore.jsont.error.BuildError;
import io.github.datakore.jsont.model.JsonTCatalog;
import io.github.datakore.jsont.model.JsonTEnum;
import io.github.datakore.jsont.model.JsonTSchema;

import java.util.ArrayList;
import java.util.List;

/**
 * Fluent builder for {@link JsonTCatalog}.
 *
 * <pre>{@code
 *   JsonTCatalog orders = JsonTCatalogBuilder.create()
 *       .schema(JsonTSchemaBuilder.straight("Order")
 *           .fieldFrom(JsonTFieldBuilder.scalar("id", ScalarType.I64))
 *           .build())
 *       .schema(JsonTSchemaBuilder.derived("OrderSummary", "Order")
 *           .operation(SchemaOperation.project(FieldPath.single("id")))
 *           .build())
 *       .enum_("Status", "ACTIVE", "INACTIVE", "SUSPENDED")
 *       .build();
 * }</pre>
 */
public final class JsonTCatalogBuilder {

    private final List<JsonTSchema> schemas = new ArrayList<>();
    private final List<JsonTEnum> enums = new ArrayList<>();

    private JsonTCatalogBuilder() {}

    /** Returns a new, empty catalog builder. */
    public static JsonTCatalogBuilder create() {
        return new JsonTCatalogBuilder();
    }

    // ─── Schemas ──────────────────────────────────────────────────────────────

    /**
     * Adds an already-built schema.
     *
     * @param schema the schema to add
     */
    public JsonTCatalogBuilder schema(JsonTSchema schema) {
        if (schema == null) throw new IllegalArgumentException("schema must not be null");
        schemas.add(schema);
        return this;
    }

    /**
     * Builds and adds a schema from the supplied builder.
     *
     * @param builder schema builder (already configured)
     * @throws BuildError if the schema is misconfigured
     */
    public JsonTCatalogBuilder schema(JsonTSchemaBuilder builder) throws BuildError {
        if (builder == null) throw new BuildError("Schema builder must not be null");
        return schema(builder.build());
    }

    // ─── Enums ────────────────────────────────────────────────────────────────

    /**
     * Adds an enum definition.
     *
     * @param name   enum name (must start with uppercase)
     * @param values one or more ALL_CAPS enum constant names
     * @throws BuildError if name is blank or no values are provided
     */
    public JsonTCatalogBuilder enum_(String name, String... values) throws BuildError {
        if (name == null || name.isBlank())
            throw new BuildError("Enum name must not be blank");
        if (values == null || values.length == 0)
            throw new BuildError("Enum '" + name + "' must have at least one value");
        enums.add(new JsonTEnum(name, List.of(values)));
        return this;
    }

    /**
     * Adds an already-constructed enum.
     */
    public JsonTCatalogBuilder enum_(JsonTEnum enumDef) {
        if (enumDef == null) throw new IllegalArgumentException("enum must not be null");
        enums.add(enumDef);
        return this;
    }

    // ─── Build ────────────────────────────────────────────────────────────────

    /**
     * Validates state and constructs an immutable {@link JsonTCatalog}.
     *
     * @throws BuildError if no schemas have been added
     */
    public JsonTCatalog build() throws BuildError {
        if (schemas.isEmpty())
            throw new BuildError("Catalog must contain at least one schema");
        // duplicate schema name check
        long distinct = schemas.stream().map(JsonTSchema::name).distinct().count();
        if (distinct != schemas.size())
            throw new BuildError("Catalog contains duplicate schema names");
        return new JsonTCatalog(schemas, enums);
    }
}
