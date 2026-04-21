package io.github.datakore.jsont.model;

import java.util.Objects;

/**
 * Universal numeric type for JsonT, covering integers, decimals, and integer-based temporal variants.
 */
public sealed interface JsonTNumber extends JsonTValue
        permits JsonTNumber.I16, JsonTNumber.I32, JsonTNumber.I64,
                JsonTNumber.U16, JsonTNumber.U32, JsonTNumber.U64,
                JsonTNumber.D32, JsonTNumber.D64, JsonTNumber.D128,
                JsonTNumber.Date, JsonTNumber.Time, JsonTNumber.DateTime, JsonTNumber.Timestamp {

    record I16(short value) implements JsonTNumber {
        @Override public String toString() { return Short.toString(value); }
    }

    record I32(int value) implements JsonTNumber {
        @Override public String toString() { return Integer.toString(value); }
    }

    record I64(long value) implements JsonTNumber {
        @Override public String toString() { return Long.toString(value); }
    }

    record U16(int value) implements JsonTNumber {
        public U16 { if (value < 0 || value > 65535) throw new IllegalArgumentException(); }
        @Override public String toString() { return Integer.toString(value); }
    }

    record U32(long value) implements JsonTNumber {
        public U32 { if (value < 0 || value > 4294967295L) throw new IllegalArgumentException(); }
        @Override public String toString() { return Long.toString(value); }
    }

    record U64(long value) implements JsonTNumber {
        @Override public String toString() { return Long.toUnsignedString(value); }
    }

    record D32(float value) implements JsonTNumber {
        @Override public String toString() { return Float.toString(value); }
    }

    record D64(double value) implements JsonTNumber {
        @Override public String toString() { return Double.toString(value); }
    }

    record D128(java.math.BigDecimal value) implements JsonTNumber {
        public D128 { Objects.requireNonNull(value); }
        @Override public String toString() { return value.toPlainString(); }
    }

    record Date(int value) implements JsonTNumber {
        @Override public String toString() { return Integer.toString(value); }
    }

    record Time(int value) implements JsonTNumber {
        @Override public String toString() { return Integer.toString(value); }
    }

    record DateTime(long value) implements JsonTNumber {
        @Override public String toString() { return Long.toString(value); }
    }

    record Timestamp(long value) implements JsonTNumber {
        @Override public String toString() { return Long.toString(value); }
    }
}
