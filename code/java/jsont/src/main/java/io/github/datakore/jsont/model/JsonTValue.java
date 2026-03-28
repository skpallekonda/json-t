package io.github.datakore.jsont.model;

import java.util.List;
import java.util.Objects;

/**
 * Sealed interface representing any value that can appear in a JsonT row.
 * Implementations are nested records matching Rust's enum variants.
 *
 * <p>Semantic string variants ({@link Email}, {@link Uuid}, etc.) serialise identically
 * to {@link Text} on the wire. They are promoted from {@link Text} in the validation
 * layer once the declared {@link ScalarType} is known.
 */
public sealed interface JsonTValue
        permits JsonTValue.Null, JsonTValue.Unspecified,
                JsonTValue.Bool,
                JsonTValue.I16, JsonTValue.I32, JsonTValue.I64,
                JsonTValue.U16, JsonTValue.U32, JsonTValue.U64,
                JsonTValue.D32, JsonTValue.D64, JsonTValue.D128,
                JsonTValue.Text,
                JsonTValue.Nstr, JsonTValue.Uuid, JsonTValue.Uri,
                JsonTValue.Email, JsonTValue.Hostname,
                JsonTValue.Ipv4, JsonTValue.Ipv6,
                JsonTValue.Date, JsonTValue.Time, JsonTValue.DateTime,
                JsonTValue.Timestamp, JsonTValue.Tsz, JsonTValue.Inst,
                JsonTValue.Duration,
                JsonTValue.Base64, JsonTValue.Hex, JsonTValue.Oid,
                JsonTValue.Enum, JsonTValue.Array {

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
        public Text { Objects.requireNonNull(value, "Text value must not be null"); }
        @Override public String toString() { return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""; }
    }

    // ── Semantic string variants ───────────────────────────────────────────────
    // All serialise as double-quoted strings, identical to Text on the wire.
    // Promoted from Text in the validation layer when ScalarType is known.

    /** Normalised (unicode NFC) string — maps to {@link ScalarType#NSTR}. */
    record Nstr(String value) implements JsonTValue {
        public Nstr { Objects.requireNonNull(value, "Nstr value must not be null"); }
        @Override public String toString() { return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""; }
    }
    /** UUID — maps to {@link ScalarType#UUID}. */
    record Uuid(String value) implements JsonTValue {
        public Uuid { Objects.requireNonNull(value, "Uuid value must not be null"); }
        @Override public String toString() { return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""; }
    }
    /** URI — maps to {@link ScalarType#URI}. */
    record Uri(String value) implements JsonTValue {
        public Uri { Objects.requireNonNull(value, "Uri value must not be null"); }
        @Override public String toString() { return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""; }
    }
    /** Email address — maps to {@link ScalarType#EMAIL}. */
    record Email(String value) implements JsonTValue {
        public Email { Objects.requireNonNull(value, "Email value must not be null"); }
        @Override public String toString() { return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""; }
    }
    /** Hostname — maps to {@link ScalarType#HOSTNAME}. */
    record Hostname(String value) implements JsonTValue {
        public Hostname { Objects.requireNonNull(value, "Hostname value must not be null"); }
        @Override public String toString() { return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""; }
    }
    /** IPv4 address — maps to {@link ScalarType#IPV4}. */
    record Ipv4(String value) implements JsonTValue {
        public Ipv4 { Objects.requireNonNull(value, "Ipv4 value must not be null"); }
        @Override public String toString() { return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""; }
    }
    /** IPv6 address — maps to {@link ScalarType#IPV6}. */
    record Ipv6(String value) implements JsonTValue {
        public Ipv6 { Objects.requireNonNull(value, "Ipv6 value must not be null"); }
        @Override public String toString() { return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""; }
    }
    /** Calendar date — maps to {@link ScalarType#DATE}. */
    record Date(String value) implements JsonTValue {
        public Date { Objects.requireNonNull(value, "Date value must not be null"); }
        @Override public String toString() { return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""; }
    }
    /** Time-of-day — maps to {@link ScalarType#TIME}. */
    record Time(String value) implements JsonTValue {
        public Time { Objects.requireNonNull(value, "Time value must not be null"); }
        @Override public String toString() { return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""; }
    }
    /** Date and time without timezone — maps to {@link ScalarType#DATETIME}. */
    record DateTime(String value) implements JsonTValue {
        public DateTime { Objects.requireNonNull(value, "DateTime value must not be null"); }
        @Override public String toString() { return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""; }
    }
    /** Unix timestamp — maps to {@link ScalarType#TIMESTAMP}. */
    record Timestamp(String value) implements JsonTValue {
        public Timestamp { Objects.requireNonNull(value, "Timestamp value must not be null"); }
        @Override public String toString() { return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""; }
    }
    /** Timestamp with timezone — maps to {@link ScalarType#TSZ}. */
    record Tsz(String value) implements JsonTValue {
        public Tsz { Objects.requireNonNull(value, "Tsz value must not be null"); }
        @Override public String toString() { return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""; }
    }
    /** Monotonic instant — maps to {@link ScalarType#INST}. */
    record Inst(String value) implements JsonTValue {
        public Inst { Objects.requireNonNull(value, "Inst value must not be null"); }
        @Override public String toString() { return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""; }
    }
    /** ISO 8601 duration — maps to {@link ScalarType#DURATION}. */
    record Duration(String value) implements JsonTValue {
        public Duration { Objects.requireNonNull(value, "Duration value must not be null"); }
        @Override public String toString() { return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""; }
    }
    /** Base64-encoded binary — maps to {@link ScalarType#BASE64}. */
    record Base64(String value) implements JsonTValue {
        public Base64 { Objects.requireNonNull(value, "Base64 value must not be null"); }
        @Override public String toString() { return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""; }
    }
    /** Hex-encoded binary — maps to {@link ScalarType#HEX}. */
    record Hex(String value) implements JsonTValue {
        public Hex { Objects.requireNonNull(value, "Hex value must not be null"); }
        @Override public String toString() { return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""; }
    }
    /** Object identifier — maps to {@link ScalarType#OID}. */
    record Oid(String value) implements JsonTValue {
        public Oid { Objects.requireNonNull(value, "Oid value must not be null"); }
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

    // ── Semantic string factories ──────────────────────────────────────────────
    static JsonTValue nstr(String v) { return new Nstr(v); }
    static JsonTValue uuid(String v) { return new Uuid(v); }
    static JsonTValue uri(String v) { return new Uri(v); }
    static JsonTValue email(String v) { return new Email(v); }
    static JsonTValue hostname(String v) { return new Hostname(v); }
    static JsonTValue ipv4(String v) { return new Ipv4(v); }
    static JsonTValue ipv6(String v) { return new Ipv6(v); }
    static JsonTValue date(String v) { return new Date(v); }
    static JsonTValue time(String v) { return new Time(v); }
    static JsonTValue dateTime(String v) { return new DateTime(v); }
    static JsonTValue timestamp(String v) { return new Timestamp(v); }
    static JsonTValue tsz(String v) { return new Tsz(v); }
    static JsonTValue inst(String v) { return new Inst(v); }
    static JsonTValue duration(String v) { return new Duration(v); }
    static JsonTValue base64(String v) { return new Base64(v); }
    static JsonTValue hex(String v) { return new Hex(v); }
    static JsonTValue oid(String v) { return new Oid(v); }

    /**
     * Promotes a {@link Text} value to its semantic variant for the given {@link ScalarType}.
     *
     * Returns {@code value} unchanged if it is not {@link Text}, or if the type maps to plain
     * {@link Text}/{@link ScalarType#STR}. Called in the validation layer after all checks pass.
     */
    static JsonTValue promote(JsonTValue value, ScalarType type) {
        if (!(value instanceof Text t)) return value;
        String s = t.value();
        return switch (type) {
            case NSTR      -> new Nstr(s);
            case UUID      -> new Uuid(s);
            case URI       -> new Uri(s);
            case EMAIL     -> new Email(s);
            case HOSTNAME  -> new Hostname(s);
            case IPV4      -> new Ipv4(s);
            case IPV6      -> new Ipv6(s);
            case DATE      -> new Date(s);
            case TIME      -> new Time(s);
            case DATETIME  -> new DateTime(s);
            case TIMESTAMP -> new Timestamp(s);
            case TSZ       -> new Tsz(s);
            case INST      -> new Inst(s);
            case DURATION  -> new Duration(s);
            case BASE64    -> new Base64(s);
            case HEX       -> new Hex(s);
            case OID       -> new Oid(s);
            default        -> value; // STR stays Text
        };
    }

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

    /**
     * Returns the string content if this is any string-typed variant ({@link Text},
     * {@link Email}, {@link Uuid}, etc.), otherwise throws.
     */
    default String asText() {
        if (this instanceof Text      t) return t.value();
        if (this instanceof Nstr      t) return t.value();
        if (this instanceof Uuid      t) return t.value();
        if (this instanceof Uri       t) return t.value();
        if (this instanceof Email     t) return t.value();
        if (this instanceof Hostname  t) return t.value();
        if (this instanceof Ipv4      t) return t.value();
        if (this instanceof Ipv6      t) return t.value();
        if (this instanceof Date      t) return t.value();
        if (this instanceof Time      t) return t.value();
        if (this instanceof DateTime  t) return t.value();
        if (this instanceof Timestamp t) return t.value();
        if (this instanceof Tsz       t) return t.value();
        if (this instanceof Inst      t) return t.value();
        if (this instanceof Duration  t) return t.value();
        if (this instanceof Base64    t) return t.value();
        if (this instanceof Hex       t) return t.value();
        if (this instanceof Oid       t) return t.value();
        throw new UnsupportedOperationException("Not a string value: " + this);
    }

    /** Returns true if this is any string-typed variant (Text or any semantic string). */
    default boolean isStringLike() {
        return this instanceof Text || this instanceof Nstr || this instanceof Uuid
            || this instanceof Uri || this instanceof Email || this instanceof Hostname
            || this instanceof Ipv4 || this instanceof Ipv6
            || this instanceof Date || this instanceof Time || this instanceof DateTime
            || this instanceof Timestamp || this instanceof Tsz || this instanceof Inst
            || this instanceof Duration || this instanceof Base64
            || this instanceof Hex || this instanceof Oid;
    }
}
