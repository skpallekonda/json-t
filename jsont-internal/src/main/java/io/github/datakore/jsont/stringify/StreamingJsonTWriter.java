package io.github.datakore.jsont.stringify;

import io.github.datakore.jsont.adapters.AdapterRegistry;
import io.github.datakore.jsont.datagen.DataGenerator;
import io.github.datakore.jsont.exception.DataException;
import io.github.datakore.jsont.exception.SchemaException;
import io.github.datakore.jsont.grammar.schema.ast.NamespaceT;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.io.Writer;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class StreamingJsonTWriter<T> {
    private final DataGenerator<T> generator;
    private final String dataSchema;
    private JsonTStringify stringify;

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

    public Mono<Void> stringify(T data, Writer writer, boolean includeSchema) {
        return Mono.fromRunnable(() -> {
            try {
                // emit schema
                if (includeSchema) {
                    stringify.writeSchema(this.dataSchema, writer);
                }
                // write data block prefix
                stringify.writeDataBlockPrefix(this.dataSchema, writer);
                stringify.stringify(data, writer);
                // write data block suffix
                stringify.writeDataBlockSuffix(writer);
            } catch (Exception e) {
                throw new DataException(e);
            }
        });
    }

    public Mono<Void> stringify(List<T> data, Writer writer, boolean includeSchema) {
        return Mono.fromRunnable(() -> {
            try {
                // emit schema
                if (includeSchema) {
                    stringify.writeSchema(this.dataSchema, writer);
                }
                // write data block prefix
                stringify.writeDataBlockPrefix(this.dataSchema, writer);
                commonListDataWrite(writer, data, false);
                // write data block suffix
                stringify.writeDataBlockSuffix(writer);
            } catch (Exception e) {
                throw new DataException(e);
            }
        });
    }

    private Flux<T> createDataStream(long count) {
        return Flux.<T>generate(sink -> {
            try {
                T data = generator.generate(this.dataSchema);
                sink.next(data);
            } catch (Exception e) {
                sink.error(e);
            }
        }).take(count);
    }

    public Mono<Void> writeBatch(Writer writer,
                                 long totalRecords,
                                 int batchSize,
                                 int flushEveryNBatches,
                                 boolean includeSchema) {
        AtomicBoolean isFirstBatch = new AtomicBoolean(true);
        return Mono.fromRunnable(() -> {
                    try {
                        if (includeSchema) {
                            stringify.writeSchema(this.dataSchema, writer);
                        }
                        stringify.writeDataBlockPrefix(this.dataSchema, writer);
                    } catch (IOException e) {
                        throw new RuntimeException("Failed to write JSON start", e);
                    }
                })
                .subscribeOn(Schedulers.boundedElastic())
                .thenMany(
                        createDataStream(totalRecords)
                                .buffer(batchSize)
                                .index()
                                .concatMap(tuple -> {
                                    long batchIndex = tuple.getT1();
                                    List<T> batch = tuple.getT2();

                                    return writeBatchReactive(writer, batch, isFirstBatch)
                                            .then(flushIfNeeded(writer, batchIndex, flushEveryNBatches));
                                })
                ).then(writeJsonEndAndFlush(writer));
    }

    private Mono<Void> flushIfNeeded(Writer writer, long batchIndex, int flushEveryNBatches) {
        if ((batchIndex + 1) % flushEveryNBatches == 0) {
            return Mono.fromRunnable(() -> {
                try {
                    writer.flush();
                } catch (IOException e) {
                    throw new DataException("Failed to flush writer", e);
                }
            }).subscribeOn(Schedulers.boundedElastic()).then();
        }
        return Mono.empty();
    }

    // Helper method for final flush
    private Mono<Void> writeJsonEndAndFlush(Writer writer) {
        return Mono.fromRunnable(() -> {
            try {
                stringify.writeDataBlockSuffix(writer);
                writer.flush();
            } catch (IOException e) {
                throw new DataException("Failed to write JSON end", e);
            }
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    private Mono<Void> writeBatchReactive(Writer writer, List<T> batch, AtomicBoolean isFirstBatch) {
        return Mono.fromRunnable(() -> {
            try {
                boolean needsComma = !isFirstBatch.getAndSet(false);
                commonListDataWrite(writer, batch, needsComma);
            } catch (IOException e) {
                throw new DataException("Failed to write batch", e);
            }
        });
    }

    private void commonListDataWrite(Writer writer, List<T> batch, boolean needsComma) throws IOException {
        for (int i = 0; i < batch.size(); i++) {
            if (needsComma || i > 0) {
                writer.write(",\n");
            }
            stringify.stringify(batch.get(i), writer);
        }
    }
}
