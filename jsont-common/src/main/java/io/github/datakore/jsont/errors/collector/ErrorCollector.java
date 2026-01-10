package io.github.datakore.jsont.errors.collector;

import io.github.datakore.jsont.errors.ValidationError;

import java.util.List;

public interface ErrorCollector {
    void report(ValidationError error);

    boolean hasFatalErrors();

    List<ValidationError> all();
}
