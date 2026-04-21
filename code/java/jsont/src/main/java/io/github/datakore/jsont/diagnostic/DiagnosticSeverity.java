package io.github.datakore.jsont.diagnostic;

public enum DiagnosticSeverity {
    FATAL,
    WARNING,
    INFO;

    public boolean isFatal()   { return this == FATAL; }
    public boolean isWarning() { return this == WARNING; }
    public boolean isInfo()    { return this == INFO; }
}
