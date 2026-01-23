package io.github.datakore.jsont.core;

import io.github.datakore.jsont.adapters.AdapterRegistry;
import io.github.datakore.jsont.errors.collector.ErrorCollector;
import io.github.datakore.jsont.grammar.schema.ast.NamespaceT;
import io.github.datakore.jsont.stringify.StreamingJsonTWriter;
import io.github.datakore.jsont.stringify.StreamingJsonTWriterBuilder;
import io.github.datakore.jsont.util.StepCounter;

import java.io.InputStream;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Consumer;

public final class JsonTConfig {
    final NamespaceT namespaceT;
    final ErrorCollector errorCollector;
    final AdapterRegistry adapterRegistry;
    final int bufferSize;
    final Path errorFile;
    private Consumer<StepCounter> monitor;


    public JsonTConfig(NamespaceT namespaceT, ErrorCollector errorCollector, AdapterRegistry adapterRegistry, int bufferSize, Path errorFile) {
        this.namespaceT = namespaceT;
        this.errorCollector = errorCollector;
        this.adapterRegistry = adapterRegistry;
        this.bufferSize = bufferSize;
        this.errorFile = errorFile;
    }

    public JsonTConfig withMonitor(Consumer<StepCounter> monitor) {
        this.monitor = monitor;
        return this;
    }

    public JsonTExecution source(InputStream stream) {
        Objects.requireNonNull(stream, "Source cannot be null");
        return new JsonTExecution(this, stream, this.monitor);
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
