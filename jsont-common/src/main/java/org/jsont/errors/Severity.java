package org.jsont.errors;

public enum Severity {
    WARNING,
    ERROR,
    FIELD_FATAL,
    ROW_FATAL,
    FATAL;

    public boolean isFatal() {
        return this == FATAL || this == FIELD_FATAL || this == ROW_FATAL;
    }
}
