package io.github.datakore.jsont.file;

import io.github.datakore.jsont.chunk.AnalysisResult;
import io.github.datakore.jsont.errors.collector.DefaultErrorCollector;
import io.github.datakore.jsont.errors.collector.ErrorCollector;
import io.github.datakore.jsont.execution.ParserExecutor;
import io.github.datakore.jsont.grammar.schema.ast.NamespaceT;
import io.github.datakore.jsont.pipeline.ParseStage;
import io.github.datakore.jsont.pipeline.ScanStage;
import io.github.datakore.jsont.util.ChunkContext;
import io.github.datakore.jsont.util.ProgressMonitor;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class JsonTStructureAnalyzerTest {
    @Test
    void analyzeFileWithNamespace() throws IOException {
        String path = "src/test/resources/test-file-with-namespace.jsont";
        JsonTStructureAnalyzer analyzer = new JsonTStructureAnalyzer();
        AnalysisResult result = analyzer.analyze(Paths.get(path));
        assertNotNull(result);
        assertEquals(AnalysisResult.FileVariant.FULL_DOCUMENT, result.getVariant());
        System.out.println(result.getNamespaceContent());
        System.out.println(result.getDataSchemaName());
    }

    @Test
    void scanAlone() throws IOException {
        String path = "C:\\Users\\sasik\\github\\data-backup\\marketplace_data-10.jsont";
        String schemaPath = "C:\\Users\\sasik\\github\\json-t\\jsont-benchmark\\src\\main\\java\\io\\github\\datakore\\marketplace\\entity\\ns-marketplace-schema.jsont";
        JsonTStructureAnalyzer analyzer = new JsonTStructureAnalyzer();
        AnalysisResult schemaResult = analyzer.analyze(Paths.get(schemaPath));
        AnalysisResult dataResult = analyzer.analyze(Paths.get(path));
        ErrorCollector errorCollector = new DefaultErrorCollector();
        NamespaceT ns = ParserExecutor.validateSchema(schemaResult, errorCollector);
        ChunkContext chunkContext = ParserExecutor.validateDataSchema(dataResult, ns);
        AtomicLong counter = new AtomicLong();
        ProgressMonitor monitor = new ProgressMonitor(10000, 2, 10, true,new StringWriter());
        monitor.startProgress();
        try (InputStream inputStream = Files.newInputStream(Paths.get(path))) {
            ScanStage stage = new ScanStage(inputStream, chunkContext, monitor);
            stage.execute(null)
                    .doOnNext(record -> counter.incrementAndGet())
                    .blockLast();
        }
        monitor.endProgress();
        assertEquals(10, counter.get());
    }

    @Test
    void scanAndParse() throws IOException {
        String path = "C:\\Users\\sasik\\github\\data-backup\\marketplace_data-%d.jsont";
        String schemaPath = "C:\\Users\\sasik\\github\\json-t\\jsont-benchmark\\src\\main\\java\\io\\github\\datakore\\marketplace\\entity\\ns-marketplace-schema.jsont";
        JsonTStructureAnalyzer analyzer = new JsonTStructureAnalyzer();
        AnalysisResult schemaResult = analyzer.analyze(Paths.get(schemaPath));
        long totalRecords = 1_000_000;
        AnalysisResult dataResult = analyzer.analyze(Paths.get(String.format(path, totalRecords)));
        ErrorCollector errorCollector = new DefaultErrorCollector();
        NamespaceT ns = ParserExecutor.validateSchema(schemaResult, errorCollector);
        ChunkContext chunkContext = ParserExecutor.validateDataSchema(dataResult, ns);
        AtomicLong counter = new AtomicLong();
        int batchSize = 10000;
        ProgressMonitor monitor = new ProgressMonitor(totalRecords, batchSize, 50, true,new StringWriter());
        monitor.startProgress();
        try (InputStream inputStream = Files.newInputStream(Paths.get(String.format(path, totalRecords)))) {
            ScanStage stage = new ScanStage(inputStream, chunkContext, monitor);
            ParseStage stage2 = new ParseStage(errorCollector, chunkContext, monitor, 4);
            stage2.execute(stage.execute(null))
                    .doOnNext(record -> {
                        counter.incrementAndGet();
//                        if (counter.get() % 200_000 == 0){
//                            System.out.println(record.values().get("orderNumber"));
//                        }
                    })
                    .blockLast();
        }
        monitor.endProgress();
        assertEquals(totalRecords, counter.get());
    }

}
