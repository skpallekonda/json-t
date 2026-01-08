package org.jsont.errors;

public interface ErrorReportingStrategy extends AutoCloseable {
    void report(ValidationError error);

    @Override
    default void close() throws Exception {
        // Default no-op
    }
}
