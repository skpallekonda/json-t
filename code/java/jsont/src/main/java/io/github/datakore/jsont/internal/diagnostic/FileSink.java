package io.github.datakore.jsont.internal.diagnostic;

import io.github.datakore.jsont.diagnostic.DiagnosticEvent;
import io.github.datakore.jsont.diagnostic.DiagnosticSink;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;

public class FileSink implements DiagnosticSink, AutoCloseable {

    private final Writer writer;

    public FileSink(String path) throws IOException {
        this.writer = new BufferedWriter(new FileWriter(path, true));
    }

    public FileSink(Writer writer) {
        this.writer = writer;
    }

    @Override
    public void emit(DiagnosticEvent event) {
        try {
            writer.write(event.toString() + "\n");
        } catch (IOException e) {
            throw new RuntimeException("FileSink write failed", e);
        }
    }

    @Override
    public void flush() {
        try {
            writer.flush();
        } catch (IOException e) {
            throw new RuntimeException("FileSink flush failed", e);
        }
    }

    @Override
    public void close() throws Exception {
        writer.close();
    }
}
