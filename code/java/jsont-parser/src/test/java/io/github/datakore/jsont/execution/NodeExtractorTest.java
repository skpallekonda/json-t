package io.github.datakore.jsont.execution;

import io.github.datakore.jsont.errors.collector.DefaultErrorCollector;
import io.github.datakore.jsont.errors.collector.ErrorCollector;
import io.github.datakore.jsont.grammar.schema.ast.NamespaceT;
import io.github.datakore.jsont.parser.SchemaCatalogVisitor;
import org.junit.jupiter.api.Test;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class NodeExtractorTest {

    @Test
    void shouldParseCatalog() throws IOException {
        Path path = Path.of("src/test/resources/ns-schema.jsont");
        try (InputStream is = new FileInputStream(path.toFile())) {
            ErrorCollector errorCollector = new DefaultErrorCollector();
            SchemaCatalogVisitor visitor = new SchemaCatalogVisitor(errorCollector);
            ParserExecutor.executeSchema(is, errorCollector, visitor);
            NamespaceT ns = visitor.getNamespaceT();
            assertEquals(0, errorCollector.all().size());
            assertFalse(errorCollector.hasFatalErrors());
            assertEquals(1, ns.getCatalogs().size());
            assertNotNull(ns.getCatalogs().get(0).getSchema("User"));
            assertNotNull(ns.getCatalogs().get(0).getSchema("Address"));
            assertNotNull(ns.getCatalogs().get(0).getEnum("Role"));
        }
    }

    @Test
    void shouldParseData() throws IOException {
        Path path = Path.of("src/test/resources/data.jsont");
        ErrorCollector errorCollector = new DefaultErrorCollector();
//        ScanS
    }
}

