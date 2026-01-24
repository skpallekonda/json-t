package io.github.datakore.jsont.benchmark;

import io.github.datakore.jsont.JsonT;
import io.github.datakore.jsont.adapters.SchemaAdapter;
import io.github.datakore.jsont.core.JsonTConfig;
import io.github.datakore.jsont.errors.collector.DefaultErrorCollector;
import io.github.datakore.jsont.errors.collector.ErrorCollector;
import io.github.datakore.jsont.stringify.StreamingJsonTWriter;
import io.github.datakore.jsont.stringify.StreamingJsonTWriterBuilder;
import io.github.datakore.marketplace.adapters.*;
import io.github.datakore.marketplace.datagen.OrderDataGenerator;
import io.github.datakore.marketplace.entity.Order;
import org.openjdk.jmh.annotations.*;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 1, time = 10)
@Measurement(iterations = 3, time = 30)
@Fork(1)
public class JsonTBenchmark {

    private static final String SCHEMA_PATH = "jsont-benchmark/src/main/java/io/github/datakore/marketplace/entity/ns-marketplace-schema.jsont";
    private static final OrderDataGenerator generator = new OrderDataGenerator();

    @Param({"1000000","2000000"})
    // 100k, 1M. 5M/10M might be too slow for standard JMH iteration times
    private long recordCount;

    private JsonTConfig jsonTConfig;
    private Path tempFile;

    @Setup(Level.Trial)
    public void setup() throws IOException {
        // 1. Setup Config
        ErrorCollector errorCollector = new DefaultErrorCollector();
        File schemaFile = new File(SCHEMA_PATH);
        if (!schemaFile.exists()) {
            System.out.println(schemaFile.getAbsoluteFile().toString());
            // Fallback for running inside module
            schemaFile = new File("src/main/java/io/github/datakore/marketplace/entity/ns-marketplace-schema.jsont");
        }
        generator.initialize();
        try (InputStream is = Files.newInputStream(schemaFile.toPath())) {
            jsonTConfig = JsonT.configureBuilder()
                    .withAdapters(loadAdapters())
                    .withErrorCollector(errorCollector)
                    .source(is)
                    .build();
        }

    }

    @TearDown(Level.Trial)
    public void tearDown() throws IOException {
        // Files.deleteIfExists(tempFile);
    }

    // Benchmark 1: Writing (Stringify)
    // We write to a NullWriter or a temp file. Writing to disk affects IO, but that's part of the use case.
    // To measure pure CPU/Throughput of generation+stringify, we can use a NullWriter.
    @Benchmark
    public void benchmarkStringify() throws IOException {
        // We use a fresh writer for each iteration to avoid huge files
        // Using a NullWriter to measure pure library performance, excluding Disk I/O
        try (Writer writer = new NullWriter()) {
            StreamingJsonTWriter<Order> stringifier = new StreamingJsonTWriterBuilder<Order>()
                    .registry(jsonTConfig.getAdapters())
                    .namespace(jsonTConfig.getNamespace())
                    .generator(generator)
                    .build("Order");
            stringifier.stringify(writer, recordCount, 1000, 10, false, null);
        }
    }


    @Benchmark
    public void benchmarkParseAndConvert() throws IOException {
        String dataFile = String.format("jsont-benchmark/target/marketplace_data-%d.jsont", recordCount);
        File file = new File(dataFile);
        assert file.exists();
        try (InputStream is = Files.newInputStream(file.toPath())) {
            this.jsonTConfig.source(is)
                    .convert(Order.class, 1)
                    .count()
                    .block();
        }
    }

    private static SchemaAdapter<?>[] loadAdapters() {
        return new SchemaAdapter[]{
                new OrderAdapter(),
                new OrderLineItemAdapter(),
                new CustomerAdapter(),
                new PaymentAdapter(),
                new ShippingAdapter(),
                new AddressAdapter(),
                new CategoryAdapter(),
                new CardDetailsAdapter()
        };
    }

    // Helper class to discard output
    public static class NullWriter extends Writer {
        @Override
        public void write(char[] cbuf, int off, int len) {
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    }
}
