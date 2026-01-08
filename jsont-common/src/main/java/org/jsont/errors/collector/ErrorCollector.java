package org.jsont.errors.collector;

import org.jsont.errors.ValidationError;

import java.util.List;

public interface ErrorCollector {
    void report(ValidationError error);

    boolean hasFatalErrors();

    List<ValidationError> all();
}
