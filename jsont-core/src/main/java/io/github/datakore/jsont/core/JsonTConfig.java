package io.github.datakore.jsont.core;

import io.github.datakore.jsont.adapters.AdapterRegistry;
import io.github.datakore.jsont.errors.collector.ErrorCollector;
import io.github.datakore.jsont.grammar.schema.ast.NamespaceT;
import io.github.datakore.jsont.stringify.StreamingJsonTWriter;
import io.github.datakore.jsont.stringify.StreamingJsonTWriterBuilder;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.Objects;

public final class JsonTConfig {
    final NamespaceT namespaceT;
    final ErrorCollector errorCollector;
    final AdapterRegistry adapterRegistry;
    final int bufferSize;
    final Path errorFile;


    public JsonTConfig(NamespaceT namespaceT, ErrorCollector errorCollector, AdapterRegistry adapterRegistry, int bufferSize, Path errorFile) {
        this.namespaceT = namespaceT;
        this.errorCollector = errorCollector;
        this.adapterRegistry = adapterRegistry;
        this.bufferSize = bufferSize;
        this.errorFile = errorFile;
    }

    public JsonTExecution source(Path path) {
        Objects.requireNonNull(path, "Source cannot be null");
        try {
            return new JsonTExecution(this, CharStreams.fromPath(path));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public JsonTExecution source(CharStream stream) {
        Objects.requireNonNull(stream, "Source cannot be null");
        return new JsonTExecution(this, stream);
    }

    public <T> String stringify(Class<T> clazz) {
        StreamingJsonTWriter<T> writer = new StreamingJsonTWriterBuilder<T>()
                .registry(this.adapterRegistry)
                .namespace(this.namespaceT)
                .build(clazz.getSimpleName());
        StringWriter sw = new StringWriter();
        writer.stringify(sw, clazz);
        return sw.toString();
    }

    public AdapterRegistry getAdapters() {
        return this.adapterRegistry;
    }

    public NamespaceT getNamespace() {
        return this.namespaceT;
    }
}
