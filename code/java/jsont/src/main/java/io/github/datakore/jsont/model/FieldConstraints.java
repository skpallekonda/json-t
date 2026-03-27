package io.github.datakore.jsont.model;

/**
 * Constraint set for a single field, built by {@code JsonTFieldBuilder}.
 * All fields are nullable — {@code null} means no constraint.
 */
public record FieldConstraints(
        Double minValue,
        Double maxValue,
        Integer minLength,
        Integer maxLength,
        String pattern,
        boolean required,
        Integer maxPrecision,
        Integer minItems,
        Integer maxItems,
        boolean allowNullElements,
        Integer maxNullElements,
        JsonTValue constantValue
) {
    /** A fully-unconstrained sentinel (all nulls / false). */
    public static final FieldConstraints NONE = new FieldConstraints(
            null, null, null, null, null, false, null, null, null, false, null, null
    );

    /** Returns {@code true} if at least one constraint is active. */
    public boolean hasAny() {
        return minValue != null || maxValue != null
                || minLength != null || maxLength != null
                || pattern != null
                || required
                || maxPrecision != null
                || minItems != null || maxItems != null
                || allowNullElements
                || maxNullElements != null
                || constantValue != null;
    }
}
