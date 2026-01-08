package org.jsont.core;

import org.antlr.v4.runtime.CharStream;
import org.jsont.JsonTType;
import org.jsont.adapters.AdapterRegistry;
import org.jsont.adapters.DefaultAdapterContext;
import org.jsont.codec.CodecRegistry;
import org.jsont.errors.collector.ErrorCollector;
import org.jsont.execution.*;
import org.jsont.extractors.DefaultValueNodeExtractor;
import org.jsont.extractors.ValueNodeExtractor;
import org.jsont.grammar.JsonTParser;
import org.jsont.grammar.data.RowNode;
import org.jsont.grammar.schema.ast.SchemaCatalog;
import org.jsont.grammar.schema.ast.SchemaModel;
import org.jsont.schema.SchemaResolver;
import org.jsont.stringify.JsonTStringify;
import org.jsont.stringify.StringifyMode;

import java.util.List;

public class JsonTContext {
    private final DataWalker dataWalker;
    private final SchemaCatalog catalog;
    private final ErrorCollector errorCollector;
    private final DefaultAdapterContext adapterContext;
    private final JsonTBuilder builder;
    private final SchemaEmitter emitter;
    private List<RowNode> dataRows;
    private SchemaModel dataSchema;

    public JsonTContext(JsonTNode node, JsonTBuilder builder, ErrorCollector errorCollector, AdapterRegistry adapters) {
        this.builder = builder;
        this.errorCollector = errorCollector;
        SchemaResolver resolver = new SchemaResolver();
        catalog = resolver.resolve(node.getSchemaNodeList(), node.getEnumDefs());
        this.emitter = new DefaultSchemaEmitter(adapters, catalog);

        ValueNodeExtractor extractor = new DefaultValueNodeExtractor();
        CodecRegistry codecRegistry = new CodecRegistry();
        ValueConverter converter = new DefaultValueConverter(codecRegistry);

        RowMapper rowMapper = new DefaultRowMapper(extractor, converter);

        adapterContext = new DefaultAdapterContext(codecRegistry, adapters, catalog);
        codecRegistry.initialize(converter, rowMapper, adapterContext);

        this.dataWalker = new DataWalker(rowMapper, adapterContext);
    }

    public <T extends JsonTType> String stringify(Class<T> clazz) {
        JsonTStringify stringifier = new JsonTStringify(this.adapterContext.adapterRegistry(), this.catalog);
        return stringifier.stringifySchema(clazz);
    }

    public <T extends JsonTType> String stringify(T object) {
        return stringify(object, StringifyMode.DATA_ONLY);
    }

    public <T extends JsonTType> String stringify(T object, StringifyMode mode) {
        return stringify(List.of(object), mode);
    }

    public <T extends JsonTType> String stringify(List<T> list) {
        return stringify(list, StringifyMode.DATA_ONLY);
    }

    public <T extends JsonTType> String stringify(List<T> list, StringifyMode mode) {
        JsonTStringify stringifier = new JsonTStringify(this.adapterContext.adapterRegistry(), this.catalog);
        return stringifier.stringifyData(list, mode);
    }

    public JsonTExecutionBuilder withData(CharStream input) {
        // 1. Create ANTLR lexer + parser
        JsonTParser parser = ParserUtil.createParser(input, this.errorCollector);

        // 2. Create DataPipeline (Reactor sink)
        DataPipeline pipeline = new DataPipeline(this.catalog);

        // 3. Create visitor
        DataRowVisitor visitor = new DataRowVisitor(pipeline);

        // 4. Walk the parse tree (push rows into pipeline)
        visitor.visitData(parser.data());

        // 5. Return execution builder
        ValueNodeExtractor extractor = new DefaultValueNodeExtractor();
        CodecRegistry codecRegistry = new CodecRegistry();
        ValueConverter valueConverter = new DefaultValueConverter(codecRegistry);
        RowMapper rowMapper = new DefaultRowMapper(extractor, valueConverter);
        codecRegistry.initialize(valueConverter, rowMapper, adapterContext);
        return new JsonTExecutionBuilder(pipeline, rowMapper, adapterContext, dataSchema);
    }

    ErrorCollector errorCollector() {
        return errorCollector;
    }

    void setDataSchema(String dataSchemaName) {
        this.dataSchema = this.catalog.getSchema(dataSchemaName);
    }

    void setData(List<RowNode> dataRows) {
        this.dataRows = dataRows;
    }
}
