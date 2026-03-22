package io.github.datakore.jsont.execution;

import io.github.datakore.jsont.errors.Severity;
import io.github.datakore.jsont.errors.ValidationError;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

public class CSVErrorLogger implements AutoCloseable {
    private final Sinks.Many<String> errorSink;
    private final Path outputPath;

    public CSVErrorLogger(Path outputPath) {
        this.outputPath = outputPath;

        // 1. Create a queue for errors
        this.errorSink = Sinks.many().unicast().onBackpressureBuffer();

        // 2. Start the Consumer immediately on a Background IO Thread
        this.errorSink.asFlux().publishOn(Schedulers.boundedElastic()) // <--- RUNS ON SEPARATE THREAD
                .bufferTimeout(100, java.time.Duration.ofSeconds(1)) // Optimization: Batch writes
                .subscribe(this::writeBatchToDisk,
                        err -> System.err.println("Logger Sink Error: " + err.getMessage()));
    }

    public void log(int rowIndex, List<ValidationError> errors) {
        // Format CSV line: "RowIndex, ErrorMessage"
        for (ValidationError err : errors) {
            Severity sev = err.severity();
            String key = err.key();
            String msg = err.getMessage();
            String csvLine = String.format("%d,\"%s\",\"%s\",\"%s\"\n", rowIndex, sev, key, msg);

            errorSink.tryEmitNext(csvLine);
        }
    }

    private void writeBatchToDisk(List<String> lines) {
        try (BufferedWriter writer = Files.newBufferedWriter(outputPath, StandardOpenOption.CREATE, StandardOpenOption.APPEND, StandardOpenOption.WRITE)) {

            for (String line : lines) {
                writer.write(line);
                writer.newLine();
            }
        } catch (IOException e) {
            System.err.println("Failed to write error log: " + e.getMessage());
        }
    }

    @Override
    public void close() throws Exception {
        errorSink.tryEmitComplete();
    }
}
