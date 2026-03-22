package io.github.datakore.jsont;

import io.github.datakore.jsont.core.JsonTConfigBuilder;

/**
 * The main entry point for the JsonT library.
 * <p>
 * This class provides static methods to configure and build a JsonT instance.
 * </p>
 */
public final class JsonT {
    private JsonT() {
    }

    /**
     * Creates a new {@link JsonTConfigBuilder} to configure a JsonT instance.
     *
     * @return a new {@link JsonTConfigBuilder}
     */
    public static JsonTConfigBuilder configureBuilder() {
        return new JsonTConfigBuilder();
    }
}
