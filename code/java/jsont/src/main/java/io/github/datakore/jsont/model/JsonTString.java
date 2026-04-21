package io.github.datakore.jsont.model;

import java.util.Objects;

/**
 * Universal string type for JsonT, covering all semantic variants.
 * All implementations are records that carry the raw string value.
 */
public sealed interface JsonTString extends JsonTValue
        permits JsonTString.Plain, JsonTString.Nstr,
                JsonTString.Uuid, JsonTString.Uri, JsonTString.Email,
                JsonTString.Hostname, JsonTString.Ipv4, JsonTString.Ipv6,
                JsonTString.Date, JsonTString.Time, JsonTString.DateTime,
                JsonTString.Timestamp, JsonTString.Tsz, JsonTString.Inst,
                JsonTString.Duration,
                JsonTString.Base64, JsonTString.Hex, JsonTString.Oid {

    String value();

    default String typeName() {
        return getClass().getSimpleName().toLowerCase();
    }

    static String quote(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    record Plain(String value) implements JsonTString {
        public Plain { Objects.requireNonNull(value); }
        @Override public String toString() { return quote(value); }
    }

    record Nstr(String value) implements JsonTString {
        public Nstr { Objects.requireNonNull(value); }
        @Override public String toString() { return quote(value); }
    }

    record Uuid(String value) implements JsonTString {
        public Uuid { Objects.requireNonNull(value); }
        @Override public String toString() { return quote(value); }
    }

    record Uri(String value) implements JsonTString {
        public Uri { Objects.requireNonNull(value); }
        @Override public String toString() { return quote(value); }
    }

    record Email(String value) implements JsonTString {
        public Email { Objects.requireNonNull(value); }
        @Override public String toString() { return quote(value); }
    }

    record Hostname(String value) implements JsonTString {
        public Hostname { Objects.requireNonNull(value); }
        @Override public String toString() { return quote(value); }
    }

    record Ipv4(String value) implements JsonTString {
        public Ipv4 { Objects.requireNonNull(value); }
        @Override public String toString() { return quote(value); }
    }

    record Ipv6(String value) implements JsonTString {
        public Ipv6 { Objects.requireNonNull(value); }
        @Override public String toString() { return quote(value); }
    }

    record Date(String value) implements JsonTString {
        public Date { Objects.requireNonNull(value); }
        @Override public String toString() { return quote(value); }
    }

    record Time(String value) implements JsonTString {
        public Time { Objects.requireNonNull(value); }
        @Override public String toString() { return quote(value); }
    }

    record DateTime(String value) implements JsonTString {
        public DateTime { Objects.requireNonNull(value); }
        @Override public String toString() { return quote(value); }
    }

    record Timestamp(String value) implements JsonTString {
        public Timestamp { Objects.requireNonNull(value); }
        @Override public String toString() { return quote(value); }
    }

    record Tsz(String value) implements JsonTString {
        public Tsz { Objects.requireNonNull(value); }
        @Override public String toString() { return quote(value); }
    }

    record Inst(String value) implements JsonTString {
        public Inst { Objects.requireNonNull(value); }
        @Override public String toString() { return quote(value); }
    }

    record Duration(String value) implements JsonTString {
        public Duration { Objects.requireNonNull(value); }
        @Override public String toString() { return quote(value); }
    }

    record Base64(String value) implements JsonTString {
        public Base64 { Objects.requireNonNull(value); }
        @Override public String toString() { return quote(value); }
    }

    record Hex(String value) implements JsonTString {
        public Hex { Objects.requireNonNull(value); }
        @Override public String toString() { return quote(value); }
    }

    record Oid(String value) implements JsonTString {
        public Oid { Objects.requireNonNull(value); }
        @Override public String toString() { return quote(value); }
    }
}
