package io.github.datakore.jsont.util;

public class StringUtils {
    public static boolean isBlank(String str) {
        return str == null || str.trim().isEmpty();
    }

    public static boolean isEmpty(String str) {
        return str == null || str.trim().isEmpty();
    }

    public static String removeQuotes(String str) {
        if (isBlank(str)) {
            return null;
        }
        if ((str.startsWith("\"") && str.endsWith("\"")) || (str.startsWith("'") && str.endsWith("'"))) {
            return str.substring(1, str.length() - 1);
        } else {
            return str;
        }
    }

    public static String wrapInQuotes(String str) {
        if (isBlank(str)) {
            return "null";
        }
        str = removeQuotes(str);
        return "\"" + str + "\"";
    }
}
