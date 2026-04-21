package io.github.datakore.jsont.diagnostic;

public interface DiagnosticSink {
    void emit(DiagnosticEvent event);
    default void flush() {}

    /** Returns a sink that forwards every event to all of {@code sinks}. */
    static DiagnosticSink fanOut(DiagnosticSink... sinks) {
        return new DiagnosticSink() {
            @Override public void emit(DiagnosticEvent event) {
                for (DiagnosticSink s : sinks) s.emit(event);
            }
            @Override public void flush() {
                for (DiagnosticSink s : sinks) s.flush();
            }
        };
    }
}
