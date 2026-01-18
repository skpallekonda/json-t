package io.github.datakore.jsont;

import io.github.datakore.jsont.adapters.*;
import io.github.datakore.jsont.core.JsonTConfig;
import io.github.datakore.jsont.core.JsonTConfigBuilder;
import io.github.datakore.jsont.core.JsonTExecution;
import io.github.datakore.jsont.datagen.DataGenerator;
import io.github.datakore.jsont.entity.Address;
import io.github.datakore.jsont.entity.AllTypeHolder;
import io.github.datakore.jsont.entity.User;
import io.github.datakore.jsont.errors.ValidationError;
import io.github.datakore.jsont.errors.collector.DefaultErrorCollector;
import io.github.datakore.jsont.errors.collector.ErrorCollector;
import io.github.datakore.jsont.execution.DataStream;
import io.github.datakore.jsont.execution.ParserExecutor;
import io.github.datakore.jsont.grammar.data.RowNode;
import io.github.datakore.jsont.parser.DataRowVisitor;
import io.github.datakore.jsont.stringify.StreamingJsonTWriter;
import io.github.datakore.jsont.stringify.StreamingJsonTWriterBuilder;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

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
        StreamingJsonTWriter<AllTypeHolder> writer = getTypedStreamWriter("src/test/resources/all-type-schema.jsont", AllTypeHolder.class, null, adapter1, adapter2);
        StringWriter sw = new StringWriter();
        writer.stringify(sw, AllTypeHolder.class);
        System.out.println(sw);
    }

    private <T> StreamingJsonTWriter<T> getTypedStreamWriter(String path, Class<T> clazz, DataGenerator<T> gen, SchemaAdapter<?>... adapters) throws IOException {
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

    private JsonTConfig getJsonTConfig(String schemaPath, SchemaAdapter<?>... adapters) throws IOException {
        Path scPath = Paths.get(schemaPath);
        assert scPath.toFile().exists();
        return JsonT.configureBuilder()
                .withAdapters(adapters)
                .withErrorCollector(errorCollector).source(scPath).build();
    }

    @Test
    void shouldStringifyData() throws IOException {
        Address add = new Address("Chennai", "12345", "ACTIVE");
        User u1 = new User(123, "sasikp", "ADMIN", add);
        u1.setEmail("sasikp@abcdef.com");
        u1.setTags(new String[]{"programmer"});
        StreamingJsonTWriter<User> writer = getTypedStreamWriter("src/test/resources/ns-schema.jsont", User.class, null, adapter1, adapter2);
        StringWriter sw = new StringWriter();
        writer.stringify(u1, sw, true).block();
        System.out.println(sw);
    }

    @Test
    void shouldGenerateJsonTString() throws IOException {
        AllTypeHolderAdapter a1 = new AllTypeHolderAdapter();
        DateTypeAdapter a2 = new DateTypeAdapter();
        NumberTypeAdapter a3 = new NumberTypeAdapter();
        StringEntryAdapter a4 = new StringEntryAdapter();
        ArrayEntryAdapter a5 = new ArrayEntryAdapter();
        DataGenerator<AllTypeHolder> gen = new AllTypeHolderDataGen();
        StreamingJsonTWriter<AllTypeHolder> stringifier = getTypedStreamWriter(
                "src/test/resources/all-type-schema.jsont", AllTypeHolder.class, gen, a1, a2, a3, a4, a5);
        StringWriter sw = new StringWriter();
        stringifier.writeBatch(sw, 100, 10, 25, false).block();
        System.out.println(sw);
    }

    private JsonTConfig getJsonTConfig(Path errorPath) throws IOException {
        Path schemaPath = Paths.get("src/test/resources/all-type-schema.jsont");
        assert schemaPath.toFile().exists();
        AllTypeHolderAdapter a1 = new AllTypeHolderAdapter();
        DateTypeAdapter a2 = new DateTypeAdapter();
        NumberTypeAdapter a3 = new NumberTypeAdapter();
        StringEntryAdapter a4 = new StringEntryAdapter();
        ArrayEntryAdapter a5 = new ArrayEntryAdapter();
        JsonTConfigBuilder builder = JsonT.configureBuilder()
                .withErrorCollector(errorCollector).withAdapters(a1).withAdapters(a2).withAdapters(a3).withAdapters(a4).withAdapters(a5);
        if (errorPath != null) {
            builder = builder.withErrorFile(errorPath);
        }
        return builder.source(schemaPath).build();
    }

    @Test
    void shouldParseAllTypeData() throws IOException {
        Path schemaPath = Paths.get("10000000-1768667773899.jsont");
        assert schemaPath.toFile().exists();
        CharStream schemaStream = CharStreams.fromPath(schemaPath);
        AtomicLong rowsProcessed = new AtomicLong();
        Instant strt = Instant.now();
        DataStream dataStream = new DataStream() {
            @Override
            public void onRowParsed(RowNode row) {
                rowsProcessed.incrementAndGet();
//                if (rowsProcessed.get() % 100 == 0 || rowsProcessed.get() == 1) {
//                    System.out.println("Row parsed: " + row);
//                }
                if (rowsProcessed.get() % 100000 == 0) {
                    System.out.printf("Processed %d rows so far - time elapsed %s \n", rowsProcessed.get(), Duration.between(strt, Instant.now()));
                }
            }

            @Override
            public void onEOF() {
                System.out.println("EOF");
            }

            @Override
            public Flux<RowNode> rows() {
                return null;
            }

            @Override
            public void onRowError(int rowIndex, List<ValidationError> errors) {
                System.out.println(rowIndex + " " + errors);
            }
        };
        DataRowVisitor listener = new DataRowVisitor(errorCollector, null, dataStream);
        ParserExecutor.executeDataParse(schemaStream, errorCollector, listener);
        Instant end = Instant.now();
        System.out.println("Total number of records read is " + rowsProcessed.get());
        System.out.printf("Took %s to process %d records", Duration.between(strt, end), rowsProcessed.get());
    }

    @Test
    void shouldParseUsingJsonTExecution() throws IOException {
        String dataFile = "src/test/resources/all-type-sample.jsont";
        Path dataPath = Paths.get(dataFile);
        assert dataPath.toFile().exists();
        Path errorPath = Paths.get(dataFile.concat(".csv"));
        JsonTConfig config = getJsonTConfig(errorPath);
        JsonTExecution execution = config.source(dataPath);

        AtomicLong rowsProcessed = new AtomicLong();
        Instant start = Instant.now();

        execution.parse(4) // Use 4 parallel threads
                .doOnNext(row -> {
                    long count = rowsProcessed.incrementAndGet();
                    if (count % 10 == 0) {
                        System.out.printf("Processed %d rows so far - time elapsed %s \n", count, Duration.between(start, Instant.now()));
                    }
                })
                .blockLast(); // Wait for completion

        Instant end = Instant.now();
        System.out.println("Total number of records read is " + rowsProcessed.get());
        System.out.printf("Took %s to process %d records", Duration.between(start, end), rowsProcessed.get());
    }

    class AllTypeHolderDataGen implements DataGenerator<AllTypeHolder> {
        @Override
        public AllTypeHolder generate(String schema) {
            AllTypeHolder ah = new AllTypeHolder();
            ah.setNumber(numberTypeAdapter.generate());
            ah.setDate(dateTypeAdapter.generate());
            ah.setStr(stringEntryAdapter.generate());
            ah.setArray(arrayEntryAdapter.generate());
            return ah;
        }
    }

    private List<AllTypeHolder> generateHolderData(int count) {
        List<AllTypeHolder> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            AllTypeHolder ah = new AllTypeHolder();
            ah.setNumber(numberTypeAdapter.generate());
            ah.setDate(dateTypeAdapter.generate());
            ah.setStr(stringEntryAdapter.generate());
            ah.setArray(arrayEntryAdapter.generate());
            list.add(ah);
        }
        return list;
    }

    DateTypeAdapter dateTypeAdapter = new DateTypeAdapter();
    NumberTypeAdapter numberTypeAdapter = new NumberTypeAdapter();

    StringEntryAdapter stringEntryAdapter = new StringEntryAdapter();
    ArrayEntryAdapter arrayEntryAdapter = new ArrayEntryAdapter();


}
