package org.jsont.types;

import java.util.Set;

public interface Constants {

    Set<String> VALID_STRING_TYPES = Set.of(
            "str",
            "char",
            "password",
            "email",
            "uuid",
            "uri",
            "url");

    Set<String> VALID_NUMBER_TYPES = Set.of("int", "long", "float", "double");

    Set<String> VALID_BOOLEAN_TYPES = Set.of("boolean", "bool");

    Set<String> VALID_DATE_TYPES = Set.of("date", "datetime", "time", "timestamp");

    Set<String> VALID_BYTE_TYPES = Set.of("byte", "bytes", "base64", "hex");

    static boolean isValidStringType(String type) {
        return VALID_STRING_TYPES.contains(type);
    }

    static boolean isValidNumberType(String type) {
        return VALID_NUMBER_TYPES.contains(type);
    }

    static boolean isValidBooleanType(String type) {
        return VALID_BOOLEAN_TYPES.contains(type);
    }

    static boolean isValidDateType(String type) {
        return VALID_DATE_TYPES.contains(type);
    }

    static boolean isValidByteType(String type) {
        return VALID_BYTE_TYPES.contains(type);
    }
}
