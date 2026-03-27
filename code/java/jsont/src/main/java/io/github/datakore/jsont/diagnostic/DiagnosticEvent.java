package io.github.datakore.jsont.diagnostic;

import java.util.Optional;

public final class DiagnosticEvent {

    private final DiagnosticSeverity severity;
    private final DiagnosticEventKind kind;
    private final Integer rowIndex;
    private final String source;

    private DiagnosticEvent(DiagnosticSeverity severity, DiagnosticEventKind kind, Integer rowIndex, String source) {
        this.severity = severity;
        this.kind = kind;
        this.rowIndex = rowIndex;
        this.source = source;
    }

    // ─── Static factories ─────────────────────────────────────────────────────

    public static DiagnosticEvent fatal(DiagnosticEventKind kind) {
        return new DiagnosticEvent(DiagnosticSeverity.FATAL, kind, null, null);
    }

    public static DiagnosticEvent warning(DiagnosticEventKind kind) {
        return new DiagnosticEvent(DiagnosticSeverity.WARNING, kind, null, null);
    }

    public static DiagnosticEvent info(DiagnosticEventKind kind) {
        return new DiagnosticEvent(DiagnosticSeverity.INFO, kind, null, null);
    }

    // ─── Wither methods ───────────────────────────────────────────────────────

    public DiagnosticEvent atRow(int row) {
        return new DiagnosticEvent(severity, kind, row, source);
    }

    public DiagnosticEvent withSource(String src) {
        return new DiagnosticEvent(severity, kind, rowIndex, src);
    }

    // ─── Accessors ────────────────────────────────────────────────────────────

    public DiagnosticSeverity severity() { return severity; }

    public DiagnosticEventKind kind() { return kind; }

    public Optional<Integer> rowIndex() { return Optional.ofNullable(rowIndex); }

    public Optional<String> source() { return Optional.ofNullable(source); }

    public boolean isFatal() { return severity == DiagnosticSeverity.FATAL; }

    // ─── toString ─────────────────────────────────────────────────────────────

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(severity.name()).append("]");
        if (rowIndex != null) {
            sb.append(" [row ").append(rowIndex).append("]");
        }
        if (source != null) {
            sb.append(" (").append(source).append(")");
        }
        sb.append(" ").append(formatKind(kind));
        return sb.toString();
    }

    private static String formatKind(DiagnosticEventKind k) {
        if (k instanceof DiagnosticEventKind.TypeMismatch e) {
            return "TypeMismatch on '" + e.field() + "': expected " + e.expected() + ", got " + e.actual();
        }
        if (k instanceof DiagnosticEventKind.ShapeMismatch e) {
            return "ShapeMismatch on '" + e.field() + "': expected " + e.expected() + ", got " + e.actual();
        }
        if (k instanceof DiagnosticEventKind.RequiredFieldMissing e) {
            return "RequiredFieldMissing: '" + e.field() + "'";
        }
        if (k instanceof DiagnosticEventKind.ConstraintViolation e) {
            return "ConstraintViolation on '" + e.field() + "' [" + e.constraint() + "]: " + e.reason();
        }
        if (k instanceof DiagnosticEventKind.RuleViolation e) {
            return "RuleViolation [" + e.rule() + "]: " + e.reason();
        }
        if (k instanceof DiagnosticEventKind.ConditionalRequirementViolation e) {
            return "ConditionalRequirementViolation when '" + e.condition() + "': missing " + e.missingFields();
        }
        if (k instanceof DiagnosticEventKind.UniqueViolation e) {
            return "UniqueViolation at row " + e.rowIndex() + ": fields " + e.fields();
        }
        if (k instanceof DiagnosticEventKind.ParseFailure e) {
            return "ParseFailure: " + e.reason();
        }
        if (k instanceof DiagnosticEventKind.ProcessStarted e) {
            return "ProcessStarted: " + e.source();
        }
        if (k instanceof DiagnosticEventKind.RowAccepted e) {
            return "RowAccepted: row " + e.rowIndex();
        }
        if (k instanceof DiagnosticEventKind.RowRejected e) {
            return "RowRejected: row " + e.rowIndex() + " (" + e.fatalCount() + " fatal issue(s))";
        }
        if (k instanceof DiagnosticEventKind.RowAcceptedWithWarnings e) {
            return "RowAcceptedWithWarnings: row " + e.rowIndex() + " (" + e.warningCount() + " warning(s))";
        }
        if (k instanceof DiagnosticEventKind.ProcessCompleted e) {
            return "ProcessCompleted: total=" + e.totalRows()
                    + " valid=" + e.validRows()
                    + " warnings=" + e.warningRows()
                    + " invalid=" + e.invalidRows()
                    + " duration=" + e.durationMs() + "ms";
        }
        if (k instanceof DiagnosticEventKind.Notice e) {
            return "Notice: " + e.message();
        }
        return k.toString();
    }
}
