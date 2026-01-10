package io.github.datakore.jsont.errors;

public class ValidationError {
    private final int rowIndex;
    private final String key;
    private final String message;
    private final String expected;
    private final String actual;
    private final ErrorLocation location;
    private final Severity severity;

    public ValidationError(Severity severity, String message, ErrorLocation location) {
        this.severity = severity;
        this.message = message;
        this.location = location;
        this.rowIndex = location.row();
        this.key = location.location();
        this.expected = "";
        this.actual = "";
    }

    public ValidationError(ErrorLocation location, String key, String message, String expected,
                           String actual) {
        this.location = location;
        this.rowIndex = location.row();
        this.key = key;
        this.message = message;
        this.expected = expected;
        this.actual = actual;
        this.severity = Severity.WARNING;
    }

    public int rowIndex() {
        return location != null ? location.row() : rowIndex;
    }

    public String key() {
        return key;
    }

    public String getMessage() {
        return message;
    }

    public String getExpected() {
        return expected;
    }

    public String getActual() {
        return actual;
    }

    public Severity severity() {
        return severity;
    }

    @Override
    public String toString() {
        return String.format("Error at %s, Key '%s': %s (Expected: %s, Actual: %s)",
                location, key, message, expected, actual);
    }
}
