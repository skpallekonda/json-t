package io.github.datakore.jsont.model;

import java.util.List;
import java.util.Objects;

/**
 * Validation rules attached to a straight schema.
 *
 * <p>Contains two optional constraint categories:
 * <ul>
 *   <li><b>Unique keys</b> — each entry is a group of field paths whose combined
 *       values must be distinct across all rows in a dataset.</li>
 *   <li><b>Rules</b> — {@link JsonTRule} entries (boolean expressions or conditional
 *       requirements) that every row must satisfy.</li>
 * </ul>
 *
 * <pre>{@code
 *   // Build via JsonTValidationBlockBuilder
 *   JsonTValidationBlock vb = JsonTValidationBlockBuilder.create()
 *       .unique(FieldPath.single("id"))
 *       .rule(JsonTExpression.binary(GT, fieldName("price"), literal(d64(0.0))))
 *       .conditionalRule(
 *           JsonTExpression.binary(EQ, fieldName("status"), literal(text("SHIPPED"))),
 *           FieldPath.single("tracking"))
 *       .build();
 * }</pre>
 */
public record JsonTValidationBlock(
        List<List<FieldPath>> uniqueKeys,
        List<JsonTRule> rules
) {
    public JsonTValidationBlock {
        Objects.requireNonNull(uniqueKeys, "uniqueKeys must not be null");
        Objects.requireNonNull(rules, "rules must not be null");
        // deep-copy outer list; inner lists already immutable
        uniqueKeys = List.copyOf(uniqueKeys.stream().map(List::copyOf).toList());
        rules = List.copyOf(rules);
    }

    /** Returns {@code true} if this block specifies no uniqueness or rule constraints. */
    public boolean isEmpty() {
        return uniqueKeys.isEmpty() && rules.isEmpty();
    }
}
