package io.github.datakore.jsont.execution;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.tree.ParseTree;
import io.github.datakore.jsont.errors.collector.DefaultErrorCollector;
import io.github.datakore.jsont.errors.collector.ErrorCollector;
import io.github.datakore.jsont.grammar.JsonTParser;
import io.github.datakore.jsont.grammar.data.RowNode;
import io.github.datakore.jsont.grammar.schema.ast.SchemaModel;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class NodeExtractorTest {

    @Test
    void shouldParseCatalog() throws IOException {
        CharStream schemaStream = CharStreams.fromPath(Path.of("src/test/resources/example.jsont"));
        ErrorCollector errorCollector = new DefaultErrorCollector();
        JsonTParser parser = ParserUtil.createParser(schemaStream, errorCollector);
        SchemaCatalogVisitor visitor = new SchemaCatalogVisitor();
        JsonTNode jsonTDef = (JsonTNode) visitor.visitCatalog(parser.catalog());
        assertEquals(2, jsonTDef.getSchemaNodeList().size());
        assertEquals(1, jsonTDef.getEnumDefs().size());
    }

    @Test
    void shouldParseData() throws IOException {
        CharStream schemaStream = CharStreams.fromPath(Path.of("src/test/resources/example.jsont"));
        ErrorCollector errorCollector = new DefaultErrorCollector();
        JsonTParser parser = ParserUtil.createParser(schemaStream, errorCollector);
        TestDataStream stream = new TestDataStream();
        DataRowVisitor visitor = new DataRowVisitor(stream);
        ParseTree tree = parser.jsonT();
        visitor.visit(tree);
        assertEquals(2, stream.getRows().size());
        assertEquals("User", stream.getDataSchema().name());
    }

    class TestDataStream implements DataStream {
        List<RowNode> rows = new ArrayList<>();
        private SchemaModel schemaModel;

        public List<RowNode> getRows() {
            return rows;
        }

        @Override
        public void onRowParsed(RowNode row) {
            this.rows.add(row);
        }

        @Override
        public void onEOF() {
            System.out.println("Processed " + this.rows.size() + " records");
        }

        @Override
        public Flux<RowNode> rows() {
            return null;
        }

        @Override
        public SchemaModel getDataSchema() {
            return this.schemaModel;
        }

        @Override
        public void setDataSchema(String schema) {
            this.schemaModel = new SchemaModel(schema, List.of());
        }
    }

}
