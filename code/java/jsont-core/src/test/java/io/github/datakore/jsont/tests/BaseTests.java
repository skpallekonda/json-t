package io.github.datakore.jsont.tests;

import io.github.datakore.jsont.JsonT;
import io.github.datakore.jsont.adapters.*;
import io.github.datakore.jsont.core.JsonTConfig;
import io.github.datakore.jsont.datagen.DataGenerator;
import io.github.datakore.jsont.errors.collector.DefaultErrorCollector;
import io.github.datakore.jsont.errors.collector.ErrorCollector;
import io.github.datakore.jsont.stringify.StreamingJsonTWriter;
import io.github.datakore.jsont.stringify.StreamingJsonTWriterBuilder;

import java.io.IOException;
import java.nio.file.Paths;

public abstract class BaseTests {

    protected ErrorCollector errorCollector = new DefaultErrorCollector();
    protected JsonTConfig jsonTConfig;

    protected JsonTConfig getJsonTConfig(String schemaPath, SchemaAdapter<?>... adapters) {
        return JsonT.configureBuilder()
                .withAdapters(adapters)
                .withErrorCollector(errorCollector).source(Paths.get(schemaPath)).build();
    }

    protected <T> StreamingJsonTWriter<T> getTypedStreamWriter(String path, Class<T> clazz, DataGenerator<T> gen, SchemaAdapter<?>... adapters) throws IOException {
        jsonTConfig = getJsonTConfig(path, adapters);
        StreamingJsonTWriterBuilder<T> builder = new StreamingJsonTWriterBuilder<T>()
                .registry(jsonTConfig.getAdapters())
                .namespace(jsonTConfig.getNamespace());
        if (gen != null) {
            builder = builder.generator(gen);
        }
        return builder
                .build(clazz.getSimpleName());
    }

    protected SchemaAdapter<?>[] loadAllTypeAdapters() {
        return new SchemaAdapter[]{new AllTypeHolderAdapter(), new NumberTypeAdapter(), new DateTypeAdapter(), new StringEntryAdapter(), new ArrayEntryAdapter()};
    }

    protected SchemaAdapter<?>[] loadUserAdapters() {
        return new SchemaAdapter[]{new UserAdapter(), new AddressAdapter()};
    }
}
