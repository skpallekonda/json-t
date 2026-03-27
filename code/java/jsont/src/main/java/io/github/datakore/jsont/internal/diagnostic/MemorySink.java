package io.github.datakore.jsont.internal.diagnostic;

import io.github.datakore.jsont.diagnostic.DiagnosticEvent;
import io.github.datakore.jsont.diagnostic.DiagnosticSeverity;
import io.github.datakore.jsont.diagnostic.DiagnosticSink;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class MemorySink implements DiagnosticSink {

    private final List<DiagnosticEvent> events = new ArrayList<>();

    @Override
    public void emit(DiagnosticEvent event) {
        events.add(event);
    }

    @Override
    public void flush() {
        // no-op
    }

    public List<DiagnosticEvent> events() {
        return Collections.unmodifiableList(events);
    }

    public List<DiagnosticEvent> fatalEvents() {
        return events.stream()
                .filter(DiagnosticEvent::isFatal)
                .collect(Collectors.toList());
    }

    public List<DiagnosticEvent> warningEvents() {
        return events.stream()
                .filter(e -> e.severity() == DiagnosticSeverity.WARNING)
                .collect(Collectors.toList());
    }

    public void clear() {
        events.clear();
    }

    public int size() {
        return events.size();
    }
}
