package io.github.datakore.jsont.error;

/**
 * Base unchecked exception for all runtime JsonT failures.
 *
 * <p>Subtypes mirror the Rust {@code JsonTError} enum variants and are declared
 * as {@code static final} inner classes so callers can catch precisely the
 * failure category they care about:
 *
 * <pre>{@code
 *   try {
 *       schema.transform(row, registry);
 *   } catch (JsonTError.Transform.Filtered e) {
 *       // row was deliberately excluded — not a hard failure
 *   } catch (JsonTError.Transform e) {
 *       // any other transform failure
 *   }
 * }</pre>
 */
public class JsonTError extends RuntimeException {

    public JsonTError(String message) {
        super(message);
    }

    public JsonTError(String message, Throwable cause) {
        super(message, cause);
    }

    // ─── Namespace / row parsing ───────────────────────────────────────────────

    /** Raised when namespace or row parsing fails (grammar mismatch, unknown ref). */
    public static final class Parse extends JsonTError {
        public Parse(String message) { super(message); }
        public Parse(String message, Throwable cause) { super(message, cause); }
    }

    // ─── Expression evaluation ─────────────────────────────────────────────────

    /** Raised when expression evaluation fails (unbound field, type mismatch, div-by-zero). */
    public static final class Eval extends JsonTError {
        public Eval(String message) { super(message); }
        public Eval(String message, Throwable cause) { super(message, cause); }
    }

    // ─── Row transformation ────────────────────────────────────────────────────

    /** Raised when a schema transformation fails. */
    public static class Transform extends JsonTError {
        public Transform(String message) { super(message); }
        public Transform(String message, Throwable cause) { super(message, cause); }

        /**
         * Special signal: a {@code filter} operation excluded this row.
         * This is NOT an error — callers should skip the row rather than propagate.
         */
        public static final class Filtered extends Transform {
            public Filtered() { super("row filtered"); }
        }

        /** A referenced field was not found in the row at the expected position. */
        public static final class FieldNotFound extends Transform {
            public FieldNotFound(String fieldName) {
                super("Field not found in row: " + fieldName);
            }
        }

        /** A derived schema referenced an unknown parent schema name. */
        public static final class UnknownSchema extends Transform {
            public UnknownSchema(String schemaName) {
                super("Unknown schema: " + schemaName);
            }
        }

        /** A derivation chain contains a cycle (A derived from B derived from A). */
        public static final class CyclicDerivation extends Transform {
            public CyclicDerivation(String cycle) {
                super("Cyclic schema derivation detected: " + cycle);
            }
        }

        /**
         * A {@code decrypt(...)} operation failed — either the crypto call returned an
         * error or the decrypted bytes were not valid UTF-8.
         */
        public static final class DecryptFailed extends Transform {
            public DecryptFailed(String fieldName, String reason) {
                super("decrypt failed for field '" + fieldName + "': " + reason);
            }
        }
    }

    // ─── Serialization ─────────────────────────────────────────────────────────

    /** Raised during JsonT serialization (stringify). */
    public static final class Stringify extends JsonTError {
        public Stringify(String message) { super(message); }
        public Stringify(String message, Throwable cause) { super(message, cause); }
    }

    // ─── Static analysis ───────────────────────────────────────────────────────

    /** Raised by {@code JsonTSchema.validateSchema(registry)} for structural problems. */
    public static final class SchemaInvalid extends JsonTError {
        public SchemaInvalid(String message) { super(message); }
    }
}
