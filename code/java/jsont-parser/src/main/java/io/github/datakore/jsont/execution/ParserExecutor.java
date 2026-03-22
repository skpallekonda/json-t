package io.github.datakore.jsont.execution;

import io.github.datakore.jsont.chunk.AnalysisResult;
import io.github.datakore.jsont.errors.collector.ErrorCollector;
import io.github.datakore.jsont.exception.SchemaException;
import io.github.datakore.jsont.grammar.JsonTListener;
import io.github.datakore.jsont.grammar.JsonTParser;
import io.github.datakore.jsont.grammar.schema.ast.NamespaceT;
import io.github.datakore.jsont.grammar.schema.ast.SchemaModel;
import io.github.datakore.jsont.parser.DataRowVisitor;
import io.github.datakore.jsont.parser.SchemaCatalogVisitor;
import io.github.datakore.jsont.util.ChunkContext;
import io.github.datakore.jsont.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class ParserExecutor {
    public static void executeSchema(InputStream stream, ErrorCollector collector, SchemaCatalogVisitor listener) {
        executeParse(stream, collector, listener);
    }

    public static void executeDataParse(InputStream stream, ErrorCollector collector, DataRowVisitor listener) {
        executeParse(stream, collector, listener);
    }

    private static void executeParse(InputStream stream, ErrorCollector collector, JsonTListener listener) {
        JsonTParser parser = ParserUtil.createParser(stream, collector, listener);
        parser.jsonT();
    }

    public static void executeRowParse(InputStream stream, ErrorCollector collector, DataRowVisitor listener) {
        JsonTParser parser = ParserUtil.createParser(stream, collector, listener);
        parser.dataRow(); // Parse only a single dataRow
    }

    public static NamespaceT validateSchema(AnalysisResult result, ErrorCollector errorCollector) {
        assert result != null;
        assert !StringUtils.isBlank(result.getNamespaceContent());
        assert errorCollector != null;
        SchemaCatalogVisitor visitor = new SchemaCatalogVisitor(errorCollector);
        if (AnalysisResult.FileVariant.SCHEMA_ONLY == result.getVariant() || AnalysisResult.FileVariant.FULL_DOCUMENT == result.getVariant()) {
            try (ByteArrayInputStream bais = new ByteArrayInputStream(result.getNamespaceContent().getBytes(StandardCharsets.UTF_8))) {
                ParserExecutor.executeSchema(bais, errorCollector, visitor);
                return visitor.getNamespaceT();
            } catch (Exception e) {
                throw new SchemaException("Unable to parse namespace", e);
            }
        }
        throw new SchemaException("Unknown variant");
    }

    public static ChunkContext validateDataSchema(AnalysisResult result, NamespaceT namespace) {
        assert result != null;
        assert namespace != null;
        assert !StringUtils.isBlank(result.getDataSchemaName());
        if (AnalysisResult.FileVariant.DATA_BLOCK == result.getVariant() || AnalysisResult.FileVariant.FULL_DOCUMENT == result.getVariant()) {
            SchemaModel dataSchema = namespace.findSchema(result.getDataSchemaName());
            assert dataSchema != null;
            return new ChunkContext(namespace, dataSchema, result.getDataStartOffset());
        }
        throw new SchemaException("Unknown variant");
    }
}
