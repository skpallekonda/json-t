package io.github.datakore.jsont.model;

/**
 * Constraint set for a single field, built by {@code JsonTFieldBuilder}.
 *
 * <p>All fields are optional; {@code null} means "no constraint". The sentinel
 * {@link #NONE} represents the unconstrained state.
 *
 * <p>Constraint semantics:
 * <ul>
 *   <li>{@code minValue / maxValue} — applies to numeric scalar fields.</li>
 *   <li>{@code minLength / maxLength} — applies to string-like scalar fields.</li>
 *   <li>{@code pattern} — regex pattern for string-like fields.</li>
 *   <li>{@code required} — field must be present and non-null even when declared optional.</li>
 *   <li>{@code maxPrecision} — maximum digits after the decimal point (decimal types).</li>
 *   <li>{@code minItems / maxItems} — applies to array fields.</li>
 *   <li>{@code allowNullElements} — array elements may be null.</li>
 *   <li>{@code maxNullElements} — maximum number of null elements in an array.</li>
 * </ul>
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
