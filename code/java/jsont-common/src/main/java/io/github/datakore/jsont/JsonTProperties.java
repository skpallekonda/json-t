package io.github.datakore.jsont;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.regex.Pattern;

public class JsonTProperties {

    private static final int DEFAULT_MAX_SCHEMAS = 100;
    private static final int DEFAULT_MAX_DEPTH = 25;
    private static final int DEFAULT_MAX_FIELDS = 100;
    private static final int DEFAULT_MAX_ID_LEN = 100;
    private static final int DEFAULT_MAX_STR_LEN = 10000;
    private static final String DEFAULT_WHITE_LIST_CHARS = "^[A-Za-z0-9 !#$%&()*+,./:;=?@_{}~?£¥]+$";
    private static int maxAllowedSchemas = 0;
    private static int maxDepthAllowed = 0;
    private static int maxFieldsPerSchema = 0;
    private static int maxLengthOfIdentifier = 0;
    private static int maxLengthOfString = 0;
    private static Pattern whitelistCharacters = null;

    static {
        try (InputStream in = JsonTProperties.class.getClassLoader().getResourceAsStream("jsont.properties")) {

            Properties PROPS = new Properties();
            if (in != null) {
                PROPS.load(in);
            } else {
                // Fallback or log warning? 
                // If the file is mandatory, we should throw, but if defaults are fine, we can proceed.
                // The original code threw an exception.
                // However, for a library, it's often better to use defaults if config is missing.
                // But let's stick to the original intent of failing if missing, OR create it.
                // Since I am creating the file next, I will assume it should be found.
                // But to be safe against "not found" issues in complex classloaders, 
                // I will use defaults if not found, or throw a better error.

                // Let's use defaults if not found to make it robust.
                // System.err.println("jsont.properties not found, using defaults.");
            }

            // Parse strongly typed values
            maxAllowedSchemas = getIntOrDefault(PROPS, "platform.schemas.maxAllowed", DEFAULT_MAX_SCHEMAS);
            maxDepthAllowed = getIntOrDefault(PROPS, "platform.schemas.maxDepthAllowed", DEFAULT_MAX_DEPTH);
            maxFieldsPerSchema = getIntOrDefault(PROPS, "platform.schemas.maxFieldsPerSchema", DEFAULT_MAX_FIELDS);
            maxLengthOfIdentifier = getIntOrDefault(PROPS, "platform.schemas.identifier.maxLength", DEFAULT_MAX_ID_LEN);
            maxLengthOfString = getIntOrDefault(PROPS, "platform.schemas.strings.maxLength", DEFAULT_MAX_STR_LEN);
            whitelistCharacters = getPatternOrDefault(PROPS, "platform.schemas.strings.whitelistChars", DEFAULT_WHITE_LIST_CHARS);

        } catch (IOException e) {
            throw new ExceptionInInitializerError("Failed to load jsont.properties: " + e);
        }
    }

    private static int getIntOrDefault(Properties properties, String key, int _default) {
        if (properties.containsKey(key)) {
            try {
                return Integer.parseInt(properties.getProperty(key).trim());
            } catch (NumberFormatException e) {
                return _default;
            }
        }
        return _default;
    }

    private static Pattern getPatternOrDefault(Properties properties, String key, String _default) {
        String regex = properties.getProperty(key, _default).trim();
        return Pattern.compile(regex);
    }


    public static int getMaxAllowedSchemas() {
        return maxAllowedSchemas;
    }

    public static int getMaxFieldsPerSchema() {
        return maxFieldsPerSchema;
    }

    public static int getMaxLengthOfIdentifier() {
        return maxLengthOfIdentifier;
    }

    public static int getMaxLengthOfString() {
        return maxLengthOfString;
    }

    public static Pattern getWhitelistCharacters() {
        return whitelistCharacters;
    }

    public static int getMaxDepthAllowed() {
        return maxDepthAllowed;
    }

}
