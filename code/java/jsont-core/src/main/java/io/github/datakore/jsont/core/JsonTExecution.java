package io.github.datakore.jsont.core;

import io.github.datakore.jsont.chunk.DataRowRecord;
import io.github.datakore.jsont.exception.ExecutionException;
import io.github.datakore.jsont.grammar.data.RowNode;
import io.github.datakore.jsont.pipeline.ConvertStage;
import io.github.datakore.jsont.pipeline.ParseStage;
import io.github.datakore.jsont.pipeline.ScanStage;
import io.github.datakore.jsont.pipeline.ValidateStage;
import io.github.datakore.jsont.util.ChunkContext;
import io.github.datakore.jsont.util.StepCounter;
import reactor.core.publisher.Flux;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Manages the execution of the JsonT processing pipeline.
 * <p>
 * This class provides methods to parse, validate, and convert JsonT data.
 * It is created via {@link JsonTConfig#source(Path)} or other source methods.
 * </p>
 */
public class JsonTExecution {

    private final JsonTConfig config;
    private final Supplier<InputStream> streamSupplier;
    private final ChunkContext chunkContext;
    private final Consumer<StepCounter> monitor;

    public JsonTExecution(JsonTConfig config, ChunkContext chunkContext, Path path, Consumer<StepCounter> monitor) {
        this(config, chunkContext, () -> {
            try {
                return Files.newInputStream(path);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }, monitor);
    }

    public JsonTExecution(JsonTConfig config, ChunkContext chunkContext, Supplier<InputStream> streamSupplier, Consumer<StepCounter> monitor) {
        this.config = config;
        this.streamSupplier = streamSupplier;
        this.monitor = monitor;
        this.chunkContext = chunkContext;
    }

    /**
     * Parses the JsonT data into a stream of {@link RowNode} objects.
     *
     * @param parallelism the number of parallel threads to use for parsing
     * @return a {@link Flux} of parsed {@link RowNode}s
     */
    public Flux<RowNode> parse(int parallelism) {
        return Flux.using(
                () -> new BufferedInputStream(streamSupplier.get()),
                stream -> {
                    ScanStage scanStage = new ScanStage(stream, chunkContext, monitor);
                    Flux<DataRowRecord> rawRecords = scanStage.execute(Flux.empty());

                    // 2. Parse
                    ParseStage parseStage = new ParseStage(config.errorCollector, chunkContext, monitor, parallelism);
                    return parseStage.execute(rawRecords);
                },
                stream -> {
                    try {
                        stream.close();
                    } catch (Exception e) {
                        throw new ExecutionException("Error closing the stream", e);
                    }
                }
        );
    }

    /**
     * Parses and validates the JsonT data against the schema.
     *
     * @param targetType  the target class type for validation context
     * @param parallelism the number of parallel threads to use
     * @return a {@link Flux} of validated {@link RowNode}s
     */
    public Flux<RowNode> validate(Class<?> targetType, int parallelism) {
        // 1 & 2. Scan & Parse
        Flux<RowNode> parsedRows = parse(parallelism);

        // 3. Validate
        ValidateStage validateStage = new ValidateStage(config.namespaceT, config.errorCollector, targetType, monitor, parallelism);
        return validateStage.execute(parsedRows);
    }

    /**
     * Parses, validates, and converts the JsonT data into objects of the specified type.
     *
     * @param targetType  the target class to convert the data into
     * @param parallelism the number of parallel threads to use
     * @param <T>         the type of the target class
     * @return a {@link Flux} of converted objects
     */
    public <T> Flux<T> convert(Class<T> targetType, int parallelism) {
        // 1, 2 & 3. Scan, Parse & Validate
        Flux<RowNode> validatedRows = validate(targetType, parallelism);

        // 4. Convert
        ConvertStage<T> convertStage = new ConvertStage<>(config.namespaceT, config.adapterRegistry, targetType, monitor, parallelism);
        return convertStage.execute(validatedRows);
    }
}
