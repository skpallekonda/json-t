package io.github.datakore.jsont.model;

import java.util.List;
import java.util.Objects;

/**
 * Identifies a field, possibly nested through dot-separated segments.
 *
 * <pre>{@code
 *   FieldPath id      = FieldPath.single("id");
 *   FieldPath city    = FieldPath.of("address", "city");
 *
 *   id.leaf()         // "id"
 *   city.dotJoined()  // "address.city"
 * }</pre>
 */
public final class FieldPath {

    private final List<String> segments;

    private FieldPath(List<String> segments) {
        this.segments = List.copyOf(segments);
    }

    /** Creates a single-segment path (no nesting). */
    public static FieldPath single(String name) {
        Objects.requireNonNull(name, "name must not be null");
        if (name.isBlank()) throw new IllegalArgumentException("name must not be blank");
        return new FieldPath(List.of(name));
    }

    /** Creates a nested path from explicit segments. */
    public static FieldPath of(String first, String... rest) {
        Objects.requireNonNull(first, "first segment must not be null");
        var all = new java.util.ArrayList<String>();
        all.add(first);
        for (String s : rest) {
            Objects.requireNonNull(s, "segment must not be null");
            all.add(s);
        }
        return new FieldPath(all);
    }

    /** Creates a path by splitting a dot-separated string. */
    public static FieldPath parse(String dotted) {
        Objects.requireNonNull(dotted, "dotted must not be null");
        String[] parts = dotted.split("\\.");
        if (parts.length == 0) throw new IllegalArgumentException("Path must not be empty");
        return new FieldPath(List.of(parts));
    }

    /** Returns all path segments (immutable). */
    public List<String> segments() {
        return segments;
    }

    /** Returns the leaf (last) segment name. */
    public String leaf() {
        return segments.get(segments.size() - 1);
    }

    /** Returns true when the path has exactly one segment. */
    public boolean isSimple() {
        return segments.size() == 1;
    }

    /** Returns the dot-joined representation, e.g. {@code "address.city"}. */
    public String dotJoined() {
        return String.join(".", segments);
    }

    @Override
    public String toString() {
        return dotJoined();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FieldPath fp)) return false;
        return segments.equals(fp.segments);
    }

    @Override
    public int hashCode() {
        return segments.hashCode();
    }
}
