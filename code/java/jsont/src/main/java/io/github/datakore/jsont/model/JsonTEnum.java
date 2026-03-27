package io.github.datakore.jsont.model;

import java.util.List;
import java.util.Objects;

/**
 * An enumeration definition within a JsonT catalog.
 *
 * <p>Enum values are ALL_CAPS identifiers (e.g. {@code ACTIVE}, {@code SUSPENDED}).
 *
 * <pre>{@code
 *   JsonTEnum status = new JsonTEnum("Status", List.of("ACTIVE", "INACTIVE", "SUSPENDED"));
 *   status.contains("ACTIVE")  // true
 * }</pre>
 */
public record JsonTEnum(String name, List<String> values) {

    public JsonTEnum {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(values, "values must not be null");
        if (name.isBlank()) throw new IllegalArgumentException("Enum name must not be blank");
        if (values.isEmpty()) throw new IllegalArgumentException("Enum '" + name + "' must have at least one value");
        values = List.copyOf(values);
    }

    /** Returns {@code true} if {@code value} is a declared constant of this enum. */
    public boolean contains(String value) {
        return values.contains(value);
    }

    @Override
    public String toString() {
        return name + ": [" + String.join(", ", values) + "]";
    }
}
