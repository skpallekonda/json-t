package io.github.datakore.jsont;

import io.github.datakore.jsont.adapters.SchemaAdapter;
import io.github.datakore.jsont.adapters.marketplace.*;
import io.github.datakore.jsont.core.JsonTConfig;
import io.github.datakore.jsont.datagen.OrderDataGenerator;
import io.github.datakore.jsont.errors.collector.DefaultErrorCollector;
import io.github.datakore.jsont.errors.collector.ErrorCollector;
import io.github.datakore.jsont.marketplace.Order;
import io.github.datakore.jsont.stringify.StreamingJsonTWriter;
import io.github.datakore.jsont.stringify.StreamingJsonTWriterBuilder;
import io.github.datakore.jsont.util.ProgressMonitor;
import io.github.datakore.jsont.util.StepCounter;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.util.concurrent.atomic.AtomicLong;

public class JsonTMarketPlaceTest {
    private String schemaPath = "src/test/java/io/github/datakore/jsont/marketplace/ns-marketplace-schema.jsont";
    private ErrorCollector errorCollector;
    JsonTConfig jsonTConfig;
    private String oneOrderContent;


    @Test
    void shouldParseSchemaCorrectly() throws IOException {
        this.errorCollector = new DefaultErrorCollector();
        try (InputStream is = new FileInputStream(schemaPath)) {
            jsonTConfig = JsonT.configureBuilder().withAdapters(loadAdapters())
                    .withErrorCollector(errorCollector).source(is).build();
        }
        assert jsonTConfig != null;
        assert jsonTConfig.getNamespace() != null;
    }

    @Test
    void shouldStringifyOneOrder() throws IOException {
        shouldParseSchemaCorrectly();
        OrderDataGenerator generator = loadData(false);
        Order order = generator.generate(schemaPath);
        StringWriter writer = new StringWriter();
        StreamingJsonTWriter<Order> stringifier = createStreamingWriter(generator);
        stringifier.stringify(order, writer, true);
        this.oneOrderContent = writer.toString();
        System.out.println(this.oneOrderContent);
    }


    @Test
    void shouldStringifyMultipleOrders() throws IOException {
        shouldParseSchemaCorrectly();
        OrderDataGenerator generator = loadData(true);
        long totalRecords = 1_000_000;
        int batchSize = 1000;
        int flushEveryNBatches = 10;
        long progressWindowSize = 100;
        boolean includeSchema = false;
        String outFileBatch = String.format("%d-%d-%d", totalRecords, batchSize, progressWindowSize);
        ProgressMonitor onBatchComplete = new ProgressMonitor(totalRecords, batchSize, progressWindowSize);
        String outFileName = String.format("target/marketplace_data-%s-%d.jsont", outFileBatch, System.currentTimeMillis());
        try (FileWriter writer = new FileWriter(outFileName)) {
            StreamingJsonTWriter<Order> stringifier = createStreamingWriter(generator);
            onBatchComplete.startProgress();
            stringifier.stringify(writer, totalRecords, batchSize, flushEveryNBatches, includeSchema, onBatchComplete);
            onBatchComplete.endProgress();
            System.out.println("Filename " + outFileName);
        }
    }

    @Test
    void shouldBeAbleToParseOneOrder() throws IOException {
        shouldStringifyOneOrder();
        assert this.oneOrderContent != null;
        final AtomicLong counter = new AtomicLong();
        ProgressMonitor monitor = new ProgressMonitor(1, 1, 1);
        try (ByteArrayInputStream bais = new ByteArrayInputStream(this.oneOrderContent.getBytes())) {
            monitor.startProgress();
            jsonTConfig.source(bais).convert(Order.class, 1).doOnNext(order -> {
                monitor.accept(new StepCounter("JsonT -> Convert", counter.getAndIncrement()));
            }).blockLast();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void shouldBeAbleToParseMultipleOrders() throws IOException {
        shouldParseSchemaCorrectly();
        String dataFile = "target/marketplace_data-1000000-1000-100-1769131820243.jsont";
        long totalRecords;
        long batchSize;
        long progressWindowSize;
        String[] tokens = dataFile.split("-");
        totalRecords = Long.parseLong(tokens[1]);
        batchSize = Long.parseLong(tokens[2]);
        progressWindowSize = Long.parseLong(tokens[3]);
        ProgressMonitor monitor = new ProgressMonitor(totalRecords, batchSize, progressWindowSize, true);
        final AtomicLong counter = new AtomicLong(0);
        try (FileInputStream fis = new FileInputStream(dataFile)) {
            monitor.startProgress();
            Long counter2 = this.jsonTConfig.withMonitor(monitor).source(fis)
                    .convert(Order.class, 1)
                    .doOnNext(order -> {
                        counter.incrementAndGet();
                    }).count().block();
            monitor.endProgress();
            assert counter.get() == counter2;
        }
    }

    private StreamingJsonTWriter<Order> createStreamingWriter(OrderDataGenerator generator) {
        return new StreamingJsonTWriterBuilder<Order>()
                .registry(jsonTConfig.getAdapters())
                .namespace(jsonTConfig.getNamespace())
                .generator(generator)
                .build("Order");
    }

    private OrderDataGenerator loadData(boolean initialize) {
        OrderDataGenerator generator = new OrderDataGenerator();
        if (initialize) {
            generator.initialize();
        }
        return generator;
    }

    private SchemaAdapter<?>[] loadAdapters() {
        OrderAdapter order = new OrderAdapter();
        OrderLineItemAdapter lineItems = new OrderLineItemAdapter();
        CustomerAdapter customer = new CustomerAdapter();
        PaymentAdapter payment = new PaymentAdapter();
        ShippingAdapter shipping = new ShippingAdapter();
        io.github.datakore.jsont.adapters.marketplace.AddressAdapter address = new io.github.datakore.jsont.adapters.marketplace.AddressAdapter();
        CategoryAdapter category = new CategoryAdapter();
        CardDetailsAdapter cardDetails = new CardDetailsAdapter();
        return new SchemaAdapter[]{order, lineItems, customer, payment, shipping, address, category, cardDetails};
    }
}
