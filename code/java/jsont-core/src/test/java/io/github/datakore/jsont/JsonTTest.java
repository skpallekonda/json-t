package io.github.datakore.jsont;

import io.github.datakore.jsont.adapters.AddressAdapter;
import io.github.datakore.jsont.adapters.SchemaAdapter;
import io.github.datakore.jsont.adapters.UserAdapter;
import io.github.datakore.jsont.core.JsonTConfig;
import io.github.datakore.jsont.datagen.DataGenerator;
import io.github.datakore.jsont.entity.AllTypeHolder;
import io.github.datakore.jsont.errors.collector.DefaultErrorCollector;
import io.github.datakore.jsont.errors.collector.ErrorCollector;
import io.github.datakore.jsont.stringify.StreamingJsonTWriter;
import io.github.datakore.jsont.stringify.StreamingJsonTWriterBuilder;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Path;
import java.nio.file.Paths;

public class JsonTTest {
    //String schemaPath = "src/test/resources/schema.jsont";
    String schemaPath = "src/test/resources/ns-schema.jsont";
    // String dataPath = "src/test/resources/data.jsont";
    String dataPath = "500000-1767884033236.jsont";
    String errorFile = "errors%d.json";


    Path scPath = Paths.get(schemaPath);
    Path datPath = Paths.get(dataPath);


    int total = 2;
    int batchSize = 1;
    ErrorCollector errorCollector = new DefaultErrorCollector();
    UserAdapter adapter1 = new UserAdapter();
    AddressAdapter adapter2 = new AddressAdapter();

    @Test
    void shouldProduceSchemaStringify() throws IOException {
        Path path = Paths.get("src/test/resources/all-type-schema.jsont");
        StreamingJsonTWriter<AllTypeHolder> writer = getTypedStreamWriter(path, AllTypeHolder.class, null, adapter1, adapter2);
        StringWriter sw = new StringWriter();
        writer.stringify(sw, AllTypeHolder.class);
        System.out.println(sw);
    }

    private <T> StreamingJsonTWriter<T> getTypedStreamWriter(Path path, Class<T> clazz, DataGenerator<T> gen, SchemaAdapter<?>... adapters) throws IOException {
        JsonTConfig config = getJsonTConfig(path, adapters);
        StreamingJsonTWriterBuilder<T> builder = new StreamingJsonTWriterBuilder<T>()
                .registry(config.getAdapters())
                .namespace(config.getNamespace());
        if (gen != null) {
            builder = builder.generator(gen);
        }
        return builder
                .build(clazz.getSimpleName());
    }

    private JsonTConfig getJsonTConfig(Path scPath, SchemaAdapter<?>... adapters) throws IOException {
        return JsonT.configureBuilder()
                .withAdapters(adapters)
                .withErrorCollector(errorCollector).source(scPath).build();
    }

}
