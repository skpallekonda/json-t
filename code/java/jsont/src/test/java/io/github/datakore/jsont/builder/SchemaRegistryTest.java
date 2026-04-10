package io.github.datakore.jsont.builder;

import io.github.datakore.jsont.error.BuildError;
import io.github.datakore.jsont.error.JsonTError;
import io.github.datakore.jsont.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static io.github.datakore.jsont.model.SchemaOperation.project;

class SchemaRegistryTest {

    private JsonTSchema orderSchema;
    private JsonTSchema userSchema;

    @BeforeEach
    void setUp() throws BuildError {
        orderSchema = JsonTSchemaBuilder.straight("Order")
                .fieldFrom(JsonTFieldBuilder.scalar("id",      ScalarType.I64))
                .fieldFrom(JsonTFieldBuilder.scalar("product", ScalarType.STR))
                .build();

        userSchema = JsonTSchemaBuilder.straight("User")
                .fieldFrom(JsonTFieldBuilder.scalar("id",    ScalarType.I64))
                .fieldFrom(JsonTFieldBuilder.scalar("email", ScalarType.STR))
                .build();
    }

    // ─── empty() ─────────────────────────────────────────────────────────────

    @Test
    void empty_hasNoSchemas() {
        SchemaRegistry registry = SchemaRegistry.empty();
        assertTrue(registry.isEmpty());
        assertEquals(0, registry.size());
        assertTrue(registry.names().isEmpty());
    }

    // ─── register() ──────────────────────────────────────────────────────────

    @Test
    void register_addsSchema() {
        SchemaRegistry registry = SchemaRegistry.empty().register(orderSchema);
        assertEquals(1, registry.size());
        assertTrue(registry.contains("Order"));
    }

    @Test
    void register_returnsNewRegistry_originalUnchanged() {
        SchemaRegistry original = SchemaRegistry.empty();
        SchemaRegistry updated  = original.register(orderSchema);
        assertTrue(original.isEmpty());
        assertEquals(1, updated.size());
    }

    @Test
    void register_replacesExistingByName() throws BuildError {
        JsonTSchema v2 = JsonTSchemaBuilder.straight("Order")
                .fieldFrom(JsonTFieldBuilder.scalar("id",      ScalarType.I64))
                .fieldFrom(JsonTFieldBuilder.scalar("product", ScalarType.STR))
                .fieldFrom(JsonTFieldBuilder.scalar("version", ScalarType.I32))
                .build();

        SchemaRegistry registry = SchemaRegistry.empty()
                .register(orderSchema)
                .register(v2);

        assertEquals(1, registry.size());
        assertEquals(3, registry.resolveOrThrow("Order").fieldCount());
    }

    // ─── fromNamespace() ──────────────────────────────────────────────────────

    @Test
    void fromNamespace_registersAllSchemas() throws BuildError {
        JsonTNamespace ns = JsonTNamespaceBuilder.create()
                .catalog(JsonTCatalogBuilder.create()
                        .schema(orderSchema)
                        .schema(userSchema)
                        .build())
                .build();

        SchemaRegistry registry = SchemaRegistry.fromNamespace(ns);
        assertEquals(2, registry.size());
        assertTrue(registry.contains("Order"));
        assertTrue(registry.contains("User"));
    }

    @Test
    void fromNamespace_multiCatalog_mergesAllSchemas() throws BuildError {
        JsonTNamespace ns = JsonTNamespaceBuilder.create()
                .catalog(JsonTCatalogBuilder.create().schema(orderSchema).build())
                .catalog(JsonTCatalogBuilder.create().schema(userSchema).build())
                .build();

        SchemaRegistry registry = SchemaRegistry.fromNamespace(ns);
        assertEquals(2, registry.size());
    }

    // ─── resolve() ───────────────────────────────────────────────────────────

    @Test
    void resolve_returnsSchemaWhenPresent() {
        SchemaRegistry registry = SchemaRegistry.empty().register(orderSchema);
        assertTrue(registry.resolve("Order").isPresent());
        assertSame(orderSchema, registry.resolve("Order").get());
    }

    @Test
    void resolve_returnsEmptyWhenAbsent() {
        SchemaRegistry registry = SchemaRegistry.empty();
        assertTrue(registry.resolve("Nonexistent").isEmpty());
    }

    @Test
    void resolveOrThrow_throwsWhenAbsent() {
        SchemaRegistry registry = SchemaRegistry.empty();
        assertThrows(IllegalArgumentException.class,
                () -> registry.resolveOrThrow("Missing"));
    }

    // ─── merge() ──────────────────────────────────────────────────────────────

    @Test
    void merge_combinesTwoRegistries() {
        SchemaRegistry r1 = SchemaRegistry.empty().register(orderSchema);
        SchemaRegistry r2 = SchemaRegistry.empty().register(userSchema);
        SchemaRegistry merged = r1.merge(r2);

        assertEquals(2, merged.size());
        assertTrue(merged.contains("Order"));
        assertTrue(merged.contains("User"));
    }

    @Test
    void merge_otherWinsOnNameCollision() throws BuildError {
        JsonTSchema orderV2 = JsonTSchemaBuilder.straight("Order")
                .fieldFrom(JsonTFieldBuilder.scalar("id",  ScalarType.I64))
                .fieldFrom(JsonTFieldBuilder.scalar("sku", ScalarType.STR))
                .build();

        SchemaRegistry r1     = SchemaRegistry.empty().register(orderSchema); // 2 fields
        SchemaRegistry r2     = SchemaRegistry.empty().register(orderV2);     // 2 fields (different)
        SchemaRegistry merged = r1.merge(r2);

        assertEquals(1, merged.size());
        // r2 wins — should have "sku" field
        assertTrue(merged.resolveOrThrow("Order").findField("sku").isPresent());
    }

    // ─── names() & size() ────────────────────────────────────────────────────

    @Test
    void names_containsAllRegisteredNames() {
        SchemaRegistry registry = SchemaRegistry.empty()
                .register(orderSchema)
                .register(userSchema);

        assertTrue(registry.names().contains("Order"));
        assertTrue(registry.names().contains("User"));
        assertEquals(2, registry.names().size());
    }

    @Test
    void names_isImmutable() {
        SchemaRegistry registry = SchemaRegistry.empty().register(orderSchema);
        assertThrows(UnsupportedOperationException.class,
                () -> registry.names().add("Hack"));
    }

    // ─── Null safety ──────────────────────────────────────────────────────────

    @Test
    void register_rejectsNull() {
        assertThrows(IllegalArgumentException.class,
                () -> SchemaRegistry.empty().register(null));
    }

    @Test
    void fromNamespace_rejectsNull() {
        assertThrows(IllegalArgumentException.class,
                () -> SchemaRegistry.fromNamespace(null));
    }

    @Test
    void merge_rejectsNull() {
        assertThrows(IllegalArgumentException.class,
                () -> SchemaRegistry.empty().merge(null));
    }

    // ─── fromNamespace() — structural validation ──────────────────────────────

    @Nested
    class FromNamespaceValidation {

        @Test
        void validSchemas_pass() throws BuildError {
            JsonTNamespace ns = JsonTNamespaceBuilder.create()
                    .catalog(JsonTCatalogBuilder.create()
                            .schema(orderSchema)
                            .schema(userSchema)
                            .build())
                    .build();
            assertDoesNotThrow(() -> SchemaRegistry.fromNamespace(ns));
        }

        @Test
        void objectFieldReferencingUnknownSchema_throwsSchemaInvalid() throws BuildError {
            // "Order" has a field referencing "Address" — but "Address" is not in the namespace
            JsonTSchema withRef = JsonTSchemaBuilder.straight("Order")
                    .fieldFrom(JsonTFieldBuilder.scalar("id", ScalarType.I64))
                    .fieldFrom(JsonTFieldBuilder.object("addr", "Address"))
                    .build();

            JsonTNamespace ns = JsonTNamespaceBuilder.create()
                    .catalog(JsonTCatalogBuilder.create().schema(withRef).build())
                    .build();

            assertThrows(JsonTError.SchemaInvalid.class, () -> SchemaRegistry.fromNamespace(ns));
        }

        @Test
        void derivedSchemaReferencingUnknownParent_throwsSchemaInvalid() throws BuildError {
            JsonTSchema derived = JsonTSchemaBuilder.derived("Summary", "Order")
                    .operation(project(FieldPath.single("id")))
                    .build();

            // namespace contains only "Summary", not "Order"
            JsonTNamespace ns = JsonTNamespaceBuilder.create()
                    .catalog(JsonTCatalogBuilder.create().schema(derived).build())
                    .build();

            assertThrows(JsonTError.SchemaInvalid.class, () -> SchemaRegistry.fromNamespace(ns));
        }

        @Test
        void derivedSchemaWithParentPresent_passes() throws BuildError {
            JsonTSchema derived = JsonTSchemaBuilder.derived("Summary", "Order")
                    .operation(project(FieldPath.single("id")))
                    .build();

            JsonTNamespace ns = JsonTNamespaceBuilder.create()
                    .catalog(JsonTCatalogBuilder.create()
                            .schema(orderSchema)   // "Order" is present
                            .schema(derived)
                            .build())
                    .build();

            assertDoesNotThrow(() -> SchemaRegistry.fromNamespace(ns));
        }

        @Test
        void derivedSchemaOperationReferencingUnknownField_throwsSchemaInvalid() throws BuildError {
            // project(nonExistent) should fail at fromNamespace time
            JsonTSchema derived = JsonTSchemaBuilder.derived("Bad", "Order")
                    .operation(project(FieldPath.single("nonExistent")))
                    .build();

            JsonTNamespace ns = JsonTNamespaceBuilder.create()
                    .catalog(JsonTCatalogBuilder.create()
                            .schema(orderSchema)
                            .schema(derived)
                            .build())
                    .build();

            assertThrows(JsonTError.SchemaInvalid.class, () -> SchemaRegistry.fromNamespace(ns));
        }
    }
}
