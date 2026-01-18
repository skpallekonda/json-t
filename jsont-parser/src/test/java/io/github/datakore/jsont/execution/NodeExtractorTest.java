package io.github.datakore.jsont.execution;

import io.github.datakore.jsont.errors.ValidationError;
import io.github.datakore.jsont.errors.collector.DefaultErrorCollector;
import io.github.datakore.jsont.errors.collector.ErrorCollector;
import io.github.datakore.jsont.grammar.data.RowNode;
import io.github.datakore.jsont.grammar.schema.ast.NamespaceT;
import io.github.datakore.jsont.parser.DataRowVisitor;
import io.github.datakore.jsont.parser.SchemaCatalogVisitor;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

public class NodeExtractorTest {

    @Test
    void shouldParseCatalog() throws IOException {
        CharStream schemaStream = CharStreams.fromPath(Path.of("src/test/resources/ns-schema.jsont"));
        ErrorCollector errorCollector = new DefaultErrorCollector();
        SchemaCatalogVisitor visitor = new SchemaCatalogVisitor(errorCollector);
        ParserExecutor.executeSchema(schemaStream, errorCollector, visitor);
        NamespaceT ns = visitor.getNamespaceT();
        assertEquals(0, errorCollector.all().size());
        assertFalse(errorCollector.hasFatalErrors());
        assertEquals(1, ns.getCatalogs().size());
        assertNotNull(ns.getCatalogs().get(0).getSchema("User"));
        assertNotNull(ns.getCatalogs().get(0).getSchema("Address"));
        assertNotNull(ns.getCatalogs().get(0).getEnum("Role"));
    }

    @Test
    void shouldParseData() throws IOException {
        CharStream schemaStream = CharStreams.fromPath(Path.of("src/test/resources/data.jsont"));
        ErrorCollector errorCollector = new DefaultErrorCollector();
        final AtomicInteger rowsReceived = new AtomicInteger();
        final AtomicInteger errorsReceived = new AtomicInteger();
        DataStream dataStream = new DataStream() {
            @Override
            public void onRowParsed(RowNode row) {
                rowsReceived.incrementAndGet();
            }

            @Override
            public void onEOF() {

            }

            @Override
            public Flux<RowNode> rows() {
                return null;
            }

            @Override
            public void onRowError(int rowIndex, List<ValidationError> errors) {
                errorsReceived.incrementAndGet();
            }
        };
        DataRowVisitor visitor = new DataRowVisitor(errorCollector, null, dataStream);
        ParserExecutor.executeDataParse(schemaStream, errorCollector, visitor);
        NamespaceT ns = visitor.getNamespaceT();
        assertEquals(0, errorCollector.all().size());
        assertFalse(errorCollector.hasFatalErrors());
        assertEquals(1, ns.getCatalogs().size());
        assertNotNull(ns.getCatalogs().get(0).getSchema("User"));
        assertNotNull(ns.getCatalogs().get(0).getSchema("Address"));
        assertNotNull(ns.getCatalogs().get(0).getEnum("Role"));
    }
}

