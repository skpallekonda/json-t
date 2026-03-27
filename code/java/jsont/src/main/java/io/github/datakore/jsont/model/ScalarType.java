package io.github.datakore.jsont.model;

/**
 * Enumeration of all supported scalar types in the JsonT type system.
 */
public enum ScalarType {
    I16("i16"),
    I32("i32"),
    I64("i64"),
    U16("u16"),
    U32("u32"),
    U64("u64"),
    D32("d32"),
    D64("d64"),
    D128("d128"),
    BOOL("bool"),
    STR("str"),
    NSTR("nstr"),
    URI("uri"),
    UUID("uuid"),
    EMAIL("email"),
    HOSTNAME("hostname"),
    IPV4("ipv4"),
    IPV6("ipv6"),
    DATE("date"),
    TIME("time"),
    DTM("dtm"),
    TS("ts"),
    TSZ("tsz"),
    DUR("dur"),
    INST("inst"),
    B64("b64"),
    OID("oid"),
    HEX("hex");

    private final String keyword;

    ScalarType(String keyword) {
        this.keyword = keyword;
    }

    /** Returns the keyword string used in JsonT schema text. */
    public String keyword() {
        return keyword;
    }

    /** @throws IllegalArgumentException if the keyword is not recognised */
    public static ScalarType fromKeyword(String keyword) {
        for (ScalarType t : values()) {
            if (t.keyword.equals(keyword)) {
                return t;
            }
        }
        throw new IllegalArgumentException("Unknown scalar type keyword: " + keyword);
    }

    /** Returns true if this type represents a numeric value. */
    public boolean isNumeric() {
        return switch (this) {
            case I16, I32, I64, U16, U32, U64, D32, D64, D128 -> true;
            default -> false;
        };
    }

    /** Returns true if this type represents a string-like value. */
    public boolean isStringLike() {
        return switch (this) {
            case STR, NSTR, URI, UUID, EMAIL, HOSTNAME, IPV4, IPV6,
                    DATE, TIME, DTM, TS, TSZ, DUR, INST, B64, OID, HEX -> true;
            default -> false;
        };
    }
}
