package io.github.datakore.jsont.diagnostic;

public interface DiagnosticSink {
    void emit(DiagnosticEvent event);
    default void flush() {}
}
