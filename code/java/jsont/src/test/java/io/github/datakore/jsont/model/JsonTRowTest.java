package io.github.datakore.jsont.model;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JsonTRowTest {

    @Test
    void of_createsRowAtIndex0() {
        JsonTRow row = JsonTRow.of(
                JsonTValue.i64(1L),
                JsonTValue.text("Widget"),
                JsonTValue.d64(9.99)
        );
        assertEquals(0L, row.index());
        assertEquals(3, row.size());
    }

    @Test
    void at_setsIndexAndValues() {
        JsonTRow row = JsonTRow.at(5L, JsonTValue.i32(42));
        assertEquals(5L, row.index());
        assertEquals(1, row.size());
        assertEquals(JsonTValue.i32(42), row.get(0));
    }

    @Test
    void get_retrievesValueByPosition() {
        JsonTRow row = JsonTRow.of(
                JsonTValue.i32(1),
                JsonTValue.text("hello"),
                JsonTValue.bool(true)
        );
        assertEquals(JsonTValue.i32(1),       row.get(0));
        assertEquals(JsonTValue.text("hello"), row.get(1));
        assertEquals(JsonTValue.bool(true),   row.get(2));
    }

    @Test
    void get_throwsOnOutOfBounds() {
        JsonTRow row = JsonTRow.of(JsonTValue.i32(1));
        assertThrows(IndexOutOfBoundsException.class, () -> row.get(1));
    }

    @Test
    void isEmpty_trueForEmptyValues() {
        JsonTRow row = JsonTRow.at(0L, List.of());
        assertTrue(row.isEmpty());
    }

    @Test
    void isEmpty_falseWhenHasValues() {
        assertFalse(JsonTRow.of(JsonTValue.nullValue()).isEmpty());
    }

    @Test
    void withIndex_changesIndexPreservesValues() {
        JsonTRow original = JsonTRow.at(0L, JsonTValue.i32(10), JsonTValue.i32(20));
        JsonTRow shifted  = original.withIndex(99L);

        assertEquals(99L, shifted.index());
        assertEquals(original.values(), shifted.values());
    }

    @Test
    void withValues_changesValuesPreservesIndex() {
        JsonTRow original = JsonTRow.at(7L, JsonTValue.i32(1));
        JsonTRow updated  = original.withValues(List.of(JsonTValue.i32(2), JsonTValue.i32(3)));

        assertEquals(7L, updated.index());
        assertEquals(2, updated.size());
        assertEquals(JsonTValue.i32(2), updated.get(0));
    }

    @Test
    void values_areImmutable() {
        var mutable = new ArrayList<JsonTValue>();
        mutable.add(JsonTValue.i32(1));
        JsonTRow row = JsonTRow.at(0L, mutable);
        mutable.add(JsonTValue.i32(2)); // mutate original list
        assertEquals(1, row.size()); // row unaffected
    }

    @Test
    void toString_producesPositionalFormat() {
        JsonTRow row = JsonTRow.of(JsonTValue.i32(1), JsonTValue.text("x"), JsonTValue.bool(false));
        assertEquals("{1, \"x\", false}", row.toString());
    }

    @Test
    void recordEquality() {
        JsonTRow a = JsonTRow.at(1L, JsonTValue.i32(5));
        JsonTRow b = JsonTRow.at(1L, JsonTValue.i32(5));
        JsonTRow c = JsonTRow.at(2L, JsonTValue.i32(5));
        assertEquals(a, b);
        assertNotEquals(a, c); // different index
    }
}
