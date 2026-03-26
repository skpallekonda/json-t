package io.github.datakore.jsont.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JsonTValueTest {

    // ─── Factory methods ──────────────────────────────────────────────────────

    @Test
    void nullValue_isNull() {
        JsonTValue v = JsonTValue.nullValue();
        assertTrue(v.isNull());
        assertInstanceOf(JsonTValue.Null.class, v);
        assertEquals("null", v.toString());
    }

    @Test
    void bool_wrapsValue() {
        assertTrue(((JsonTValue.Bool) JsonTValue.bool(true)).value());
        assertFalse(((JsonTValue.Bool) JsonTValue.bool(false)).value());
        assertEquals("true", JsonTValue.bool(true).toString());
    }

    @Test
    void i32_wrapsValue() {
        JsonTValue v = JsonTValue.i32(42);
        assertInstanceOf(JsonTValue.I32.class, v);
        assertEquals(42, ((JsonTValue.I32) v).value());
        assertEquals("42", v.toString());
    }

    @Test
    void i64_wrapsValue() {
        JsonTValue v = JsonTValue.i64(Long.MAX_VALUE);
        assertEquals(Long.MAX_VALUE, ((JsonTValue.I64) v).value());
    }

    @Test
    void u16_rejectsNegative() {
        assertThrows(IllegalArgumentException.class, () -> JsonTValue.u16(-1));
    }

    @Test
    void u16_rejectsOverflow() {
        assertThrows(IllegalArgumentException.class, () -> JsonTValue.u16(65536));
    }

    @Test
    void u32_rejectsNegative() {
        assertThrows(IllegalArgumentException.class, () -> JsonTValue.u32(-1L));
    }

    @Test
    void d64_wrapsDouble() {
        JsonTValue v = JsonTValue.d64(3.14);
        assertEquals(3.14, ((JsonTValue.D64) v).value());
    }

    @Test
    void d128_requiresNonNull() {
        assertThrows(NullPointerException.class, () -> JsonTValue.d128(null));
    }

    @Test
    void d128_wrapsValue() {
        BigDecimal bd = new BigDecimal("123.456789012345678901234567890");
        JsonTValue v = JsonTValue.d128(bd);
        assertEquals(bd, ((JsonTValue.D128) v).value());
        assertEquals(bd.toPlainString(), v.toString());
    }

    @Test
    void text_wrapsString() {
        JsonTValue v = JsonTValue.text("hello");
        assertEquals("hello", ((JsonTValue.Text) v).value());
        assertEquals("\"hello\"", v.toString());
    }

    @Test
    void text_escapesQuotes() {
        assertEquals("\"say \\\"hi\\\"\"", JsonTValue.text("say \"hi\"").toString());
    }

    @Test
    void text_requiresNonNull() {
        assertThrows(NullPointerException.class, () -> JsonTValue.text(null));
    }

    @Test
    void array_isImmutable() {
        var elems = new java.util.ArrayList<JsonTValue>();
        elems.add(JsonTValue.i32(1));
        JsonTValue arr = JsonTValue.array(elems);
        elems.add(JsonTValue.i32(2)); // mutate original
        assertEquals(1, ((JsonTValue.Array) arr).elements().size()); // still 1
    }

    @Test
    void array_toString() {
        JsonTValue arr = JsonTValue.array(List.of(JsonTValue.i32(1), JsonTValue.text("x")));
        assertEquals("[1, \"x\"]", arr.toString());
    }

    // ─── Numeric coercion ─────────────────────────────────────────────────────

    @Test
    void toDouble_worksForAllNumericTypes() {
        assertEquals(5.0,  JsonTValue.i16((short) 5).toDouble());
        assertEquals(5.0,  JsonTValue.i32(5).toDouble());
        assertEquals(5.0,  JsonTValue.i64(5L).toDouble());
        assertEquals(5.0,  JsonTValue.u16(5).toDouble());
        assertEquals(5.0,  JsonTValue.u32(5L).toDouble());
        assertEquals(5.0,  JsonTValue.u64(5L).toDouble());
        assertEquals(5.0f, JsonTValue.d32(5.0f).toDouble(), 0.0001);
        assertEquals(5.0,  JsonTValue.d64(5.0).toDouble());
        assertEquals(5.0,  JsonTValue.d128(BigDecimal.valueOf(5)).toDouble());
    }

    @Test
    void toDouble_throwsForNonNumeric() {
        assertThrows(UnsupportedOperationException.class, () -> JsonTValue.text("5").toDouble());
        assertThrows(UnsupportedOperationException.class, () -> JsonTValue.bool(true).toDouble());
        assertThrows(UnsupportedOperationException.class, () -> JsonTValue.nullValue().toDouble());
    }

    @Test
    void isNumeric_correctlyClassifies() {
        assertTrue(JsonTValue.i32(1).isNumeric());
        assertTrue(JsonTValue.d64(1.0).isNumeric());
        assertFalse(JsonTValue.text("1").isNumeric());
        assertFalse(JsonTValue.bool(true).isNumeric());
        assertFalse(JsonTValue.nullValue().isNumeric());
    }

    @Test
    void asText_returnsStringContent() {
        assertEquals("hello", JsonTValue.text("hello").asText());
    }

    @Test
    void asText_throwsForNonText() {
        assertThrows(UnsupportedOperationException.class, () -> JsonTValue.i32(1).asText());
    }

    // ─── Sealed instanceof dispatch ───────────────────────────────────────────

    @Test
    void sealedInstanceofDispatch_correctlyClassifies() {
        // Verify every variant is reachable via instanceof (sealed = exhaustive hierarchy)
        JsonTValue v = JsonTValue.i32(7);
        String label;
        if      (v instanceof JsonTValue.Null)  label = "null";
        else if (v instanceof JsonTValue.Bool)  label = "bool";
        else if (v instanceof JsonTValue.I16)   label = "i16";
        else if (v instanceof JsonTValue.I32)   label = "i32";
        else if (v instanceof JsonTValue.I64)   label = "i64";
        else if (v instanceof JsonTValue.U16)   label = "u16";
        else if (v instanceof JsonTValue.U32)   label = "u32";
        else if (v instanceof JsonTValue.U64)   label = "u64";
        else if (v instanceof JsonTValue.D32)   label = "d32";
        else if (v instanceof JsonTValue.D64)   label = "d64";
        else if (v instanceof JsonTValue.D128)  label = "d128";
        else if (v instanceof JsonTValue.Text)  label = "text";
        else if (v instanceof JsonTValue.Array) label = "array";
        else label = "unknown";
        assertEquals("i32", label);
    }

    // ─── Equality ─────────────────────────────────────────────────────────────

    @Test
    void recordEquality() {
        assertEquals(JsonTValue.i32(42), JsonTValue.i32(42));
        assertNotEquals(JsonTValue.i32(42), JsonTValue.i64(42L)); // different types
        assertEquals(JsonTValue.text("a"), JsonTValue.text("a"));
        assertEquals(JsonTValue.nullValue(), JsonTValue.nullValue());
    }
}
