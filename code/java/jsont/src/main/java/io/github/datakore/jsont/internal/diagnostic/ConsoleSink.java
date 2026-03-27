package io.github.datakore.jsont.internal.diagnostic;

import io.github.datakore.jsont.diagnostic.DiagnosticEvent;
import io.github.datakore.jsont.diagnostic.DiagnosticSink;

public class ConsoleSink implements DiagnosticSink {

    @Override
    public void emit(DiagnosticEvent event) {
        System.err.println(event.toString());
    }

    @Override
    public void flush() {
        // no-op
    }
}
