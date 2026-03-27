package io.github.datakore.jsont.builder;

import io.github.datakore.jsont.error.BuildError;
import io.github.datakore.jsont.model.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static io.github.datakore.jsont.model.BinaryOp.GT;
import static io.github.datakore.jsont.model.JsonTExpression.*;
import static io.github.datakore.jsont.model.JsonTValue.*;
import static org.junit.jupiter.api.Assertions.*;

class JsonTSchemaBuilderTest {

    // ─── Straight schema ──────────────────────────────────────────────────────

    @Test
    void straight_buildsValidSchema() throws BuildError {
        JsonTSchema schema = JsonTSchemaBuilder.straight("Order")
                .fieldFrom(JsonTFieldBuilder.scalar("id",      ScalarType.I64))
                .fieldFrom(JsonTFieldBuilder.scalar("product", ScalarType.STR).minLength(2))
                .fieldFrom(JsonTFieldBuilder.scalar("qty",     ScalarType.I32).minValue(1).maxValue(999))
                .fieldFrom(JsonTFieldBuilder.scalar("price",   ScalarType.D64).minValue(0.01))
                .build();

        assertEquals("Order",          schema.name());
        assertEquals(SchemaKind.STRAIGHT, schema.kind());
        assertTrue(schema.isStraight());
        assertFalse(schema.isDerived());
        assertEquals(4,                schema.fieldCount());
        assertTrue(schema.derivedFrom().isEmpty());
        assertTrue(schema.operations().isEmpty());
        assertTrue(schema.validation().isEmpty());
    }

    @Test
    void straight_withValidationBlock() throws BuildError {
        JsonTSchema schema = JsonTSchemaBuilder.straight("User")
                .fieldFrom(JsonTFieldBuilder.scalar("id",    ScalarType.I64))
                .fieldFrom(JsonTFieldBuilder.scalar("email", ScalarType.STR))
                .validationFrom(
                        JsonTValidationBlockBuilder.create()
                                .unique("id")
                                .unique("email")
                                .rule(binary(GT, fieldName("id"), literal(i64(0L)))))
                .build();

        assertTrue(schema.validation().isPresent());
        JsonTValidationBlock vb = schema.validation().get();
        assertEquals(2, vb.uniqueKeys().size());
        assertEquals(1, vb.rules().size());
    }

    @Test
    void straight_fieldsAreAccessibleByName() throws BuildError {
        JsonTSchema schema = JsonTSchemaBuilder.straight("Product")
                .fieldFrom(JsonTFieldBuilder.scalar("sku",   ScalarType.STR))
                .fieldFrom(JsonTFieldBuilder.scalar("price", ScalarType.D64))
                .build();

        assertTrue(schema.findField("sku").isPresent());
        assertTrue(schema.findField("price").isPresent());
        assertTrue(schema.findField("nonExistent").isEmpty());
    }

    @Test
    void straight_noFields_throwsBuildError() {
        assertThrows(BuildError.class,
                () -> JsonTSchemaBuilder.straight("Empty").build());
    }

    @Test
    void straight_duplicateFieldNames_throwsBuildError() {
        assertThrows(BuildError.class, () ->
                JsonTSchemaBuilder.straight("Bad")
                        .fieldFrom(JsonTFieldBuilder.scalar("id", ScalarType.I32))
                        .fieldFrom(JsonTFieldBuilder.scalar("id", ScalarType.I32))
                        .build());
    }

    @Test
    void straight_nameNotStartingWithUppercase_throwsBuildError() {
        assertThrows(BuildError.class,
                () -> JsonTSchemaBuilder.straight("order")
                        .fieldFrom(JsonTFieldBuilder.scalar("id", ScalarType.I32))
                        .build());
    }

    @Test
    void straight_cannotAddOperations() throws BuildError {
        var builder = JsonTSchemaBuilder.straight("X")
                .fieldFrom(JsonTFieldBuilder.scalar("id", ScalarType.I32));
        assertThrows(BuildError.class,
                () -> builder.operation(SchemaOperation.exclude(FieldPath.single("id"))));
    }

    @Test
    void straight_cannotAddValidationToDeduced() {
        var builder = JsonTSchemaBuilder.derived("X", "Y");
        assertThrows(BuildError.class,
                () -> builder.validationFrom(JsonTValidationBlockBuilder.create().unique("id")));
    }

    // ─── Derived schema ───────────────────────────────────────────────────────

    @Test
    void derived_buildsValidSchema() throws BuildError {
        JsonTSchema schema = JsonTSchemaBuilder.derived("OrderSummary", "Order")
                .operation(SchemaOperation.project(
                        FieldPath.single("id"),
                        FieldPath.single("product")))
                .build();

        assertEquals("OrderSummary",    schema.name());
        assertEquals(SchemaKind.DERIVED, schema.kind());
        assertTrue(schema.isDerived());
        assertFalse(schema.isStraight());
        assertEquals("Order",           schema.derivedFrom().orElse(null));
        assertEquals(1,                 schema.operations().size());
        assertTrue(schema.fields().isEmpty());
    }

    @Test
    void derived_multipleOperations() throws BuildError {
        JsonTSchema schema = JsonTSchemaBuilder.derived("View", "Order")
                .operation(SchemaOperation.exclude(FieldPath.single("internalCode")))
                .operation(SchemaOperation.rename(RenamePair.of("price", "amount")))
                .operation(SchemaOperation.filter(
                        binary(GT, fieldName("amount"), literal(d64(0.0)))))
                .build();

        assertEquals(3, schema.operations().size());
        assertInstanceOf(SchemaOperation.Exclude.class,   schema.operations().get(0));
        assertInstanceOf(SchemaOperation.Rename.class,    schema.operations().get(1));
        assertInstanceOf(SchemaOperation.Filter.class,    schema.operations().get(2));
    }

    @Test
    void derived_blankParentName_throwsAtFactory() {
        // blank parent name is caught by the factory, not build(), so IllegalArgumentException
        assertThrows(IllegalArgumentException.class,
                () -> JsonTSchemaBuilder.derived("X", ""));
    }

    @Test
    void derived_selfReference_throwsBuildError() {
        assertThrows(BuildError.class,
                () -> JsonTSchemaBuilder.derived("Order", "Order").build());
    }

    @Test
    void derived_cannotAddFields() {
        var builder = JsonTSchemaBuilder.derived("X", "Y");
        assertThrows(BuildError.class,
                () -> builder.fieldFrom(JsonTFieldBuilder.scalar("id", ScalarType.I32)));
    }

    @Test
    void derived_noOperations_isAllowed() throws BuildError {
        // A derived schema with no operations is a full projection of the parent
        JsonTSchema schema = JsonTSchemaBuilder.derived("Copy", "Original").build();
        assertTrue(schema.operations().isEmpty());
    }

    // ─── Factory null-safety ──────────────────────────────────────────────────

    @Test
    void straight_rejectsBlankName() {
        assertThrows(IllegalArgumentException.class, () -> JsonTSchemaBuilder.straight(""));
        assertThrows(IllegalArgumentException.class, () -> JsonTSchemaBuilder.straight(null));
    }

    @Test
    void derived_rejectsBlankParentName() {
        assertThrows(IllegalArgumentException.class, () -> JsonTSchemaBuilder.derived("X", null));
    }
}
