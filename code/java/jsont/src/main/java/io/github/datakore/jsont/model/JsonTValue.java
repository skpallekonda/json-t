package io.github.datakore.jsont.model;

import java.util.List;
import java.util.Objects;

/**
 * Sealed interface representing any value that can appear in a JsonT row.
 * Implementations are nested records matching Rust's enum variants.
 */
public sealed interface JsonTValue
        permits JsonTValue.Null, JsonTValue.Unspecified,
                JsonTValue.Bool,
                JsonTValue.I16, JsonTValue.I32, JsonTValue.I64,
                JsonTValue.U16, JsonTValue.U32, JsonTValue.U64,
                JsonTValue.D32, JsonTValue.D64, JsonTValue.D128,
                JsonTValue.Text, JsonTValue.Enum, JsonTValue.Array {

    // ─── Nested record implementations ────────────────────────────────────────

    record Null() implements JsonTValue {
        @Override public String toString() { return "null"; }
    }

    /** CDC sentinel — "this field was not changed". Serialises as {@code _}. */
    record Unspecified() implements JsonTValue {
        @Override public String toString() { return "_"; }
    }

    /** A named enum constant (e.g. {@code ACTIVE}). Serialises as the bare identifier. */
    record Enum(String value) implements JsonTValue {
        public Enum {
            Objects.requireNonNull(value, "Enum value must not be null");
        }
        @Override public String toString() { return value; }
    }

    record Bool(boolean value) implements JsonTValue {
        @Override public String toString() { return Boolean.toString(value); }
    }

    record I16(short value) implements JsonTValue {
        @Override public String toString() { return Short.toString(value); }
    }

    record I32(int value) implements JsonTValue {
        @Override public String toString() { return Integer.toString(value); }
    }

    record I64(long value) implements JsonTValue {
        @Override public String toString() { return Long.toString(value); }
    }

    record U16(int value) implements JsonTValue {
        public U16 {
            if (value < 0 || value > 65535) throw new IllegalArgumentException("u16 out of range: " + value);
        }
        @Override public String toString() { return Integer.toString(value); }
    }

    record U32(long value) implements JsonTValue {
        public U32 {
            if (value < 0 || value > 4294967295L) throw new IllegalArgumentException("u32 out of range: " + value);
        }
        @Override public String toString() { return Long.toString(value); }
    }

    record U64(long value) implements JsonTValue {
        @Override public String toString() { return Long.toUnsignedString(value); }
    }

    record D32(float value) implements JsonTValue {
        @Override public String toString() { return Float.toString(value); }
    }

    record D64(double value) implements JsonTValue {
        @Override public String toString() { return Double.toString(value); }
    }

    record D128(java.math.BigDecimal value) implements JsonTValue {
        public D128 {
            Objects.requireNonNull(value, "D128 value must not be null");
        }
        @Override public String toString() { return value.toPlainString(); }
    }

    record Text(String value) implements JsonTValue {
        public Text {
            Objects.requireNonNull(value, "Text value must not be null");
        }
        @Override public String toString() { return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""; }
    }

    record Array(List<JsonTValue> elements) implements JsonTValue {
        public Array {
            Objects.requireNonNull(elements, "Array elements must not be null");
            elements = List.copyOf(elements);
        }
        @Override public String toString() {
            var sb = new StringBuilder("[");
            for (int i = 0; i < elements.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(elements.get(i));
            }
            sb.append("]");
            return sb.toString();
        }
    }

    // ─── Static factories ─────────────────────────────────────────────────────

    static JsonTValue nullValue() { return new Null(); }
    static JsonTValue unspecified() { return new Unspecified(); }
    static JsonTValue enumValue(String v) { return new Enum(v); }
    static JsonTValue bool(boolean v) { return new Bool(v); }
    static JsonTValue i16(short v) { return new I16(v); }
    static JsonTValue i32(int v) { return new I32(v); }
    static JsonTValue i64(long v) { return new I64(v); }
    static JsonTValue u16(int v) { return new U16(v); }
    static JsonTValue u32(long v) { return new U32(v); }
    static JsonTValue u64(long v) { return new U64(v); }
    static JsonTValue d32(float v) { return new D32(v); }
    static JsonTValue d64(double v) { return new D64(v); }
    static JsonTValue d128(java.math.BigDecimal v) { return new D128(v); }
    static JsonTValue text(String v) { return new Text(v); }
    static JsonTValue array(List<JsonTValue> elements) { return new Array(elements); }

    // ─── Numeric coercion ─────────────────────────────────────────────────────

    /** Returns true if this value can be converted to a double. */
    default boolean isNumeric() {
        return this instanceof I16  || this instanceof I32  || this instanceof I64
            || this instanceof U16  || this instanceof U32  || this instanceof U64
            || this instanceof D32  || this instanceof D64  || this instanceof D128;
    }

    /**
     * Coerces this value to a double for numeric comparisons.
     *
     * @throws UnsupportedOperationException if this value is not numeric
     */
    default double toDouble() {
        if (this instanceof I16  v) return v.value();
        if (this instanceof I32  v) return v.value();
        if (this instanceof I64  v) return (double) v.value();
        if (this instanceof U16  v) return v.value();
        if (this instanceof U32  v) return (double) v.value();
        if (this instanceof U64  v) return (double) v.value();
        if (this instanceof D32  v) return v.value();
        if (this instanceof D64  v) return v.value();
        if (this instanceof D128 v) return v.value().doubleValue();
        throw new UnsupportedOperationException(
                "Cannot convert " + getClass().getSimpleName() + " to double");
    }

    /** Returns true if this value is logically null. */
    default boolean isNull() {
        return this instanceof Null;
    }

    /** Returns the string content if this is a Text value, otherwise throws. */
    default String asText() {
        if (this instanceof Text t) return t.value();
        throw new UnsupportedOperationException("Not a Text value: " + this);
    }
}
