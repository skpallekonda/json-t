package io.github.datakore.jsont.stringify;

import io.github.datakore.jsont.adapters.AdapterRegistry;
import io.github.datakore.jsont.datagen.DataGenerator;
import io.github.datakore.jsont.grammar.schema.ast.NamespaceT;

public class StreamingJsonTWriterBuilder<T> {
    private DataGenerator<T> generator;
    private NamespaceT namespace;
    private AdapterRegistry registry;

    public StreamingJsonTWriterBuilder<T> generator(DataGenerator<T> generator) {
        this.generator = generator;
        return this;
    }

    public StreamingJsonTWriterBuilder<T> namespace(NamespaceT namespace) {
        this.namespace = namespace;
        return this;
    }

    public StreamingJsonTWriterBuilder<T> registry(AdapterRegistry registry) {
        this.registry = registry;
        return this;
    }

    public StreamingJsonTWriter<T> build(String dataSchema) {
        return new StreamingJsonTWriter<>(dataSchema, generator, namespace, registry);
    }

}
