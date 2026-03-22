package io.github.datakore.jsont.core;

import io.github.datakore.jsont.adapters.AdapterRegistry;
import io.github.datakore.jsont.adapters.SchemaAdapter;
import io.github.datakore.jsont.chunk.AnalysisResult;
import io.github.datakore.jsont.errors.collector.DefaultErrorCollector;
import io.github.datakore.jsont.errors.collector.ErrorCollector;
import io.github.datakore.jsont.exception.SchemaException;
import io.github.datakore.jsont.execution.ParserExecutor;
import io.github.datakore.jsont.file.JsonTStructureAnalyzer;
import io.github.datakore.jsont.grammar.schema.ast.NamespaceT;
import io.github.datakore.jsont.grammar.schema.ast.SchemaModel;
import io.github.datakore.jsont.parser.SchemaCatalogVisitor;
import io.github.datakore.jsont.util.ChunkContext;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;

public class JsonTConfigBuilder {
    private ErrorCollector errorCollector = new DefaultErrorCollector();
    private final AdapterRegistry adapterRegistry = new AdapterRegistry();

    private final int bufferSize = 1024;
    private Path errorFile;
    private AnalysisResult analysisResult;
    private ChunkContext context;

    public JsonTConfigBuilder withAdapters(SchemaAdapter<?> adapter) {
        adapterRegistry.register(adapter);
        return this;
    }

    public JsonTConfigBuilder withErrorCollector(ErrorCollector collector) {
        this.errorCollector = collector;
        return this;
    }

    public JsonTConfigBuilder source(Path schemaSource) {
        JsonTStructureAnalyzer analyzer = new JsonTStructureAnalyzer();
        try {
            analysisResult = analyzer.analyze(schemaSource);
            NamespaceT namespaceT = null;
            if (AnalysisResult.FileVariant.FULL_DOCUMENT == analysisResult.getVariant()
                    || AnalysisResult.FileVariant.SCHEMA_ONLY == analysisResult.getVariant()) {
                namespaceT = parseSchema(new ByteArrayInputStream(analysisResult.getNamespaceContent().getBytes(StandardCharsets.UTF_8)));
            }
            SchemaModel dataSchema = null;
            if (AnalysisResult.FileVariant.FULL_DOCUMENT == analysisResult.getVariant()) {
                if (namespaceT == null) {
                    throw new SchemaException("Input source must contain schema");
                }
                dataSchema = namespaceT.findSchema(analysisResult.getDataSchemaName());
                if (dataSchema == null) {
                    throw new SchemaException("Unknown data schema" + analysisResult.getDataSchemaName());
                }
            }
            context = new ChunkContext(namespaceT, dataSchema, analysisResult.getDataStartOffset());
            return this;
        } catch (Exception e) {
            throw new SchemaException("Input path is not a valid JsonT file", e);
        }
    }

    private NamespaceT parseSchema(InputStream codePointCharStream) {
        SchemaCatalogVisitor listener = new SchemaCatalogVisitor(errorCollector);
        ParserExecutor.executeSchema(codePointCharStream, errorCollector, listener);
        return listener.getNamespaceT();
    }

    public JsonTConfigBuilder withErrorFile(Path errorFile) {
        this.errorFile = errorFile;
        return this;
    }

    public JsonTConfig build() {
        return new JsonTConfig(context, errorCollector, adapterRegistry, bufferSize, errorFile);
    }

    public JsonTConfigBuilder withAdapters(SchemaAdapter<?>[] adapters) {
        Arrays.asList(adapters).forEach(adapter -> adapterRegistry.register(adapter));
        return this;
    }
}
