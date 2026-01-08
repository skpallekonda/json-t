package org.jsont.execution;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTree;
import org.jsont.adapters.AdapterContext;
import org.jsont.adapters.AdapterRegistry;
import org.jsont.adapters.DefaultAdapterContext;
import org.jsont.codec.CodecRegistry;
import org.jsont.errors.collector.DefaultErrorCollector;
import org.jsont.errors.collector.ErrorCollector;
import org.jsont.extractors.DefaultValueNodeExtractor;
import org.jsont.extractors.ValueNodeExtractor;
import org.jsont.grammar.JsonTLexer;
import org.jsont.grammar.JsonTParser;
import org.jsont.grammar.schema.ast.SchemaCatalog;
import org.jsont.grammar.schema.ast.SchemaModel;
import org.jsont.listener.JsonTErrorListener;
import org.jsont.schema.SchemaResolver;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class DataWalkerTest {

    private JsonTNode parseTree(String source) {
        if (!source.isEmpty() && source.charAt(0) == '\uFEFF') {
            source = source.substring(1);
        }

        CharStream input = CharStreams.fromString(source);
        ErrorCollector errorCollector = new DefaultErrorCollector();
        JsonTErrorListener errorListener = new JsonTErrorListener(errorCollector);
        JsonTLexer lexer = new JsonTLexer(input);
        CommonTokenStream tokens = new CommonTokenStream(lexer);

        tokens.fill();

        JsonTParser parser = new JsonTParser(tokens);
        lexer.removeErrorListeners();
        parser.removeErrorListeners();

        parser.addErrorListener(errorListener);
        lexer.addErrorListener(errorListener);
        ParseTree tree = parser.jsonT();

        SchemaCatalogVisitor visitor = new SchemaCatalogVisitor();
        visitor.visit(tree);
        return visitor.getJsonTDef();
    }

    private String readSource(String path) {
        try {
            String content = Files.readString(Path.of(path));
            if (!content.isEmpty() && content.charAt(0) == '\uFEFF') {
                content = content.substring(1);
            }
            return content;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void shouldWalkData() {
        JsonTNode jsonTDef = parseTree(readSource("src/test/resources/example.jsont"));
        SchemaResolver schemaResolver = new SchemaResolver();
        SchemaCatalog catalog = schemaResolver.resolve(jsonTDef.getSchemaNodeList(), jsonTDef.getEnumDefs());
        SchemaModel schemaModel = catalog.getSchema(jsonTDef.getDataSchema().getName());
        CodecRegistry codecRegistry = new CodecRegistry();
        AdapterRegistry adapterRegistry = new AdapterRegistry();
        AdapterContext adapterContext = new DefaultAdapterContext(codecRegistry, adapterRegistry, catalog);
        ValueNodeExtractor nodeExtractor = new DefaultValueNodeExtractor();
        ValueConverter dataValueBinder = new DefaultValueConverter(codecRegistry);
        RowMapper rowBinder = new DefaultRowMapper(nodeExtractor, dataValueBinder);
        codecRegistry.initialize(dataValueBinder, rowBinder, adapterContext);
        schemaModel.bindTargetClass(HashMap.class);
        DataWalker dataWalker = new DataWalker(rowBinder, adapterContext);
        List<Object> jsonDataList = dataWalker.walk(jsonTDef.getDataRows(), schemaModel);
        assertNotNull(jsonDataList);
        assertEquals(2, jsonDataList.size());
        System.out.println(jsonDataList);
    }

}
