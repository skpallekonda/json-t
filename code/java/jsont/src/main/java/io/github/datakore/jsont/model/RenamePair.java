package io.github.datakore.jsont.model;

import java.util.Objects;

/**
 * Maps an old field path to a new simple field name in a {@code rename} operation.
 *
 * <pre>{@code
 *   // rename "price" -> "amount"
 *   RenamePair.of("price", "amount")
 *
 *   // rename nested "billing.zip" -> "zipCode"
 *   new RenamePair(FieldPath.of("billing", "zip"), "zipCode")
 * }</pre>
 */
public record RenamePair(FieldPath from, String to) {

    public RenamePair {
        Objects.requireNonNull(from, "from must not be null");
        Objects.requireNonNull(to, "to must not be null");
        if (to.isBlank()) throw new IllegalArgumentException("to must not be blank");
    }

    /** Convenience factory for simple single-segment renames. */
    public static RenamePair of(String from, String to) {
        return new RenamePair(FieldPath.single(from), to);
    }
}
