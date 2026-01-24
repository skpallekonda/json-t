package io.github.datakore.marketplace;

import io.github.datakore.jsont.JsonT;
import io.github.datakore.jsont.adapters.SchemaAdapter;
import io.github.datakore.jsont.core.JsonTConfig;
import io.github.datakore.jsont.errors.collector.DefaultErrorCollector;
import io.github.datakore.jsont.stringify.StreamingJsonTWriter;
import io.github.datakore.jsont.stringify.StreamingJsonTWriterBuilder;
import io.github.datakore.jsont.util.ProgressMonitor;
import io.github.datakore.marketplace.adapters.*;
import io.github.datakore.marketplace.datagen.OrderDataGenerator;
import io.github.datakore.marketplace.entity.Order;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;

public class StringifyUtil {

    private final DefaultErrorCollector errorCollector;
    private final String schemaPath;
    private final OrderDataGenerator generator;
    private JsonTConfig jsonTConfig;

    public StringifyUtil() throws IOException {
        this.errorCollector = new DefaultErrorCollector();
        this.schemaPath = "jsont-benchmark/src/main/java/io/github/datakore/marketplace/entity/ns-marketplace-schema.jsont";
        readSchemaFile();
        generator = new OrderDataGenerator();
        generator.initialize();
    }

    public void setupTestFileFor(long recordCount) throws IOException {
        int batchSize = 1000;
        int flushEveryNBatches = 10;
        long progressWindowSize = Math.min(50, recordCount / (batchSize + flushEveryNBatches));
        boolean includeSchema = false;
        String outFileBatch = String.format("%d", recordCount);
        ProgressMonitor onBatchComplete = new ProgressMonitor(recordCount, batchSize, progressWindowSize);
        String outFileName = String.format("jsont-benchmark/target/marketplace_data-%s.jsont", outFileBatch);
        File outFile = new File(outFileName);
        System.out.println("Writing to " + outFile.getAbsolutePath());
        try (FileWriter writer = new FileWriter(outFile)) {
            StreamingJsonTWriter<Order> stringifier = createStreamingWriter();
            onBatchComplete.startProgress();
            stringifier.stringify(writer, recordCount, batchSize, flushEveryNBatches, includeSchema, onBatchComplete);
            onBatchComplete.endProgress();
            System.out.println("Filename " + outFileName);
        }
    }

    private StreamingJsonTWriter<Order> createStreamingWriter() {
        return new StreamingJsonTWriterBuilder<Order>()
                .registry(jsonTConfig.getAdapters())
                .namespace(jsonTConfig.getNamespace())
                .generator(generator)
                .build("Order");
    }

    private void readSchemaFile() throws IOException {
        File schemaFile = new File(schemaPath);
        System.out.println("Schema file: " + schemaFile.getAbsolutePath());
        assert schemaFile.exists();
        try (InputStream is = Files.newInputStream(Paths.get(schemaPath))) {
            jsonTConfig = JsonT.configureBuilder().withAdapters(loadAdapters())
                    .withErrorCollector(errorCollector).source(is).build();
        }
        assert jsonTConfig != null;
        assert jsonTConfig.getNamespace() != null;
    }

    private SchemaAdapter<?>[] loadAdapters() {
        OrderAdapter order = new OrderAdapter();
        OrderLineItemAdapter lineItems = new OrderLineItemAdapter();
        CustomerAdapter customer = new CustomerAdapter();
        PaymentAdapter payment = new PaymentAdapter();
        ShippingAdapter shipping = new ShippingAdapter();
        AddressAdapter address = new AddressAdapter();
        CategoryAdapter category = new CategoryAdapter();
        CardDetailsAdapter cardDetails = new CardDetailsAdapter();
        return new SchemaAdapter[]{order, lineItems, customer, payment, shipping, address, category, cardDetails};
    }
}
