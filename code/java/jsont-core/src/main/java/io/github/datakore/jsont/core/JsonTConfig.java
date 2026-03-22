package io.github.datakore.jsont.core;

import io.github.datakore.jsont.adapters.AdapterRegistry;
import io.github.datakore.jsont.chunk.AnalysisResult;
import io.github.datakore.jsont.errors.collector.ErrorCollector;
import io.github.datakore.jsont.exception.SchemaException;
import io.github.datakore.jsont.execution.ParserExecutor;
import io.github.datakore.jsont.file.JsonTStructureAnalyzer;
import io.github.datakore.jsont.grammar.schema.ast.NamespaceT;
import io.github.datakore.jsont.grammar.schema.ast.SchemaModel;
import io.github.datakore.jsont.stringify.StreamingJsonTWriter;
import io.github.datakore.jsont.stringify.StreamingJsonTWriterBuilder;
import io.github.datakore.jsont.util.ChunkContext;
import io.github.datakore.jsont.util.StepCounter;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.nio.channels.Channels;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Represents the configuration for a JsonT instance.
 * <p>
 * This class holds the schema, adapters, and other settings required for parsing and stringifying JsonT data.
 * It is created using a {@link JsonTConfigBuilder}.
 * </p>
 */
public final class JsonTConfig {
    final NamespaceT namespaceT;
    ChunkContext context;
    final ErrorCollector errorCollector;
    final AdapterRegistry adapterRegistry;
    final int bufferSize;
    final Path errorFile;
    private Consumer<StepCounter> monitor;


    public JsonTConfig(ChunkContext context, ErrorCollector errorCollector, AdapterRegistry adapterRegistry, int bufferSize, Path errorFile) {
        this.context = context;
        this.namespaceT = context != null ? context.getNamespace() : null;
        this.errorCollector = errorCollector;
        this.adapterRegistry = adapterRegistry;
        this.bufferSize = bufferSize;
        this.errorFile = errorFile;
    }

    public JsonTConfig withMonitor(Consumer<StepCounter> monitor) {
        this.monitor = monitor;
        return this;
    }

    /**
     * Specifies the source data file to be processed, along with a specific chunk context.
     *
     * @param path    the path to the JsonT data file
     * @param context the chunk context to use for processing
     * @return a {@link JsonTExecution} instance for the specified source
     * @throws IOException if an I/O error occurs
     */
    public JsonTExecution source(Path path, ChunkContext context) throws IOException {
        this.context = context;
        return source(path);
    }

    /**
     * Specifies the source data file to be processed.
     *
     * @param path the path to the JsonT data file
     * @return a {@link JsonTExecution} instance for the specified source
     * @throws IOException if an I/O error occurs
     */
    public JsonTExecution source(Path path) throws IOException {
        Objects.requireNonNull(path, "Source cannot be null");
        JsonTStructureAnalyzer analyzer = new JsonTStructureAnalyzer();
        AnalysisResult result = analyzer.analyze(path);
        return createExecution(result, path);
    }

    /**
     * Specifies the source data as an {@link InputStream}.
     * <p>
     * <b>Warning:</b> This method is memory-intensive as it loads the complete stream into a byte array in memory.
     * It should only be used for smaller, in-memory payloads. For large files, use {@link #source(Path)} instead.
     * </p>
     *
     * @param inputStream the input stream containing the JsonT data
     * @return a {@link JsonTExecution} instance for the specified source
     * @throws IOException if an I/O error occurs
     */
    public JsonTExecution source(InputStream inputStream) throws IOException {
        Objects.requireNonNull(inputStream, "Source cannot be null");
        JsonTStructureAnalyzer analyzer = new JsonTStructureAnalyzer();

        // Read the entire stream into memory. This is necessary because we need to:
        // 1. Analyze the structure (which might require random access or multiple passes)
        // 2. Provide a fresh stream to the execution engine that starts from the beginning.
        byte[] bytes = JsonTStructureAnalyzer.readAllBytes(inputStream);

        // Use a ByteArrayInputStream for analysis
        AnalysisResult result = analyzer.analyze(new ByteArrayInputStream(bytes));

        // Provide a supplier that creates new streams from the byte array
        return createExecution(result, () -> new ByteArrayInputStream(bytes));
    }

    /**
     * Specifies the source data as a {@link SeekableByteChannel}.
     *
     * @param channel the seekable byte channel containing the JsonT data
     * @return a {@link JsonTExecution} instance for the specified source
     * @throws IOException if an I/O error occurs
     */
    public JsonTExecution source(SeekableByteChannel channel) throws IOException {
        Objects.requireNonNull(channel, "Source cannot be null");
        JsonTStructureAnalyzer analyzer = new JsonTStructureAnalyzer();
        channel.position(0); // Ensure we are at the beginning
        AnalysisResult result = analyzer.analyze(channel);
        channel.position(0); // Reset to the beginning for the execution stage
        return createExecution(result, () -> Channels.newInputStream(channel));
    }

    private JsonTExecution createExecution(AnalysisResult result, Path path) throws IOException {
        return createExecution(result, path, null);
    }

    private JsonTExecution createExecution(AnalysisResult result, Supplier<InputStream> streamSupplier) throws IOException {
        return createExecution(result, null, streamSupplier);
    }

    private JsonTExecution createExecution(AnalysisResult result, Path path, Supplier<InputStream> streamSupplier) throws IOException {
        ChunkContext _context = this.context;
        if (result.getVariant() == AnalysisResult.FileVariant.FRAGMENT) {
            assert _context != null;
            assert _context.getNamespace() != null;
            assert _context.getDataSchema() != null;
        } else if (result.getVariant() == AnalysisResult.FileVariant.DATA_BLOCK) {
            assert _context != null;
            assert _context.getNamespace() != null;
            SchemaModel sm = _context.getNamespace().findSchema(result.getDataSchemaName());
            assert sm != null;
            _context = new ChunkContext(_context.getNamespace(), sm, result.getDataStartOffset());
        } else if (result.getVariant() == AnalysisResult.FileVariant.FULL_DOCUMENT) {
            NamespaceT ns = ParserExecutor.validateSchema(result, errorCollector);
            _context = ParserExecutor.validateDataSchema(result, ns);
        } else {
            throw new SchemaException("Data is not provided in the input file");
        }

        if (path != null) {
            return new JsonTExecution(this, _context, path, this.monitor);
        } else {
            return new JsonTExecution(this, _context, streamSupplier, this.monitor);
        }
    }

    /**
     * Stringifies the schema into a JsonT representation.
     *
     * @param clazz the class to stringify
     * @param <T>   the type of the class
     * @return a string representation of the JsonT schema
     */
    public <T> String stringify(Class<T> clazz) {
        StreamingJsonTWriter<T> writer = new StreamingJsonTWriterBuilder<T>()
                .registry(this.adapterRegistry)
                .namespace(this.namespaceT)
                .build(clazz.getSimpleName());
        StringWriter sw = new StringWriter();
        writer.stringify(sw, clazz);
        return sw.toString();
    }

    /**
     * Gets the adapter registry associated with this configuration.
     *
     * @return the adapter registry
     */
    public AdapterRegistry getAdapters() {
        return this.adapterRegistry;
    }

    /**
     * Gets the namespace associated with this configuration.
     *
     * @return the namespace
     */
    public NamespaceT getNamespace() {
        return this.namespaceT;
    }
}
