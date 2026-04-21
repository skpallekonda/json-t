package io.github.datakore.jsont.model;

import java.util.List;
import java.util.Objects;

/**
 * A single validation rule attached to a {@link JsonTValidationBlock}.
 *
 * <p>Two variants mirror the Rust {@code JsonTRule} enum:
 * <ul>
 *   <li>{@link Expression} — a boolean expression every row must satisfy.</li>
 *   <li>{@link ConditionalRequirement} — when {@code condition} is {@code true},
 *       all listed fields must be present and non-null.</li>
 * </ul>
 *
 * <pre>{@code
 *   // Boolean expression rule
 *   JsonTRule.expression(JsonTExpression.binary(GT, fieldName("qty"), literal(i32(0))))
 *
 *   // Conditional requirement: if status == "SHIPPED" then tracking must be non-null
 *   JsonTRule.conditionalRequirement(
 *       JsonTExpression.binary(EQ, fieldName("status"), literal(text("SHIPPED"))),
 *       List.of(FieldPath.single("tracking")))
 * }</pre>
 */
public sealed interface JsonTRule
        permits JsonTRule.Expression, JsonTRule.ConditionalRequirement {

    /** A boolean expression that every row must evaluate to {@code true}. */
    record Expression(JsonTExpression expr) implements JsonTRule {
        public Expression {
            Objects.requireNonNull(expr, "expr");
        }
    }

    /**
     * When {@code condition} evaluates to {@code true}, all {@code requiredFields}
     * must be present and non-null in the row. Missing fields produce a
     * {@link io.github.datakore.jsont.diagnostic.DiagnosticEventKind.ConditionalRequirementViolation}
     * fatal event.
     */
    record ConditionalRequirement(
            JsonTExpression condition,
            List<FieldPath> requiredFields
    ) implements JsonTRule {
        public ConditionalRequirement {
            Objects.requireNonNull(condition, "condition");
            requiredFields = List.copyOf(Objects.requireNonNull(requiredFields, "requiredFields"));
        }
    }

    // ─── Static factories ─────────────────────────────────────────────────────

    static JsonTRule expression(JsonTExpression expr) {
        return new Expression(expr);
    }

    static JsonTRule conditionalRequirement(JsonTExpression condition, List<FieldPath> requiredFields) {
        return new ConditionalRequirement(condition, requiredFields);
    }
}
