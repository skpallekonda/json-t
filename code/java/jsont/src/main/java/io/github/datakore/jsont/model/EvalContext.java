package io.github.datakore.jsont.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Named bindings used when evaluating a {@link JsonTExpression}.
 *
 * <pre>{@code
 *   EvalContext ctx = EvalContext.create()
 *       .bind("age",  JsonTValue.i32(25))
 *       .bind("name", JsonTValue.text("Alice"));
 *
 *   JsonTValue result = expr.evaluate(ctx);
 * }</pre>
 *
 * <p>After passing a context to {@link JsonTExpression#evaluate}, use
 * {@link #snapshot()} if you need an immutable copy.
 */
public final class EvalContext {

    private final Map<String, JsonTValue> bindings = new LinkedHashMap<>();

    private EvalContext() {}

    /** Returns a new, empty {@code EvalContext} ready for binding. */
    public static EvalContext create() {
        return new EvalContext();
    }

    /** Returns an immutable, empty context (useful as a no-op default). */
    public static EvalContext empty() {
        return new EvalContext();
    }

    /** Binds a field name to a value. Fluent — returns {@code this}. */
    public EvalContext bind(String name, JsonTValue value) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name must not be blank");
        bindings.put(name, value);
        return this;
    }

    /** Returns the value bound to {@code name}, or empty if absent. */
    public Optional<JsonTValue> lookup(String name) {
        return Optional.ofNullable(bindings.get(name));
    }

    /** Returns all current bindings as an unmodifiable view. */
    public Map<String, JsonTValue> bindings() {
        return Collections.unmodifiableMap(bindings);
    }

    /** Returns a new context whose bindings cannot be altered. */
    public EvalContext snapshot() {
        EvalContext copy = new EvalContext();
        copy.bindings.putAll(this.bindings);
        return copy;
    }

    /** Returns {@code true} if no bindings have been set. */
    public boolean isEmpty() {
        return bindings.isEmpty();
    }

    @Override
    public String toString() {
        return "EvalContext" + bindings;
    }
}
