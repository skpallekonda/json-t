package io.github.datakore.jsont.core;

import io.github.datakore.jsont.adapters.AdapterRegistry;
import io.github.datakore.jsont.adapters.SchemaAdapter;
import io.github.datakore.jsont.errors.collector.DefaultErrorCollector;
import io.github.datakore.jsont.errors.collector.ErrorCollector;
import io.github.datakore.jsont.execution.ParserExecutor;
import io.github.datakore.jsont.grammar.schema.ast.NamespaceT;
import io.github.datakore.jsont.parser.SchemaCatalogVisitor;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;

public class JsonTConfigBuilder {
    private NamespaceT namespaceT;
    private ErrorCollector errorCollector = new DefaultErrorCollector();
    private AdapterRegistry adapterRegistry = new AdapterRegistry();

    private int bufferSize = 1024;
    private Path errorFile;

    public JsonTConfigBuilder withAdapters(SchemaAdapter<?> adapter) {
        adapterRegistry.register(adapter);
        return this;
    }

    public JsonTConfigBuilder withErrorCollector(ErrorCollector collector) {
        this.errorCollector = collector;
        return this;
    }

    public JsonTConfigBuilder source(Path path) throws IOException {
        return source(CharStreams.fromPath(path));
    }

    public JsonTConfigBuilder source(CharStream schemaSource) {
        // Run a "Schema-Only" parse immediately
        SchemaCatalogVisitor listener = new SchemaCatalogVisitor(errorCollector);
        ParserExecutor.executeSchema(schemaSource, errorCollector, listener);
        namespaceT = listener.getNamespaceT();
        return this;
    }

    public JsonTConfigBuilder withErrorFile(Path errorFile) {
        this.errorFile = errorFile;
        return this;
    }

    public JsonTConfig build() {
        return new JsonTConfig(namespaceT, errorCollector, adapterRegistry, bufferSize, errorFile);
    }

    public JsonTConfigBuilder withAdapters(SchemaAdapter<?>[] adapters) {
        Arrays.asList(adapters).forEach(adapter -> adapterRegistry.register(adapter));
        return this;
    }
}
