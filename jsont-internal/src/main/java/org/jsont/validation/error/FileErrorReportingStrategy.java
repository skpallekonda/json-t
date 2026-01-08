package org.jsont.validation.error;

import org.jsont.errors.ErrorReportingStrategy;
import org.jsont.errors.ValidationError;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;

public class FileErrorReportingStrategy implements ErrorReportingStrategy {

    private final BufferedWriter writer;

    public FileErrorReportingStrategy(Path outputPath) throws IOException {
        this.writer = new BufferedWriter(new FileWriter(outputPath.toFile()));
        // Write header
        writer.write("RowIndex,Key,Message,Expected,Actual");
        writer.newLine();
    }

    @Override
    public void report(ValidationError error) {
        try {
            // Simple CSV escaping
            String line = String.format("%d,%s,\"%s\",\"%s\",\"%s\"",
                    error.rowIndex(),
                    escape(error.key()),
                    escape(error.getMessage()),
                    escape(error.getExpected()),
                    escape(error.getActual()));
            writer.write(line);
            writer.newLine();
        } catch (IOException e) {
            System.err.println("Failed to write error report: " + e.getMessage());
        }
    }

    private String escape(String input) {
        if (input == null)
            return "";
        return input.replace("\"", "\"\"");
    }

    @Override
    public void close() throws Exception {
        writer.close();
    }
}
