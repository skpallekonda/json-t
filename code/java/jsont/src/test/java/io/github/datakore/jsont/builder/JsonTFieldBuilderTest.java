package io.github.datakore.jsont.builder;

import io.github.datakore.jsont.error.BuildError;
import io.github.datakore.jsont.model.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JsonTFieldBuilderTest {

    // ─── Factory methods ──────────────────────────────────────────────────────

    @Test
    void scalar_setsKindAndType() throws BuildError {
        JsonTField field = JsonTFieldBuilder.scalar("id", ScalarType.I64).build();
        assertEquals("id", field.name());
        assertEquals(FieldKind.SCALAR, field.kind());
        assertEquals(ScalarType.I64, field.scalarType());
        assertFalse(field.optional());
    }

    @Test
    void object_setsKindAndRef() throws BuildError {
        JsonTField field = JsonTFieldBuilder.object("address", "Address").build();
        assertEquals("address", field.name());
        assertEquals(FieldKind.OBJECT, field.kind());
        assertEquals("Address", field.objectRef());
    }

    @Test
    void asArray_promotesScalarToArrayScalar() throws BuildError {
        JsonTField field = JsonTFieldBuilder.scalar("tags", ScalarType.STR).asArray().build();
        assertEquals(FieldKind.ARRAY_SCALAR, field.kind());
    }

    @Test
    void asArray_promotesObjectToArrayObject() throws BuildError {
        JsonTField field = JsonTFieldBuilder.object("items", "Item").asArray().build();
        assertEquals(FieldKind.ARRAY_OBJECT, field.kind());
    }

    @Test
    void asArray_isIdempotent() throws BuildError {
        JsonTField field = JsonTFieldBuilder.scalar("x", ScalarType.I32).asArray().asArray().build();
        assertEquals(FieldKind.ARRAY_SCALAR, field.kind());
    }

    // ─── Optional modifier ────────────────────────────────────────────────────

    @Test
    void optional_makesFieldOptional() throws BuildError {
        JsonTField field = JsonTFieldBuilder.scalar("note", ScalarType.STR).optional().build();
        assertTrue(field.optional());
    }

    @Test
    void default_fieldIsRequired() throws BuildError {
        JsonTField field = JsonTFieldBuilder.scalar("id", ScalarType.I64).build();
        assertFalse(field.optional());
    }

    // ─── Numeric constraints ──────────────────────────────────────────────────

    @Test
    void minValue_maxValue_storeConstraints() throws BuildError {
        JsonTField field = JsonTFieldBuilder.scalar("price", ScalarType.D64)
                .minValue(0.01)
                .maxValue(9999.99)
                .build();
        assertEquals(0.01,    field.constraints().minValue());
        assertEquals(9999.99, field.constraints().maxValue());
    }

    @Test
    void minValueGreaterThanMaxValue_throwsBuildError() {
        var builder = JsonTFieldBuilder.scalar("qty", ScalarType.I32)
                .minValue(100)
                .maxValue(10);
        assertThrows(BuildError.class, builder::build);
    }

    // ─── String constraints ───────────────────────────────────────────────────

    @Test
    void minLength_maxLength_storeConstraints() throws BuildError {
        JsonTField field = JsonTFieldBuilder.scalar("username", ScalarType.STR)
                .minLength(3)
                .maxLength(20)
                .build();
        assertEquals(3,  field.constraints().minLength());
        assertEquals(20, field.constraints().maxLength());
    }

    @Test
    void minLengthGreaterThanMaxLength_throwsBuildError() {
        var builder = JsonTFieldBuilder.scalar("name", ScalarType.STR)
                .minLength(10)
                .maxLength(5);
        assertThrows(BuildError.class, builder::build);
    }

    @Test
    void pattern_storesRegex() throws BuildError {
        JsonTField field = JsonTFieldBuilder.scalar("email", ScalarType.STR)
                .pattern("^[\\w.]+@[\\w.]+$")
                .build();
        assertEquals("^[\\w.]+@[\\w.]+$", field.constraints().pattern());
    }

    // ─── General constraints ──────────────────────────────────────────────────

    @Test
    void required_setsFlag() throws BuildError {
        JsonTField field = JsonTFieldBuilder.scalar("email", ScalarType.STR).required().build();
        assertTrue(field.constraints().required());
    }

    // ─── Decimal constraints ──────────────────────────────────────────────────

    @Test
    void maxPrecision_storesValue() throws BuildError {
        JsonTField field = JsonTFieldBuilder.scalar("rate", ScalarType.D64).maxPrecision(4).build();
        assertEquals(4, field.constraints().maxPrecision());
    }

    @Test
    void maxPrecision_negative_throwsBuildError() {
        var builder = JsonTFieldBuilder.scalar("rate", ScalarType.D64).maxPrecision(-1);
        assertThrows(BuildError.class, builder::build);
    }

    // ─── Array constraints ────────────────────────────────────────────────────

    @Test
    void minItems_maxItems_storeConstraints() throws BuildError {
        JsonTField field = JsonTFieldBuilder.scalar("codes", ScalarType.STR)
                .asArray()
                .minItems(1)
                .maxItems(10)
                .build();
        assertEquals(1,  field.constraints().minItems());
        assertEquals(10, field.constraints().maxItems());
    }

    @Test
    void minItemsGreaterThanMaxItems_throwsBuildError() {
        var builder = JsonTFieldBuilder.scalar("x", ScalarType.I32)
                .asArray().minItems(5).maxItems(2);
        assertThrows(BuildError.class, builder::build);
    }

    @Test
    void allowNullElements_setsFlag() throws BuildError {
        JsonTField field = JsonTFieldBuilder.scalar("vals", ScalarType.I32)
                .asArray()
                .allowNullElements()
                .build();
        assertTrue(field.constraints().allowNullElements());
    }

    @Test
    void maxNullElements_storesValue() throws BuildError {
        JsonTField field = JsonTFieldBuilder.scalar("vals", ScalarType.I32)
                .asArray()
                .maxNullElements(3)
                .build();
        assertEquals(3, field.constraints().maxNullElements());
    }

    // ─── Constraints NONE sentinel ────────────────────────────────────────────

    @Test
    void noConstraints_returnsNoneEquivalent() throws BuildError {
        JsonTField field = JsonTFieldBuilder.scalar("id", ScalarType.I64).build();
        assertFalse(field.constraints().hasAny());
    }

    // ─── toString ─────────────────────────────────────────────────────────────

    @Test
    void toString_includesNameAndType() throws BuildError {
        JsonTField f1 = JsonTFieldBuilder.scalar("id", ScalarType.I64).build();
        assertEquals("id: i64", f1.toString());

        JsonTField f2 = JsonTFieldBuilder.scalar("note", ScalarType.STR).optional().build();
        assertEquals("note?: str", f2.toString());

        JsonTField f3 = JsonTFieldBuilder.object("addr", "Address").asArray().build();
        assertEquals("addr: <Address>[]", f3.toString());
    }

    // ─── Null safety ──────────────────────────────────────────────────────────

    @Test
    void scalar_rejectsBlankName() {
        assertThrows(IllegalArgumentException.class, () -> JsonTFieldBuilder.scalar("", ScalarType.I32));
        assertThrows(IllegalArgumentException.class, () -> JsonTFieldBuilder.scalar(null, ScalarType.I32));
    }

    @Test
    void scalar_rejectsNullType() {
        assertThrows(IllegalArgumentException.class, () -> JsonTFieldBuilder.scalar("x", null));
    }

    @Test
    void object_rejectsBlankRef() {
        assertThrows(IllegalArgumentException.class, () -> JsonTFieldBuilder.object("a", ""));
        assertThrows(IllegalArgumentException.class, () -> JsonTFieldBuilder.object("a", null));
    }
}
