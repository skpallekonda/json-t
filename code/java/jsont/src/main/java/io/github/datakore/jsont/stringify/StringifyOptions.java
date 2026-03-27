package io.github.datakore.jsont.stringify;

/**
 * Controls how a JsonT model is serialised to text.
 *
 * <pre>{@code
 *   StringifyOptions compact = StringifyOptions.compact();
 *   StringifyOptions pretty  = StringifyOptions.pretty();
 *   StringifyOptions wider   = StringifyOptions.prettyWithIndent(4);
 * }</pre>
 */
public final class StringifyOptions {
    private final boolean pretty;
    private final int indent;

    private StringifyOptions(boolean pretty, int indent) {
        if (indent < 0) throw new IllegalArgumentException("indent must be >= 0, got " + indent);
        this.pretty = pretty;
        this.indent = indent;
    }

    /** Compact, single-line output — no whitespace padding. */
    public static StringifyOptions compact() { return new StringifyOptions(false, 2); }

    /** Human-readable output indented with 2 spaces per level. */
    public static StringifyOptions pretty() { return new StringifyOptions(true, 2); }

    /** Human-readable output with a custom indent width. */
    public static StringifyOptions prettyWithIndent(int indent) { return new StringifyOptions(true, indent); }

    /** {@code true} if human-readable indented output is enabled. */
    public boolean isPretty() { return pretty; }

    /** Spaces per indentation level (ignored when {@link #pretty()} is false). */
    public int indent() { return indent; }

    @Override
    public String toString() {
        return isPretty() ? "StringifyOptions{pretty, indent=" + indent + "}" : "StringifyOptions{compact}";
    }
}
