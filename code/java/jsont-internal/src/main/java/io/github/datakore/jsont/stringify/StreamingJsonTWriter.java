package io.github.datakore.jsont.stringify;

import io.github.datakore.jsont.adapters.AdapterRegistry;
import io.github.datakore.jsont.datagen.DataGenerator;
import io.github.datakore.jsont.exception.DataException;
import io.github.datakore.jsont.exception.SchemaException;
import io.github.datakore.jsont.grammar.schema.ast.NamespaceT;
import io.github.datakore.jsont.util.StepCounter;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class StreamingJsonTWriter<T> {
    private final DataGenerator<T> generator;
    private final String dataSchema;
    private final JsonTStringify stringify;

    public StreamingJsonTWriter(String schema, DataGenerator<T> generator, NamespaceT namespace, AdapterRegistry registry) {
        this.dataSchema = schema;
        this.generator = generator;
        this.stringify = new JsonTStringify(registry, namespace);
    }

    public void stringify(Writer writer, Class<T> clazz) {
        try {
            stringify.writeSchema(clazz.getSimpleName(), writer);
        } catch (IOException e) {
            throw new SchemaException("Failed to stringify schema ");
        }
    }

    public void stringify(T data, Writer writer, boolean includeSchema) {
        stringifyTemplate(writer, includeSchema, w -> stringify.stringify(data, w));
    }

    public void stringify(List<T> data, Writer writer, boolean includeSchema) {
        stringifyTemplate(writer, includeSchema, w -> writeRecordList(w, data, false));
    }

    public Mono<Void> stringifyAsync(T data, Writer writer, boolean includeSchema) {
        return Mono.fromCallable(() -> {
            stringify(data, writer, includeSchema);
            return null;
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    public Mono<Void> stringifyAsync(List<T> data, Writer writer, boolean includeSchema) {
        return Mono.fromCallable(() -> {
            stringify(data, writer, includeSchema);
            return null;
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    public void stringify(Writer writer,
                          long totalRecords,
                          int batchSize,
                          int flushEveryNBatches,
                          boolean includeSchema,
                          Consumer<StepCounter> onBatchComplete) throws IOException {
        stringifyTemplate(writer, includeSchema, w -> {
            writeBatchedRecords(w, totalRecords, batchSize,
                    flushEveryNBatches, onBatchComplete);
        });
    }

    private void writeBatchedRecords(Writer writer,
                                     long totalRecords,
                                     int batchSize,
                                     int flushEveryNBatches,
                                     Consumer<StepCounter> onBatchComplete) throws IOException {
        long batchCount = 0;
        boolean isFirstBatch = true;

        for (long recordIndex = 0; recordIndex < totalRecords; recordIndex += batchSize) {
            // Calculate actual batch size (last batch might be smaller)
            int currentBatchSize = (int) Math.min(batchSize, totalRecords - recordIndex);

            // Generate batch
            List<T> batch = generateBatch(currentBatchSize);
            // Progress callback
            if (onBatchComplete != null) {
                onBatchComplete.accept(new StepCounter("generation", batchCount + 1));
            }

            // Write batch
            writeRecordList(writer, batch, !isFirstBatch);
            isFirstBatch = false;
            batchCount++;

            // Periodic flush to disk
            if (batchCount % flushEveryNBatches == 0) {
                writer.flush();
            }

            // Progress callback
            if (onBatchComplete != null) {
                onBatchComplete.accept(new StepCounter("stringify", batchCount));
            }
        }
    }

    /**
     * Write a list of records with proper comma separation.
     *
     * @param writer            Buffered writer
     * @param records           List of records to write
     * @param needsLeadingComma Whether to prefix with comma (not first batch)
     */
    private void writeRecordList(Writer writer, List<T> records, boolean needsLeadingComma)
            throws IOException {
        for (int i = 0; i < records.size(); i++) {
            if (needsLeadingComma || i > 0) {
                writer.write(",\n");
            }
            stringify.stringify(records.get(i), writer);
        }
    }

    /**
     * Generate a batch of records using the DataGenerator.
     */
    private List<T> generateBatch(int batchSize) {
        List<T> batch = new ArrayList<>(batchSize);
        try {
            for (int i = 0; i < batchSize; i++) {
                batch.add(generator.generate(this.dataSchema));
            }
        } catch (Exception e) {
            throw new DataException("Failed to generate batch", e);
        }
        return batch;
    }

    public Mono<Void> stringifyAsync(Writer writer,
                                     long totalRecords,
                                     int batchSize,
                                     int flushEveryNBatches,
                                     boolean includeSchema,
                                     Consumer<StepCounter> onBatchComplete) {
        return Mono.fromCallable(() -> {
            stringify(writer, totalRecords, batchSize, flushEveryNBatches,
                    includeSchema, onBatchComplete);
            return null;
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    public Mono<Void> stringifyAsync(Writer writer,
                                     long totalRecords,
                                     int batchSize,
                                     int flushEveryNBatches,
                                     boolean includeSchema) {
        return stringifyAsync(writer, totalRecords, batchSize,
                flushEveryNBatches, includeSchema, null);
    }

    private Writer ensureBuffered(Writer writer) {
        if (writer instanceof BufferedWriter) {
            return writer;
        }
        return new BufferedWriter(writer);
    }

    @FunctionalInterface
    private interface DataWriter {
        void writeData(Writer writer) throws IOException;
    }

    private void stringifyTemplate(Writer writer, boolean includeSchema, DataWriter dataWriter) {
        Writer bufferedWriter = ensureBuffered(writer);

        try {
            // Step 1: Write schema (optional)
            if (includeSchema) {
                stringify.writeSchema(this.dataSchema, bufferedWriter);
            }

            // Step 2: Write data block prefix
            stringify.writeDataBlockPrefix(this.dataSchema, bufferedWriter);

            // Step 3: Write actual data (delegated to caller)
            dataWriter.writeData(bufferedWriter);

            // Step 4: Write data block suffix
            stringify.writeDataBlockSuffix(bufferedWriter);

            // Step 5: Final flush
            bufferedWriter.flush();
        } catch (Exception e) {
            throw new DataException("Failed to stringify data", e);
        }
    }
}
