package io.github.datakore.jsont.errors.collector;

import io.github.datakore.jsont.errors.ValidationError;

import java.util.ArrayList;
import java.util.List;

public class DefaultErrorCollector implements ErrorCollector {

    private final List<ValidationError> errors = new ArrayList<>();

    @Override
    public void report(ValidationError error) {
        errors.add(error);
    }

    @Override
    public boolean hasFatalErrors() {
        return errors.stream().anyMatch(v -> v.severity().isFatal());
    }

    @Override
    public List<ValidationError> all() {
        return errors;
    }

}
