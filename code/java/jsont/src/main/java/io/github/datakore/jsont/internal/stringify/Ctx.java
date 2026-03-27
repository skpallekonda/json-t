package io.github.datakore.jsont.internal.stringify;

import io.github.datakore.jsont.stringify.StringifyOptions;

/** Immutable indentation context threaded through the stringify recursion. */
final class Ctx {

    final StringifyOptions opts;
    final int depth;

    Ctx(StringifyOptions opts) { this(opts, 0); }

    private Ctx(StringifyOptions opts, int depth) {
        this.opts = opts;
        this.depth = depth;
    }

    /** Indent string for this depth (empty when compact). */
    String indent() {
        return opts.isPretty() ? " ".repeat(depth * opts.indent()) : "";
    }

    /** Newline (empty when compact). */
    String nl() { return opts.isPretty() ? "\n" : ""; }

    /** Space (empty when compact). */
    String sp() { return opts.isPretty() ? " " : ""; }

    /** List separator — `,\n` pretty, `,` compact. */
    String sep() { return opts.isPretty() ? ",\n" : ","; }

    /** Returns a context one level deeper. */
    Ctx deeper() { return new Ctx(opts, depth + 1); }
}
