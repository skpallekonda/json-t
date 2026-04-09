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
                JsonTNumber,
                JsonTString,
                JsonTValue.Enum, JsonTValue.Array, JsonTValue.Encrypted {

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

    record Array(List<JsonTValue> elements) implements JsonTValue {
        public Array { elements = List.copyOf(elements); }
        @Override public String toString() { return elements.toString(); }
    }

    /**
     * A field value that is encrypted at rest.
     *
     * <p>The {@code envelope} bytes are an opaque binary blob produced by
     * {@code CryptoConfig.encrypt()}. The value flows through
     * parse → validate → transform unchanged and is only decrypted on demand
     * (via the {@code decrypt} pipeline operation or the on-demand API).
     */
    record Encrypted(byte[] envelope) implements JsonTValue {
        public Encrypted {
            Objects.requireNonNull(envelope, "envelope must not be null");
            envelope = envelope.clone(); // defensive copy
        }
        @Override public String toString() { return "<encrypted>"; }
    }

    // ─── Static factories ─────────────────────────────────────────────────────

    static JsonTValue nullValue() { return new Null(); }
    static JsonTValue unspecified() { return new Unspecified(); }
    static JsonTValue enumValue(String v) { return new Enum(v); }
    static JsonTValue bool(boolean v) { return new Bool(v); }
    static JsonTValue i16(short v) { return new JsonTNumber.I16(v); }
    static JsonTValue i32(int v) { return new JsonTNumber.I32(v); }
    static JsonTValue i64(long v) { return new JsonTNumber.I64(v); }
    static JsonTValue u16(int v) { return new JsonTNumber.U16(v); }
    static JsonTValue u32(long v) { return new JsonTNumber.U32(v); }
    static JsonTValue u64(long v) { return new JsonTNumber.U64(v); }
    static JsonTValue d32(float v) { return new JsonTNumber.D32(v); }
    static JsonTValue d64(double v) { return new JsonTNumber.D64(v); }
    static JsonTValue d128(java.math.BigDecimal v) { return new JsonTNumber.D128(v); }
    static JsonTValue text(String v) { return new JsonTString.Plain(v); }
    static JsonTValue array(List<JsonTValue> elements) { return new Array(elements); }
    static JsonTValue encrypted(byte[] envelope) { return new Encrypted(envelope); }

    // ── Semantic string factories ──────────────────────────────────────────────
    static JsonTValue nstr(String v) { return new JsonTString.Nstr(v); }
    static JsonTValue uuid(String v) { return new JsonTString.Uuid(v); }
    static JsonTValue uri(String v) { return new JsonTString.Uri(v); }
    static JsonTValue email(String v) { return new JsonTString.Email(v); }
    static JsonTValue hostname(String v) { return new JsonTString.Hostname(v); }
    static JsonTValue ipv4(String v) { return new JsonTString.Ipv4(v); }
    static JsonTValue ipv6(String v) { return new JsonTString.Ipv6(v); }
    static JsonTValue date(String v) { return new JsonTString.Date(v); }
    static JsonTValue time(String v) { return new JsonTString.Time(v); }
    static JsonTValue dateTime(String v) { return new JsonTString.DateTime(v); }
    static JsonTValue timestamp(String v) { return new JsonTString.Timestamp(v); }
    static JsonTValue tsz(String v) { return new JsonTString.Tsz(v); }
    static JsonTValue inst(String v) { return new JsonTString.Inst(v); }
    static JsonTValue duration(String v) { return new JsonTString.Duration(v); }
    static JsonTValue base64(String v) { return new JsonTString.Base64(v); }
    static JsonTValue hex(String v) { return new JsonTString.Hex(v); }
    static JsonTValue oid(String v) { return new JsonTString.Oid(v); }

    // ── Integer temporal factories ───────────────────────────────────────────
    static JsonTValue dateInt(int v) { return new JsonTNumber.Date(v); }
    static JsonTValue timeInt(int v) { return new JsonTNumber.Time(v); }
    static JsonTValue dateTimeInt(long v) { return new JsonTNumber.DateTime(v); }
    static JsonTValue timestampInt(long v) { return new JsonTNumber.Timestamp(v); }

    // ─── Numeric & Query help ──────────────────────────────────────────────────

    default boolean isNumeric() {
        return this instanceof JsonTNumber;
    }

    default boolean isNull() {
        return this instanceof Null;
    }

    default boolean isUnspecified() {
        return this instanceof Unspecified;
    }

    default double toDouble() {
        if (this instanceof JsonTNumber.I16 v) return v.value();
        if (this instanceof JsonTNumber.I32 v) return v.value();
        if (this instanceof JsonTNumber.I64 v) return (double) v.value();
        if (this instanceof JsonTNumber.U16 v) return v.value();
        if (this instanceof JsonTNumber.U32 v) return (double) v.value();
        if (this instanceof JsonTNumber.U64 v) return (double) v.value();
        if (this instanceof JsonTNumber.D32 v) return v.value();
        if (this instanceof JsonTNumber.D64 v) return v.value();
        if (this instanceof JsonTNumber.D128 v) return v.value().doubleValue();
        if (this instanceof JsonTNumber.Date v) return v.value();
        if (this instanceof JsonTNumber.Time v) return v.value();
        if (this instanceof JsonTNumber.DateTime v) return (double) v.value();
        if (this instanceof JsonTNumber.Timestamp v) return (double) v.value();
        throw new UnsupportedOperationException();
    }

    default Optional<JsonTString> asStr() {
        return this instanceof JsonTString s ? Optional.of(s) : Optional.empty();
    }

    default Optional<String> asRawStr() {
        return asStr().map(JsonTString::value);
    }

    default boolean isStringLike() {
        return this instanceof JsonTString;
    }

    /** Returns {@code true} if this value is an encrypted envelope. */
    default boolean isEncrypted() {
        return this instanceof Encrypted;
    }

    default String asText() {
        return asRawStr().orElse("");
    }

    static JsonTValue promote(JsonTValue value, ScalarType type) {
        if (value instanceof JsonTString.Plain p) {
            return JsonTStrings.promote(p.value(), type)
                    .map(JsonTValue.class::cast)
                    .orElse(value);
        }
        if (value.isNumeric()) {
            return JsonTStrings.promoteTemporal(value, type).orElse(value);
        }
        return value;
    }
}
