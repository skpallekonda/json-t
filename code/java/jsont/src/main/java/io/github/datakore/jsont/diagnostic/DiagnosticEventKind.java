package io.github.datakore.jsont.diagnostic;

import java.util.List;

public sealed interface DiagnosticEventKind
        permits DiagnosticEventKind.TypeMismatch,
                DiagnosticEventKind.ShapeMismatch,
                DiagnosticEventKind.RequiredFieldMissing,
                DiagnosticEventKind.ConstraintViolation,
                DiagnosticEventKind.RuleViolation,
                DiagnosticEventKind.ConditionalRequirementViolation,
                DiagnosticEventKind.UniqueViolation,
                DiagnosticEventKind.ParseFailure,
                DiagnosticEventKind.ProcessStarted,
                DiagnosticEventKind.RowAccepted,
                DiagnosticEventKind.RowRejected,
                DiagnosticEventKind.RowAcceptedWithWarnings,
                DiagnosticEventKind.ProcessCompleted,
                DiagnosticEventKind.Notice {

    record TypeMismatch(String field, String expected, String actual) implements DiagnosticEventKind {}

    record ShapeMismatch(String field, String expected, String actual) implements DiagnosticEventKind {}

    record RequiredFieldMissing(String field) implements DiagnosticEventKind {}

    record ConstraintViolation(String field, String constraint, String reason) implements DiagnosticEventKind {}

    record RuleViolation(String rule, String reason) implements DiagnosticEventKind {}

    record ConditionalRequirementViolation(String condition, List<String> missingFields) implements DiagnosticEventKind {
        public ConditionalRequirementViolation {
            missingFields = List.copyOf(missingFields);
        }
    }

    record UniqueViolation(List<String> fields, int rowIndex) implements DiagnosticEventKind {
        public UniqueViolation {
            fields = List.copyOf(fields);
        }
    }

    record ParseFailure(String reason) implements DiagnosticEventKind {}

    record ProcessStarted(String source) implements DiagnosticEventKind {}

    record RowAccepted(int rowIndex) implements DiagnosticEventKind {}

    record RowRejected(int rowIndex, int fatalCount) implements DiagnosticEventKind {}

    record RowAcceptedWithWarnings(int rowIndex, int warningCount) implements DiagnosticEventKind {}

    record ProcessCompleted(int totalRows, int validRows, int warningRows, int invalidRows, long durationMs)
            implements DiagnosticEventKind {}

    record Notice(String message) implements DiagnosticEventKind {}
}
