package io.github.datakore.jsont.errors;

public enum Severity {
    INFO,
    WARNING,
    ERROR,
    FIELD_FATAL,
    ROW_FATAL,
    FATAL, FIELD_ERROR;

    public boolean isFatal() {
        return this == FATAL || this == FIELD_FATAL || this == ROW_FATAL;
    }
}
