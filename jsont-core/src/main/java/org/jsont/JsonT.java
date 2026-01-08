package org.jsont;

import org.antlr.v4.runtime.CharStreams;
import org.jsont.core.JsonTBuilder;
import org.jsont.core.JsonTContext;

import java.io.IOException;
import java.nio.file.Path;

public final class JsonT {
    public static JsonTBuilder builder() {
        return new JsonTBuilder();
    }

    public static JsonTContext parseCatalog(String source) throws IOException {
        return builder().parseCatalog(CharStreams.fromString(source));
    }

    public static JsonTContext parseCatalog(Path path) throws IOException {
        return builder().parseCatalog(path);
    }
}
