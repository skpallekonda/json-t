package io.github.datakore.jsont.internal.validate;

import io.github.datakore.jsont.diagnostic.DiagnosticEvent;
import io.github.datakore.jsont.diagnostic.DiagnosticEventKind;
import io.github.datakore.jsont.model.FieldConstraints;
import io.github.datakore.jsont.model.JsonTField;
import io.github.datakore.jsont.model.JsonTValue;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class ConstraintChecker {

    public static List<DiagnosticEvent> checkField(JsonTField field, JsonTValue value, int rowIndex) {
        List<DiagnosticEvent> events = new ArrayList<>();

        // 1. Determine required
        boolean required = !field.optional();
        FieldConstraints c = field.constraints();
        if (c.required()) {
            required = true;
        }

        // 2. Check absent
        boolean absent = value instanceof JsonTValue.Null;

        // 3. Required but absent
        if (required && absent) {
            events.add(DiagnosticEvent.fatal(
                    new DiagnosticEventKind.RequiredFieldMissing(field.name()))
                    .atRow(rowIndex));
            return events;
        }

        // 4. Optional and absent — nothing to check
        if (absent) {
            return events;
        }

        // 4b. Encrypted values are opaque until decrypted — skip all value constraints.
        // The required/absent check above still applies (Encrypted counts as present).
        if (value instanceof JsonTValue.Encrypted) {
            return events;
        }

        // 4a. constantValue — Fatal if the row value doesn't equal the declared constant
        if (c.constantValue() != null && !value.equals(c.constantValue())) {
            events.add(DiagnosticEvent.fatal(
                    new DiagnosticEventKind.ConstraintViolation(
                            field.name(),
                            "constant = " + c.constantValue(),
                            "value " + describeValue(value)
                                    + " does not match declared constant " + c.constantValue()))
                    .atRow(rowIndex));
            return events;
        }

        // 5. minValue
        if (c.minValue() != null && value.isNumeric()) {
            double minV = c.minValue();
            double v = value.toDouble();
            if (v < minV) {
                events.add(DiagnosticEvent.warning(
                        new DiagnosticEventKind.ConstraintViolation(
                                field.name(),
                                "minValue = " + minV,
                                "value " + v + " violates minValue = " + minV))
                        .atRow(rowIndex));
            }
        }

        // 6. maxValue
        if (c.maxValue() != null && value.isNumeric()) {
            double maxV = c.maxValue();
            double v = value.toDouble();
            if (v > maxV) {
                events.add(DiagnosticEvent.warning(
                        new DiagnosticEventKind.ConstraintViolation(
                                field.name(),
                                "maxValue = " + maxV,
                                "value " + v + " violates maxValue = " + maxV))
                        .atRow(rowIndex));
            }
        }

        // 7. minLength — applies to any string-typed variant and to arrays
        if (c.minLength() != null) {
            int minL = c.minLength();
            int len = -1;
            if (value.isStringLike()) {
                len = value.asText().length();
            } else if (value instanceof JsonTValue.Array arr) {
                len = arr.elements().size();
            }
            if (len >= 0 && len < minL) {
                events.add(DiagnosticEvent.warning(
                        new DiagnosticEventKind.ConstraintViolation(
                                field.name(),
                                "minLength = " + minL,
                                "length " + len + " violates minLength = " + minL))
                        .atRow(rowIndex));
            }
        }

        // 8. maxLength — applies to any string-typed variant and to arrays
        if (c.maxLength() != null) {
            int maxL = c.maxLength();
            int len = -1;
            if (value.isStringLike()) {
                len = value.asText().length();
            } else if (value instanceof JsonTValue.Array arr) {
                len = arr.elements().size();
            }
            if (len >= 0 && len > maxL) {
                events.add(DiagnosticEvent.warning(
                        new DiagnosticEventKind.ConstraintViolation(
                                field.name(),
                                "maxLength = " + maxL,
                                "length " + len + " violates maxLength = " + maxL))
                        .atRow(rowIndex));
            }
        }

        // 9. pattern — applies to any string-typed variant
        if (c.pattern() != null && value.isStringLike()) {
            String patternStr = c.pattern();
            String strVal = value.asText();
            try {
                Pattern p = Pattern.compile(patternStr);
                if (!p.matcher(strVal).find()) {
                    events.add(DiagnosticEvent.warning(
                            new DiagnosticEventKind.ConstraintViolation(
                                    field.name(),
                                    "pattern = " + patternStr,
                                    "value does not match pattern: " + patternStr))
                            .atRow(rowIndex));
                }
            } catch (PatternSyntaxException e) {
                events.add(DiagnosticEvent.warning(
                        new DiagnosticEventKind.ConstraintViolation(
                                field.name(),
                                "pattern = " + patternStr,
                                "invalid regex pattern: " + e.getMessage()))
                        .atRow(rowIndex));
            }
        }

        // 10. maxPrecision — skip

        // 11. Array item constraints
        if (value instanceof JsonTValue.Array arr) {
            int count = arr.elements().size();

            if (c.minItems() != null && count < c.minItems()) {
                events.add(DiagnosticEvent.warning(
                        new DiagnosticEventKind.ConstraintViolation(
                                field.name(),
                                "minItems = " + c.minItems(),
                                "array count " + count + " violates minItems = " + c.minItems()))
                        .atRow(rowIndex));
            }

            if (c.maxItems() != null && count > c.maxItems()) {
                events.add(DiagnosticEvent.warning(
                        new DiagnosticEventKind.ConstraintViolation(
                                field.name(),
                                "maxItems = " + c.maxItems(),
                                "array count " + count + " violates maxItems = " + c.maxItems()))
                        .atRow(rowIndex));
            }

            long nullCount = arr.elements().stream()
                    .filter(v -> v instanceof JsonTValue.Null)
                    .count();

            if (c.maxNullElements() != null && nullCount > c.maxNullElements()) {
                events.add(DiagnosticEvent.warning(
                        new DiagnosticEventKind.ConstraintViolation(
                                field.name(),
                                "maxNullElements = " + c.maxNullElements(),
                                "array contains " + nullCount + " null elements, violates maxNullElements = " + c.maxNullElements()))
                        .atRow(rowIndex));
            }

            if (!c.allowNullElements() && nullCount > 0) {
                events.add(DiagnosticEvent.warning(
                        new DiagnosticEventKind.ConstraintViolation(
                                field.name(),
                                "allowNullElements = false",
                                "array contains null elements"))
                        .atRow(rowIndex));
            }
        }

        return events;
    }

    public static String describeValue(JsonTValue v) {
        if (v instanceof JsonTValue.Null) return "null";
        if (v instanceof JsonTValue.Bool b) return Boolean.toString(b.value());
        if (v.isStringLike()) return "\"" + v.asText() + "\"";
        if (v instanceof JsonTValue.Array) return "[...]";
        if (v.isNumeric()) return v.toString();
        return v.toString();
    }
}
