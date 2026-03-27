package io.github.datakore.jsont.stringify;

import io.github.datakore.jsont.model.JsonTValue;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static io.github.datakore.jsont.model.JsonTValue.*;
import static org.junit.jupiter.api.Assertions.*;

class JsonTValueStringifyTest {

    @Test void null_value() {
        assertEquals("null", JsonTStringifier.stringify(nullValue()));
    }

    @Test void bool_true() {
        assertEquals("true", JsonTStringifier.stringify(bool(true)));
    }

    @Test void bool_false() {
        assertEquals("false", JsonTStringifier.stringify(bool(false)));
    }

    @Test void i16_value() {
        assertEquals("42", JsonTStringifier.stringify(i16((short) 42)));
    }

    @Test void i32_value() {
        assertEquals("100", JsonTStringifier.stringify(i32(100)));
    }

    @Test void i32_negative() {
        assertEquals("-7", JsonTStringifier.stringify(i32(-7)));
    }

    @Test void i64_value() {
        assertEquals("9999999999", JsonTStringifier.stringify(i64(9_999_999_999L)));
    }

    @Test void u16_value() {
        assertEquals("65535", JsonTStringifier.stringify(u16(65535)));
    }

    @Test void u32_value() {
        assertEquals("4294967295", JsonTStringifier.stringify(u32(4_294_967_295L)));
    }

    @Test void d32_value() {
        assertEquals("3.14", JsonTStringifier.stringify(d32(3.14f)));
    }

    @Test void d64_value() {
        assertEquals("2.718", JsonTStringifier.stringify(d64(2.718)));
    }

    @Test void d128_value() {
        assertEquals("1.23456789", JsonTStringifier.stringify(d128(new BigDecimal("1.23456789"))));
    }

    @Test void text_plain() {
        assertEquals("\"hello\"", JsonTStringifier.stringify(text("hello")));
    }

    @Test void text_escapes_double_quote() {
        assertEquals("\"say \\\"hi\\\"\"", JsonTStringifier.stringify(text("say \"hi\"")));
    }

    @Test void text_escapes_backslash() {
        assertEquals("\"back\\\\slash\"", JsonTStringifier.stringify(text("back\\slash")));
    }

    @Test void text_empty() {
        assertEquals("\"\"", JsonTStringifier.stringify(text("")));
    }

    @Test void array_integers() {
        assertEquals("[1, 2, 3]",
                JsonTStringifier.stringify(array(List.of(i32(1), i32(2), i32(3)))));
    }

    @Test void array_mixed() {
        assertEquals("[1, \"a\", true]",
                JsonTStringifier.stringify(array(List.of(i32(1), text("a"), bool(true)))));
    }

    @Test void array_empty() {
        assertEquals("[]", JsonTStringifier.stringify(array(List.of())));
    }
}
