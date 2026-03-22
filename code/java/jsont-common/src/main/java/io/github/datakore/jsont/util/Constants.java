package io.github.datakore.jsont.util;

import java.util.Set;

public interface Constants {

    String UNSPECIFIED_TOKEN = "~!@#@!~";
    String UNSPECIFIED_TYPE = "_";

    Set<String> VALID_STRING_TYPES = CollectionUtils.asSet(
            "str",
            "char",
            "password",
            "email",
            "uuid",
            "uri",
            "url");

    Set<String> VALID_NUMBER_TYPES = CollectionUtils.asSet("int", "long", "float", "double");

    Set<String> VALID_BOOLEAN_TYPES = CollectionUtils.asSet("boolean", "bool");

    Set<String> VALID_DATE_TYPES = CollectionUtils.asSet("date", "datetime", "time", "timestamp");

    Set<String> VALID_BYTE_TYPES = CollectionUtils.asSet("byte", "bytes", "base64", "hex");

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
