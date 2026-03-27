package io.github.datakore.jsont.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.*;

class ScalarTypeTest {

    @Test
    void keyword_returnsExpectedString() {
        assertEquals("i32",  ScalarType.I32.keyword());
        assertEquals("i64",  ScalarType.I64.keyword());
        assertEquals("str",  ScalarType.STR.keyword());
        assertEquals("bool", ScalarType.BOOL.keyword());
        assertEquals("d64",  ScalarType.D64.keyword());
        assertEquals("uuid", ScalarType.UUID.keyword());
        assertEquals("date", ScalarType.DATE.keyword());
        assertEquals("b64",  ScalarType.B64.keyword());
    }

    @ParameterizedTest
    @CsvSource({
        "i16, I16", "i32, I32", "i64, I64",
        "u16, U16", "u32, U32", "u64, U64",
        "d32, D32", "d64, D64", "d128, D128",
        "bool, BOOL", "str, STR", "nstr, NSTR",
        "uri, URI", "uuid, UUID",
        "date, DATE", "time, TIME", "dtm, DTM",
        "ts, TS", "tsz, TSZ", "dur, DUR", "inst, INST",
        "b64, B64", "oid, OID", "hex, HEX"
    })
    void fromKeyword_roundTrips(String keyword, String expectedName) {
        ScalarType type = ScalarType.fromKeyword(keyword);
        assertEquals(expectedName, type.name());
        assertEquals(keyword, type.keyword());
    }

    @Test
    void fromKeyword_unknownThrows() {
        assertThrows(IllegalArgumentException.class, () -> ScalarType.fromKeyword("unknown"));
        assertThrows(IllegalArgumentException.class, () -> ScalarType.fromKeyword("INT"));
        assertThrows(IllegalArgumentException.class, () -> ScalarType.fromKeyword(""));
    }

    @ParameterizedTest
    @EnumSource(value = ScalarType.class,
            names = {"I16", "I32", "I64", "U16", "U32", "U64", "D32", "D64", "D128"})
    void isNumeric_trueForNumericTypes(ScalarType type) {
        assertTrue(type.isNumeric());
    }

    @ParameterizedTest
    @EnumSource(value = ScalarType.class,
            names = {"BOOL", "STR", "NSTR", "URI", "UUID", "DATE", "TIME"})
    void isNumeric_falseForNonNumericTypes(ScalarType type) {
        assertFalse(type.isNumeric());
    }

    @ParameterizedTest
    @EnumSource(value = ScalarType.class,
            names = {"STR", "NSTR", "URI", "UUID", "EMAIL", "HOSTNAME",
                     "IPV4", "IPV6", "DATE", "TIME", "DTM", "TS", "TSZ",
                     "DUR", "INST", "B64", "OID", "HEX"})
    void isStringLike_trueForTextualTypes(ScalarType type) {
        assertTrue(type.isStringLike());
    }

    @Test
    void isStringLike_falseForNumericAndBool() {
        assertFalse(ScalarType.I32.isStringLike());
        assertFalse(ScalarType.D64.isStringLike());
        assertFalse(ScalarType.BOOL.isStringLike());
    }
}
