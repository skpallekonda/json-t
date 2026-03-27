package io.github.datakore.jsont.builder;

import io.github.datakore.jsont.error.BuildError;
import io.github.datakore.jsont.model.FieldPath;
import io.github.datakore.jsont.model.JsonTExpression;
import io.github.datakore.jsont.model.JsonTRule;
import io.github.datakore.jsont.model.JsonTValidationBlock;

import java.util.ArrayList;
import java.util.List;

/**
 * Fluent builder for {@link JsonTValidationBlock}.
 *
 * <pre>{@code
 *   JsonTValidationBlockBuilder.create()
 *       .unique(FieldPath.single("id"))
 *       .unique(FieldPath.single("email"))
 *       .rule(JsonTExpression.binary(GT,
 *                 JsonTExpression.fieldName("price"),
 *                 JsonTExpression.literal(JsonTValue.d64(0.0))))
 * }</pre>
 */
public final class JsonTValidationBlockBuilder {

    private final List<List<FieldPath>> uniqueKeys = new ArrayList<>();
    private final List<JsonTRule> rules = new ArrayList<>();

    private JsonTValidationBlockBuilder() {}

    /** Returns a new, empty builder. */
    public static JsonTValidationBlockBuilder create() {
        return new JsonTValidationBlockBuilder();
    }

    // ─── Uniqueness constraints ───────────────────────────────────────────────

    /**
     * Adds a compound uniqueness key: the combination of all listed field paths
     * must be distinct across all rows in the dataset.
     *
     * <p>For a single-field uniqueness constraint, pass one {@link FieldPath}.
     * For a compound key, pass multiple paths.
     *
     * @param paths one or more field paths that form the key
     */
    public JsonTValidationBlockBuilder unique(List<FieldPath> paths) {
        if (paths == null || paths.isEmpty())
            throw new IllegalArgumentException("unique() requires at least one FieldPath");
        uniqueKeys.add(List.copyOf(paths));
        return this;
    }

    /** Varargs convenience overload for {@link #unique(List)}. */
    public JsonTValidationBlockBuilder unique(FieldPath... paths) {
        return unique(List.of(paths));
    }

    /**
     * Shorthand for a single-field uniqueness constraint.
     *
     * <pre>{@code .unique("id") }</pre>
     */
    public JsonTValidationBlockBuilder unique(String fieldName) {
        return unique(FieldPath.single(fieldName));
    }

    // ─── Boolean rule constraints ─────────────────────────────────────────────

    /**
     * Adds a row-level rule expression. Every row must evaluate this expression
     * to {@code Bool(true)}; rows that fail are reported as validation errors.
     *
     * @param expr a boolean-returning expression
     */
    public JsonTValidationBlockBuilder rule(JsonTExpression expr) {
        if (expr == null) throw new IllegalArgumentException("rule expression must not be null");
        rules.add(JsonTRule.expression(expr));
        return this;
    }

    /**
     * Adds a conditional requirement: when {@code condition} is {@code true}, all
     * {@code requiredFields} must be present and non-null.
     */
    public JsonTValidationBlockBuilder conditionalRule(JsonTExpression condition, List<FieldPath> requiredFields) {
        if (condition == null) throw new IllegalArgumentException("condition must not be null");
        if (requiredFields == null || requiredFields.isEmpty())
            throw new IllegalArgumentException("requiredFields must not be empty");
        rules.add(JsonTRule.conditionalRequirement(condition, requiredFields));
        return this;
    }

    /** Varargs overload for {@link #conditionalRule(JsonTExpression, List)}. */
    public JsonTValidationBlockBuilder conditionalRule(JsonTExpression condition, FieldPath... requiredFields) {
        return conditionalRule(condition, List.of(requiredFields));
    }

    // ─── Build ────────────────────────────────────────────────────────────────

    /**
     * Validates state and returns an immutable {@link JsonTValidationBlock}.
     *
     * <p>Called internally by {@link JsonTSchemaBuilder#validationFrom}.
     *
     * @throws BuildError if neither unique keys nor rules have been added
     */
    JsonTValidationBlock build() throws BuildError {
        if (uniqueKeys.isEmpty() && rules.isEmpty())
            throw new BuildError("Validation block must declare at least one unique key or rule");
        return new JsonTValidationBlock(uniqueKeys, rules);
    }
}
