package io.github.datakore.jsont.model;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Sealed interface representing any value that can appear in a JsonT row.
 */
public sealed interface JsonTValue
        permits JsonTValue.Null, JsonTValue.Unspecified,
                JsonTValue.Bool,
                JsonTValue.I16, JsonTValue.I32, JsonTValue.I64,
                JsonTValue.U16, JsonTValue.U32, JsonTValue.U64,
                JsonTValue.D32, JsonTValue.D64, JsonTValue.D128,
                JsonTValue.Date, JsonTValue.Time, JsonTValue.DateTime, JsonTValue.Timestamp,
                JsonTValue.Str,
                JsonTValue.Enum, JsonTValue.Array {

    // ─── Nested record implementations ────────────────────────────────────────

    record Null() implements JsonTValue {
        @Override public String toString() { return "null"; }
    }

    record Unspecified() implements JsonTValue {
        @Override public String toString() { return "_"; }
    }

    record Enum(String value) implements JsonTValue {
        public Enum { Objects.requireNonNull(value); }
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
        public U16 { if (value < 0 || value > 65535) throw new IllegalArgumentException(); }
        @Override public String toString() { return Integer.toString(value); }
    }

    record U32(long value) implements JsonTValue {
        public U32 { if (value < 0 || value > 4294967295L) throw new IllegalArgumentException(); }
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
        public D128 { Objects.requireNonNull(value); }
        @Override public String toString() { return value.toPlainString(); }
    }

    // ─── Integer temporal variants ───────────────────────────────────────────

    record Date(int value) implements JsonTValue {
        @Override public String toString() { return Integer.toString(value); }
    }

    record Time(int value) implements JsonTValue {
        @Override public String toString() { return Integer.toString(value); }
    }

    record DateTime(long value) implements JsonTValue {
        @Override public String toString() { return Long.toString(value); }
    }

    record Timestamp(long value) implements JsonTValue {
        @Override public String toString() { return Long.toString(value); }
    }

    // ─── String-based values ────────────────────────────────────────────────

    record Str(JsonTString value) implements JsonTValue {
        public Str { Objects.requireNonNull(value); }
        @Override public String toString() {
            String s = value.value();
            return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
        }
    }

    record Array(List<JsonTValue> elements) implements JsonTValue {
        public Array { elements = List.copyOf(elements); }
        @Override public String toString() { return elements.toString(); }
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
    static JsonTValue text(String v) { return new Str(new JsonTString.Plain(v)); }
    static JsonTValue array(List<JsonTValue> elements) { return new Array(elements); }

    // ── Semantic string factories ──────────────────────────────────────────────
    static JsonTValue nstr(String v) { return new Str(new JsonTString.Nstr(v)); }
    static JsonTValue uuid(String v) { return new Str(new JsonTString.Uuid(v)); }
    static JsonTValue uri(String v) { return new Str(new JsonTString.Uri(v)); }
    static JsonTValue email(String v) { return new Str(new JsonTString.Email(v)); }
    static JsonTValue hostname(String v) { return new Str(new JsonTString.Hostname(v)); }
    static JsonTValue ipv4(String v) { return new Str(new JsonTString.Ipv4(v)); }
    static JsonTValue ipv6(String v) { return new Str(new JsonTString.Ipv6(v)); }
    static JsonTValue date(String v) { return new Str(new JsonTString.Date(v)); }
    static JsonTValue time(String v) { return new Str(new JsonTString.Time(v)); }
    static JsonTValue dateTime(String v) { return new Str(new JsonTString.DateTime(v)); }
    static JsonTValue timestamp(String v) { return new Str(new JsonTString.Timestamp(v)); }
    static JsonTValue tsz(String v) { return new Str(new JsonTString.Tsz(v)); }
    static JsonTValue inst(String v) { return new Str(new JsonTString.Inst(v)); }
    static JsonTValue duration(String v) { return new Str(new JsonTString.Duration(v)); }
    static JsonTValue base64(String v) { return new Str(new JsonTString.Base64(v)); }
    static JsonTValue hex(String v) { return new Str(new JsonTString.Hex(v)); }
    static JsonTValue oid(String v) { return new Str(new JsonTString.Oid(v)); }

    // ── Integer temporal factories ───────────────────────────────────────────
    static JsonTValue dateInt(int v) { return new Date(v); }
    static JsonTValue timeInt(int v) { return new Time(v); }
    static JsonTValue dateTimeInt(long v) { return new DateTime(v); }
    static JsonTValue timestampInt(long v) { return new Timestamp(v); }

    // ─── Numeric & Query help ──────────────────────────────────────────────────

    default boolean isNumeric() {
        return this instanceof I16 || this instanceof I32 || this instanceof I64
                || this instanceof U16 || this instanceof U32 || this instanceof U64
                || this instanceof D32 || this instanceof D64 || this instanceof D128
                || this instanceof Date || this instanceof Time || this instanceof DateTime || this instanceof Timestamp;
    }

    default boolean isNull() {
        return this instanceof Null;
    }

    default boolean isUnspecified() {
        return this instanceof Unspecified;
    }

    default double toDouble() {
        if (this instanceof I16 v) return v.value();
        if (this instanceof I32 v) return v.value();
        if (this instanceof I64 v) return (double) v.value();
        if (this instanceof U16 v) return v.value();
        if (this instanceof U32 v) return (double) v.value();
        if (this instanceof U64 v) return (double) v.value();
        if (this instanceof D32 v) return v.value();
        if (this instanceof D64 v) return v.value();
        if (this instanceof D128 v) return v.value().doubleValue();
        if (this instanceof Date v) return v.value();
        if (this instanceof Time v) return v.value();
        if (this instanceof DateTime v) return (double) v.value();
        if (this instanceof Timestamp v) return (double) v.value();
        throw new UnsupportedOperationException();
    }

    default Optional<JsonTString> asStr() {
        return this instanceof Str s ? Optional.of(s.value()) : Optional.empty();
    }

    default Optional<String> asRawStr() {
        return asStr().map(JsonTString::value);
    }

    default boolean isStringLike() {
        return this instanceof Str;
    }

    default String asText() {
        return asRawStr().orElse("");
    }

    static JsonTValue promote(JsonTValue value, ScalarType type) {
        if (value instanceof Str s && s.value() instanceof JsonTString.Plain p) {
            return JsonTStrings.promote(p.value(), type)
                    .map(JsonTValue.Str::new)
                    .map(JsonTValue.class::cast)
                    .orElse(value);
        }
        if (value.isNumeric()) {
            return JsonTStrings.promoteTemporal(value, type).orElse(value);
        }
        return value;
    }
}
