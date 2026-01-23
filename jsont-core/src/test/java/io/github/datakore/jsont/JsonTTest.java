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
import io.github.datakore.jsont.util.ProgressMonitor;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
        try (InputStream is = new FileInputStream("src/test/resources/all-type-schema.jsont")) {
            StreamingJsonTWriter<AllTypeHolder> writer = getTypedStreamWriter(is, AllTypeHolder.class, null, adapter1, adapter2);
            StringWriter sw = new StringWriter();
            writer.stringify(sw, AllTypeHolder.class);
            System.out.println(sw);
        }
    }

    private <T> StreamingJsonTWriter<T> getTypedStreamWriter(InputStream path, Class<T> clazz, DataGenerator<T> gen, SchemaAdapter<?>... adapters) throws IOException {
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

    private JsonTConfig getJsonTConfig(InputStream scPath, SchemaAdapter<?>... adapters) throws IOException {
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
        try (InputStream is = new FileInputStream("src/test/resources/ns-schema.jsont")) {
            StreamingJsonTWriter<User> writer = getTypedStreamWriter(is, User.class, null, adapter1, adapter2);
            StringWriter sw = new StringWriter();
            writer.stringify(u1, sw, true);
            System.out.println(sw);
        }
    }

    @Test
    void shouldGenerateJsonTString() throws IOException {
        AllTypeHolderAdapter a1 = new AllTypeHolderAdapter();
        DateTypeAdapter a2 = new DateTypeAdapter();
        NumberTypeAdapter a3 = new NumberTypeAdapter();
        StringEntryAdapter a4 = new StringEntryAdapter();
        ArrayEntryAdapter a5 = new ArrayEntryAdapter();
        DataGenerator<AllTypeHolder> gen = new AllTypeHolderDataGen();
        String outFile = String.format("10_000_000-%d.jsont", System.currentTimeMillis());
        Path path = Paths.get(outFile);
        Files.createFile(path);
        long totalRecords = 100_000;
        long batchSize = 5_000;
        long progressMonitoringWindow = 10;
        ProgressMonitor progressMonitor = new ProgressMonitor(totalRecords, batchSize, progressMonitoringWindow);
        progressMonitor.startProgress();
        try (InputStream is = new FileInputStream("src/test/resources/all-type-schema.jsont")) {
            StreamingJsonTWriter<AllTypeHolder> stringifier = getTypedStreamWriter(is, AllTypeHolder.class, gen, a1, a2, a3, a4, a5);
            try (FileWriter writer = new FileWriter(path.toFile())) {
                stringifier.stringify(writer, totalRecords, (int) batchSize, 25, false, progressMonitor);
            }
        }
        progressMonitor.endProgress();
    }

    private JsonTConfig getJsonTConfig(InputStream schemaPath, Path errorPath) throws IOException {
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
        try (InputStream schemaStream = new FileInputStream(schemaPath.toFile())) {
            ParserExecutor.executeDataParse(schemaStream, errorCollector, listener);
            Instant end = Instant.now();
            System.out.println("Total number of records read is " + rowsProcessed.get());
            System.out.printf("Took %s to process %d records", Duration.between(strt, end), rowsProcessed.get());
        }
    }

    @Test
    void shouldReadDataAsList() throws IOException {
        Path schemaPath = Paths.get("src/test/resources/all-type-schema.jsont");
        String dataPath = "500000-1767884033236.jsont";
        try (InputStream is = new FileInputStream(schemaPath.toString())) {
            JsonTConfig config = JsonT.configureBuilder()
                    .withAdapters(new AddressAdapter()).withAdapters(new UserAdapter())
                    .withErrorCollector(new DefaultErrorCollector())
                    .source(is)
                    .build();
            try (FileInputStream fis = new FileInputStream(dataPath)) {
                // Collect stream into a list
                List<User> userList = config.source(fis)
                        .convert(User.class, 2)
                        .collectList()
                        .block();

                Assertions.assertNotNull(userList);
                assertEquals(total, userList.size());
                System.out.println(userList);
            }
        }
    }

    @Test
    void shouldParseUsingJsonTExecution() throws IOException {
        String dataFile = "src/test/resources/all-type-sample.jsont";
        Path schemaPath = Paths.get("src/test/resources/all-type-schema.jsont");
        Path dataPath = Paths.get(dataFile);
        Path errorPath = Paths.get(dataFile.concat(".csv"));
        assert dataPath.toFile().exists();
        AtomicLong rowsProcessed = new AtomicLong();
        Instant start = Instant.now();
        try (
                InputStream is = new FileInputStream(schemaPath.toFile());
                InputStream dataStream = new FileInputStream(dataPath.toFile())) {
            JsonTConfig config = getJsonTConfig(is, errorPath);
            JsonTExecution execution = config.source(dataStream);
            execution.parse() // Use 4 parallel threads
                    .doOnNext(row -> {
                        long count = rowsProcessed.incrementAndGet();
                        if (count % 10 == 0) {
                            System.out.printf("Processed %d rows so far - time elapsed %s \n", count, Duration.between(start, Instant.now()));
                        }
                    })
                    .blockLast(); // Wait for completion
        }
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
