package io.github.datakore.jsont.validate;

import io.github.datakore.jsont.diagnostic.DiagnosticEvent;
import io.github.datakore.jsont.diagnostic.DiagnosticEventKind;
import io.github.datakore.jsont.diagnostic.DiagnosticSink;
import io.github.datakore.jsont.internal.validate.ConstraintChecker;
import io.github.datakore.jsont.internal.validate.RuleChecker;
import io.github.datakore.jsont.model.FieldPath;
import io.github.datakore.jsont.model.JsonTField;
import io.github.datakore.jsont.model.JsonTRow;
import io.github.datakore.jsont.model.JsonTSchema;
import io.github.datakore.jsont.model.JsonTValidationBlock;
import io.github.datakore.jsont.model.JsonTValue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

public final class ValidationPipeline {

    private final List<JsonTField> fields;
    private final JsonTValidationBlock validation;
    private final String schemaName;
    private final List<DiagnosticSink> sinks;

    ValidationPipeline(List<JsonTField> fields,
                       JsonTValidationBlock validation,
                       String schemaName,
                       List<DiagnosticSink> sinks) {
        this.fields = List.copyOf(fields);
        this.validation = validation;
        this.schemaName = schemaName;
        this.sinks = List.copyOf(sinks);
    }

    public static ValidationPipelineBuilder builder(JsonTSchema schema) {
        return new ValidationPipelineBuilder(schema);
    }

    // ─── Main pipeline ────────────────────────────────────────────────────────

    public void validateEach(Iterable<JsonTRow> rows, Consumer<JsonTRow> onClean) {
        emit(DiagnosticEvent.info(new DiagnosticEventKind.ProcessStarted(schemaName)));

        int total = 0;
        int validCount = 0;
        int warnCount = 0;
        int invalidCount = 0;
        long startNs = System.nanoTime();

        // Build unique sets — one per uniqueKey group
        List<Set<List<String>>> uniqueSets = new ArrayList<>();
        if (validation != null) {
            for (int i = 0; i < validation.uniqueKeys().size(); i++) {
                uniqueSets.add(new HashSet<>());
            }
        }

        int idx = 0;
        for (JsonTRow row : rows) {
            total++;
            List<DiagnosticEvent> rowEvents = new ArrayList<>();

            // Shape check
            if (row.values().size() != fields.size()) {
                rowEvents.add(DiagnosticEvent.fatal(
                        new DiagnosticEventKind.ShapeMismatch(
                                "(row)",
                                fields.size() + " field(s)",
                                row.values().size() + " field(s)"))
                        .atRow(idx).withSource(schemaName));
            } else {
                // Field constraints
                for (int i = 0; i < fields.size(); i++) {
                    rowEvents.addAll(ConstraintChecker.checkField(fields.get(i), row.values().get(i), idx));
                }

                boolean hasFatal = rowEvents.stream().anyMatch(DiagnosticEvent::isFatal);

                // Rules
                if (!hasFatal && validation != null) {
                    rowEvents.addAll(RuleChecker.checkRules(fields, validation, row.values(), idx));
                }

                // Uniqueness
                hasFatal = rowEvents.stream().anyMatch(DiagnosticEvent::isFatal);
                if (!hasFatal && validation != null) {
                    for (int ui = 0; ui < validation.uniqueKeys().size(); ui++) {
                        List<FieldPath> group = validation.uniqueKeys().get(ui);
                        List<String> key = buildUniqueKey(group, fields, row.values());
                        if (!uniqueSets.get(ui).add(key)) {
                            rowEvents.add(DiagnosticEvent.fatal(
                                    new DiagnosticEventKind.UniqueViolation(key, idx))
                                    .atRow(idx).withSource(schemaName));
                        }
                    }
                }
            }

            // Count fatals and warnings
            long fatalCount = rowEvents.stream().filter(DiagnosticEvent::isFatal).count();
            long warningCount = rowEvents.stream()
                    .filter(e -> e.severity() == io.github.datakore.jsont.diagnostic.DiagnosticSeverity.WARNING)
                    .count();

            // Emit all row events
            for (DiagnosticEvent e : rowEvents) {
                emit(e);
            }

            if (fatalCount > 0) {
                invalidCount++;
                emit(DiagnosticEvent.fatal(
                        new DiagnosticEventKind.RowRejected(idx, (int) fatalCount))
                        .withSource(schemaName));
            } else {
                onClean.accept(row);
                if (warningCount > 0) {
                    warnCount++;
                    emit(DiagnosticEvent.warning(
                            new DiagnosticEventKind.RowAcceptedWithWarnings(idx, (int) warningCount))
                            .withSource(schemaName));
                } else {
                    validCount++;
                    emit(DiagnosticEvent.info(
                            new DiagnosticEventKind.RowAccepted(idx))
                            .withSource(schemaName));
                }
            }

            idx++;
        }

        long durationMs = (System.nanoTime() - startNs) / 1_000_000L;
        emit(DiagnosticEvent.info(
                new DiagnosticEventKind.ProcessCompleted(total, validCount, warnCount, invalidCount, durationMs)));
    }

    public List<JsonTRow> validateRows(Iterable<JsonTRow> rows) {
        List<JsonTRow> clean = new ArrayList<>();
        validateEach(rows, clean::add);
        return clean;
    }

    public void validateOne(JsonTRow row, Consumer<JsonTRow> onClean) {
        List<DiagnosticEvent> rowEvents = new ArrayList<>();
        int idx = (int) row.index();

        if (row.values().size() != fields.size()) {
            rowEvents.add(DiagnosticEvent.fatal(
                    new DiagnosticEventKind.ShapeMismatch(
                            "(row)",
                            fields.size() + " field(s)",
                            row.values().size() + " field(s)"))
                    .atRow(idx).withSource(schemaName));
        } else {
            for (int i = 0; i < fields.size(); i++) {
                rowEvents.addAll(ConstraintChecker.checkField(fields.get(i), row.values().get(i), idx));
            }

            boolean hasFatal = rowEvents.stream().anyMatch(DiagnosticEvent::isFatal);

            if (!hasFatal && validation != null) {
                rowEvents.addAll(RuleChecker.checkRules(fields, validation, row.values(), idx));
            }
        }

        for (DiagnosticEvent e : rowEvents) {
            emit(e);
        }

        boolean hasFatal = rowEvents.stream().anyMatch(DiagnosticEvent::isFatal);
        if (!hasFatal) {
            onClean.accept(row);
        }
    }

    public void finish() {
        for (DiagnosticSink sink : sinks) {
            sink.flush();
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private void emit(DiagnosticEvent event) {
        for (DiagnosticSink sink : sinks) {
            sink.emit(event);
        }
    }

    private List<String> buildUniqueKey(List<FieldPath> group, List<JsonTField> fieldList, List<JsonTValue> values) {
        List<String> key = new ArrayList<>();
        for (FieldPath path : group) {
            String fieldName = path.leaf();
            // Find the index of this field
            String described = "null";
            for (int i = 0; i < fieldList.size(); i++) {
                if (fieldList.get(i).name().equals(fieldName)) {
                    described = ConstraintChecker.describeValue(values.get(i));
                    break;
                }
            }
            key.add(described);
        }
        return key;
    }
}
