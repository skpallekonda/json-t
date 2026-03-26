package io.github.datakore.jsont.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Holds named bindings for expression evaluation.
 *
 * <p>Use {@link #create()} to start building a context, then chain {@link #bind}
 * calls — each returns the same mutable instance (fluent style matching Rust's
 * {@code EvalContext::new().bind(...)}):
 *
 * <pre>{@code
 *   EvalContext ctx = EvalContext.create()
 *       .bind("age",  JsonTValue.i32(25))
 *       .bind("name", JsonTValue.text("Alice"));
 *
 *   JsonTValue result = expr.evaluate(ctx);
 * }</pre>
 *
 * <p>Once passed to {@link JsonTExpression#evaluate}, a context should not be
 * mutated further. Use {@link #snapshot()} to obtain an immutable copy.
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

    /**
     * Binds {@code name} to {@code value} and returns {@code this} (fluent).
     *
     * @param name  field name
     * @param value the value to bind
     * @return this context
     */
    public EvalContext bind(String name, JsonTValue value) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name must not be blank");
        bindings.put(name, value);
        return this;
    }

    /**
     * Returns the value bound to {@code name}, or {@link Optional#empty()} if absent.
     */
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
