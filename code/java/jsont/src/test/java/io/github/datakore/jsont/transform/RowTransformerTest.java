package io.github.datakore.jsont.transform;

import io.github.datakore.jsont.builder.JsonTFieldBuilder;
import io.github.datakore.jsont.builder.JsonTSchemaBuilder;
import io.github.datakore.jsont.builder.SchemaRegistry;
import io.github.datakore.jsont.error.JsonTError;
import io.github.datakore.jsont.model.BinaryOp;
import io.github.datakore.jsont.model.FieldPath;
import io.github.datakore.jsont.model.JsonTExpression;
import io.github.datakore.jsont.model.JsonTRow;
import io.github.datakore.jsont.model.JsonTSchema;
import io.github.datakore.jsont.model.JsonTValue;
import io.github.datakore.jsont.model.RenamePair;
import io.github.datakore.jsont.model.ScalarType;
import io.github.datakore.jsont.model.SchemaOperation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RowTransformerTest {

    private static JsonTSchema straightSchema() throws Exception {
        return JsonTSchemaBuilder.straight("Order")
                .fieldFrom(JsonTFieldBuilder.scalar("id", ScalarType.I64))
                .fieldFrom(JsonTFieldBuilder.scalar("product", ScalarType.STR))
                .fieldFrom(JsonTFieldBuilder.scalar("qty", ScalarType.I32))
                .build();
    }

    // ─── Straight schema ───────────────────────────────────────────────────────

    @Test
    void straight_schema_returns_row_unchanged() throws Exception {
        JsonTSchema schema = straightSchema();
        SchemaRegistry registry = SchemaRegistry.empty().register(schema);
        RowTransformer t = RowTransformer.of(schema, registry);
        JsonTRow row = JsonTRow.of(JsonTValue.i64(1), JsonTValue.text("Widget"), JsonTValue.i32(10));
        JsonTRow result = t.transform(row);
        assertSame(row, result);
    }

    // ─── Project operation ─────────────────────────────────────────────────────

    @Test
    void derived_project_keepsOnlySpecifiedFields() throws Exception {
        JsonTSchema parent = straightSchema();
        JsonTSchema derived = JsonTSchemaBuilder.derived("OrderSummary", "Order")
                .operation(SchemaOperation.project(
                        FieldPath.single("id"),
                        FieldPath.single("product")))
                .build();
        SchemaRegistry registry = SchemaRegistry.empty()
                .register(parent)
                .register(derived);
        RowTransformer t = RowTransformer.of(derived, registry);
        JsonTRow row = JsonTRow.of(JsonTValue.i64(1), JsonTValue.text("Widget"), JsonTValue.i32(10));
        JsonTRow result = t.transform(row);
        assertEquals(2, result.values().size());
        assertEquals(JsonTValue.i64(1), result.get(0));
        assertEquals(JsonTValue.text("Widget"), result.get(1));
    }

    // ─── Exclude operation ─────────────────────────────────────────────────────

    @Test
    void derived_exclude_removesFields() throws Exception {
        JsonTSchema parent = straightSchema();
        JsonTSchema derived = JsonTSchemaBuilder.derived("OrderNoQty", "Order")
                .operation(SchemaOperation.exclude(FieldPath.single("qty")))
                .build();
        SchemaRegistry registry = SchemaRegistry.empty()
                .register(parent)
                .register(derived);
        RowTransformer t = RowTransformer.of(derived, registry);
        JsonTRow row = JsonTRow.of(JsonTValue.i64(1), JsonTValue.text("Widget"), JsonTValue.i32(10));
        JsonTRow result = t.transform(row);
        assertEquals(2, result.values().size());
        assertEquals(JsonTValue.i64(1), result.get(0));
        assertEquals(JsonTValue.text("Widget"), result.get(1));
    }

    // ─── Rename operation ──────────────────────────────────────────────────────

    @Test
    void derived_rename_renamesField() throws Exception {
        JsonTSchema parent = straightSchema();
        JsonTSchema derived = JsonTSchemaBuilder.derived("OrderRenamed", "Order")
                .operation(SchemaOperation.rename(RenamePair.of("product", "productName")))
                .build();
        SchemaRegistry registry = SchemaRegistry.empty()
                .register(parent)
                .register(derived);
        RowTransformer t = RowTransformer.of(derived, registry);
        JsonTRow row = JsonTRow.of(JsonTValue.i64(1), JsonTValue.text("Widget"), JsonTValue.i32(10));
        JsonTRow result = t.transform(row);
        assertEquals(3, result.values().size());
        // values unchanged, field names changed
        assertEquals(JsonTValue.text("Widget"), result.get(1));
    }

    // ─── Filter operation ──────────────────────────────────────────────────────

    @Test
    void derived_filter_true_passesRow() throws Exception {
        JsonTSchema parent = straightSchema();
        JsonTSchema derived = JsonTSchemaBuilder.derived("OrderFiltered", "Order")
                .operation(SchemaOperation.filter(
                        JsonTExpression.binary(BinaryOp.GT,
                                JsonTExpression.fieldName("qty"),
                                JsonTExpression.literal(JsonTValue.i32(0)))))
                .build();
        SchemaRegistry registry = SchemaRegistry.empty()
                .register(parent)
                .register(derived);
        RowTransformer t = RowTransformer.of(derived, registry);
        JsonTRow row = JsonTRow.of(JsonTValue.i64(1), JsonTValue.text("Widget"), JsonTValue.i32(5));
        JsonTRow result = t.transform(row);
        assertEquals(3, result.values().size());
    }

    @Test
    void derived_filter_false_throwsFiltered() throws Exception {
        JsonTSchema parent = straightSchema();
        JsonTSchema derived = JsonTSchemaBuilder.derived("OrderFiltered2", "Order")
                .operation(SchemaOperation.filter(
                        JsonTExpression.binary(BinaryOp.GT,
                                JsonTExpression.fieldName("qty"),
                                JsonTExpression.literal(JsonTValue.i32(100)))))
                .build();
        SchemaRegistry registry = SchemaRegistry.empty()
                .register(parent)
                .register(derived);
        RowTransformer t = RowTransformer.of(derived, registry);
        JsonTRow row = JsonTRow.of(JsonTValue.i64(1), JsonTValue.text("Widget"), JsonTValue.i32(5));
        assertThrows(JsonTError.Transform.Filtered.class, () -> t.transform(row));
    }

    // ─── Transform operation ───────────────────────────────────────────────────

    @Test
    void derived_transform_replacesValue() throws Exception {
        JsonTSchema parent = straightSchema();
        // Double the qty
        JsonTSchema derived = JsonTSchemaBuilder.derived("OrderDoubled", "Order")
                .operation(SchemaOperation.transform("qty",
                        JsonTExpression.binary(BinaryOp.MUL,
                                JsonTExpression.fieldName("qty"),
                                JsonTExpression.literal(JsonTValue.i32(2)))))
                .build();
        SchemaRegistry registry = SchemaRegistry.empty()
                .register(parent)
                .register(derived);
        RowTransformer t = RowTransformer.of(derived, registry);
        JsonTRow row = JsonTRow.of(JsonTValue.i64(1), JsonTValue.text("Widget"), JsonTValue.i32(5));
        JsonTRow result = t.transform(row);
        // qty should now be 10.0 (as D64)
        assertTrue(result.get(2).isNumeric());
        assertEquals(10.0, result.get(2).toDouble());
    }

    // ─── Multiple operations ───────────────────────────────────────────────────

    @Test
    void derived_multipleOperations_inSequence() throws Exception {
        JsonTSchema parent = straightSchema();
        JsonTSchema derived = JsonTSchemaBuilder.derived("OrderMulti", "Order")
                .operation(SchemaOperation.exclude(FieldPath.single("qty")))
                .operation(SchemaOperation.rename(RenamePair.of("product", "item")))
                .build();
        SchemaRegistry registry = SchemaRegistry.empty()
                .register(parent)
                .register(derived);
        RowTransformer t = RowTransformer.of(derived, registry);
        JsonTRow row = JsonTRow.of(JsonTValue.i64(1), JsonTValue.text("Widget"), JsonTValue.i32(10));
        JsonTRow result = t.transform(row);
        assertEquals(2, result.values().size());
        assertEquals(JsonTValue.i64(1), result.get(0));
        assertEquals(JsonTValue.text("Widget"), result.get(1));
    }

    // ─── Error cases ───────────────────────────────────────────────────────────

    @Test
    void unknownParentSchema_throwsUnknownSchema() throws Exception {
        JsonTSchema derived = JsonTSchemaBuilder.derived("OrderSummary", "NonExistent")
                .operation(SchemaOperation.project(FieldPath.single("id")))
                .build();
        SchemaRegistry registry = SchemaRegistry.empty().register(derived);
        RowTransformer t = RowTransformer.of(derived, registry);
        JsonTRow row = JsonTRow.of(JsonTValue.i64(1));
        assertThrows(JsonTError.Transform.UnknownSchema.class, () -> t.transform(row));
    }

    @Test
    void fieldNotFound_throwsFieldNotFound() throws Exception {
        JsonTSchema parent = straightSchema();
        JsonTSchema derived = JsonTSchemaBuilder.derived("OrderSummary", "Order")
                .operation(SchemaOperation.exclude(FieldPath.single("nonExistentField")))
                .build();
        SchemaRegistry registry = SchemaRegistry.empty()
                .register(parent)
                .register(derived);
        RowTransformer t = RowTransformer.of(derived, registry);
        JsonTRow row = JsonTRow.of(JsonTValue.i64(1), JsonTValue.text("Widget"), JsonTValue.i32(10));
        assertThrows(JsonTError.Transform.FieldNotFound.class, () -> t.transform(row));
    }

    // ─── validateSchema ────────────────────────────────────────────────────────

    @Test
    void validateSchema_validStraight_passes() throws Exception {
        JsonTSchema schema = straightSchema();
        SchemaRegistry registry = SchemaRegistry.empty().register(schema);
        RowTransformer t = RowTransformer.of(schema, registry);
        assertDoesNotThrow(t::validateSchema);
    }

    @Test
    void validateSchema_validDerived_passes() throws Exception {
        JsonTSchema parent = straightSchema();
        JsonTSchema derived = JsonTSchemaBuilder.derived("OrderSummary", "Order")
                .operation(SchemaOperation.project(FieldPath.single("id"), FieldPath.single("product")))
                .build();
        SchemaRegistry registry = SchemaRegistry.empty()
                .register(parent)
                .register(derived);
        RowTransformer t = RowTransformer.of(derived, registry);
        assertDoesNotThrow(t::validateSchema);
    }

    @Test
    void validateSchema_unknownParent_throwsSchemaInvalid() throws Exception {
        JsonTSchema derived = JsonTSchemaBuilder.derived("OrderSummary", "NonExistent")
                .operation(SchemaOperation.project(FieldPath.single("id")))
                .build();
        SchemaRegistry registry = SchemaRegistry.empty().register(derived);
        RowTransformer t = RowTransformer.of(derived, registry);
        assertThrows(JsonTError.SchemaInvalid.class, t::validateSchema);
    }

    @Test
    void validateSchema_cyclicDerivation_throwsSchemaInvalid() throws Exception {
        // A derived from B, B derived from A
        JsonTSchema schemaA = JsonTSchemaBuilder.derived("A", "B")
                .operation(SchemaOperation.project(FieldPath.single("id")))
                .build();
        JsonTSchema schemaB = JsonTSchemaBuilder.derived("B", "A")
                .operation(SchemaOperation.project(FieldPath.single("id")))
                .build();
        SchemaRegistry registry = SchemaRegistry.empty()
                .register(schemaA)
                .register(schemaB);
        RowTransformer t = RowTransformer.of(schemaA, registry);
        assertThrows(JsonTError.SchemaInvalid.class, t::validateSchema);
    }
}
