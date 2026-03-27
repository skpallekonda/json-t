package io.github.datakore.jsont.error;

/**
 * Checked exception raised when a builder is misconfigured.
 *
 * <p>Mirrors Rust's {@code BuildError} returned from {@code .build()?} calls.
 * Because builder failures indicate programmer errors that must be resolved at
 * development time, this is checked — callers must either handle or declare it.
 *
 * <pre>{@code
 *   JsonTSchema schema = JsonTSchemaBuilder.straight("Order")
 *       .fieldFrom(JsonTFieldBuilder.scalar("id", ScalarType.I64))
 *       .build();           // throws BuildError if misconfigured
 * }</pre>
 */
public final class BuildError extends Exception {

    public BuildError(String message) {
        super(message);
    }

    public BuildError(String message, Throwable cause) {
        super(message, cause);
    }
}
