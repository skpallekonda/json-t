package org.jsont.core;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.tree.ParseTree;
import org.jsont.adapters.AdapterRegistry;
import org.jsont.adapters.SchemaAdapter;
import org.jsont.errors.collector.ErrorCollector;
import org.jsont.execution.JsonTNode;
import org.jsont.execution.SchemaCatalogVisitor;
import org.jsont.grammar.JsonTParser;

import java.io.IOException;
import java.nio.file.Path;

public class JsonTBuilder {
    private final AdapterRegistry adapterRegistry = new AdapterRegistry();
    private ErrorCollector errorCollector;

    public JsonTBuilder withAdapter(SchemaAdapter<?> adapter) {
        return withAdapters(adapter);
    }

    public JsonTBuilder withAdapters(SchemaAdapter<?>... adapters) {
        for (SchemaAdapter<?> adapter : adapters) {
            adapterRegistry.register(adapter);
        }
        return this;
    }

    public JsonTBuilder withErrorCollector(ErrorCollector errorCollector) {
        this.errorCollector = errorCollector;
        return this;
    }

    void parseData(JsonTContext context, Path path) throws IOException {
        parseData(context, CharStreams.fromPath(path));
    }

    void parseData(JsonTContext context, CharStream input) {
        JsonTParser parser = createParser(input, context.errorCollector());

        ParseTree tree = parser.data();
        SchemaCatalogVisitor visitor = new SchemaCatalogVisitor();
        visitor.visit(tree);
        JsonTNode node = visitor.getJsonTDef();
        context.setDataSchema(node.getDataSchemaName());
        context.setData(node.getDataRows());
    }

    public JsonTContext parseCatalog(Path path) throws IOException {
        return parseCatalog(CharStreams.fromPath(path));
    }

    public JsonTContext parseCatalog(CharStream input) {
        JsonTParser parser = createParser(input, errorCollector);

        ParseTree tree = parser.catalog();

        SchemaCatalogVisitor visitor = new SchemaCatalogVisitor();
        visitor.visit(tree);
        JsonTNode node = visitor.getJsonTDef();
        return new JsonTContext(node, this, errorCollector, adapterRegistry);
    }

    private JsonTParser createParser(CharStream input, ErrorCollector errorCollector) {
        return org.jsont.execution.ParserUtil.createParser(input, errorCollector);
    }

}
