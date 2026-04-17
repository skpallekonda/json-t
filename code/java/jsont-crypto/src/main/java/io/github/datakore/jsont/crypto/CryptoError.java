package io.github.datakore.jsont.crypto;

/**
 * Sealed exception hierarchy for all crypto failures in JsonT.
 *
 * <p>Use pattern matching ({@code instanceof}) to distinguish variants:
 * <pre>{@code
 *   catch (CryptoError e) {
 *       if (e instanceof CryptoError.KeyNotFound knf) {
 *           System.err.println("Missing key var: " + knf.var());
 *       }
 *   }
 * }</pre>
 */
public sealed class CryptoError extends Exception
        permits CryptoError.KeyNotFound,
                CryptoError.InvalidKey,
                CryptoError.UnsupportedAlgorithm,
                CryptoError.UnsupportedKekMode,
                CryptoError.DekWrapFailed,
                CryptoError.DekUnwrapFailed,
                CryptoError.EncryptFailed,
                CryptoError.DecryptFailed,
                CryptoError.DigestMismatch,
                CryptoError.MalformedPayload {

    protected CryptoError(String message) {
        super(message);
    }

    protected CryptoError(String message, Throwable cause) {
        super(message, cause);
    }

    // -------------------------------------------------------------------------

    /** An expected environment variable holding key material was not set. */
    public static final class KeyNotFound extends CryptoError {
        private final String var;

        public KeyNotFound(String var, String reason) {
            super("Key not found in env var '" + var + "': " + reason);
            this.var = var;
        }

        /** The name of the environment variable that was not set. */
        public String var() { return var; }
    }

    // -------------------------------------------------------------------------

    /** Key material was present but could not be parsed. */
    public static final class InvalidKey extends CryptoError {
        public InvalidKey(String reason) {
            super("Invalid key: " + reason);
        }

        public InvalidKey(String reason, Throwable cause) {
            super("Invalid key: " + reason, cause);
        }
    }

    // -------------------------------------------------------------------------

    /** The version field requests an algorithm that is not implemented. */
    public static final class UnsupportedAlgorithm extends CryptoError {
        private final int algo;

        public UnsupportedAlgorithm(int algo, String reason) {
            super("Unsupported algorithm version " + algo + ": " + reason);
            this.algo = algo;
        }

        /** The raw algorithm version nibble from the version field. */
        public int algo() { return algo; }
    }

    // -------------------------------------------------------------------------

    /** The version field requests a KEK mode that is not implemented. */
    public static final class UnsupportedKekMode extends CryptoError {
        private final int mode;

        public UnsupportedKekMode(int mode, String reason) {
            super("Unsupported KEK mode " + mode + ": " + reason);
            this.mode = mode;
        }

        /** The raw KEK mode bit from the version field. */
        public int mode() { return mode; }
    }

    // -------------------------------------------------------------------------

    /** Wrapping the DEK with the public key failed. */
    public static final class DekWrapFailed extends CryptoError {
        public DekWrapFailed(String reason) {
            super("DEK wrap failed: " + reason);
        }

        public DekWrapFailed(String reason, Throwable cause) {
            super("DEK wrap failed: " + reason, cause);
        }
    }

    // -------------------------------------------------------------------------

    /** Unwrapping the DEK with the private key failed. */
    public static final class DekUnwrapFailed extends CryptoError {
        public DekUnwrapFailed(String reason) {
            super("DEK unwrap failed: " + reason);
        }

        public DekUnwrapFailed(String reason, Throwable cause) {
            super("DEK unwrap failed: " + reason, cause);
        }
    }

    // -------------------------------------------------------------------------

    /** AES-GCM encryption of a field value failed. */
    public static final class EncryptFailed extends CryptoError {
        private final String field;

        public EncryptFailed(String field, String reason) {
            super("Encrypt failed for field '" + field + "': " + reason);
            this.field = field;
        }

        public EncryptFailed(String field, String reason, Throwable cause) {
            super("Encrypt failed for field '" + field + "': " + reason, cause);
            this.field = field;
        }

        public String field() { return field; }
    }

    // -------------------------------------------------------------------------

    /** AES-GCM decryption of a field value failed. */
    public static final class DecryptFailed extends CryptoError {
        private final String field;

        public DecryptFailed(String field, String reason) {
            super("Decrypt failed for field '" + field + "': " + reason);
            this.field = field;
        }

        public DecryptFailed(String field, String reason, Throwable cause) {
            super("Decrypt failed for field '" + field + "': " + reason, cause);
            this.field = field;
        }

        public String field() { return field; }
    }

    // -------------------------------------------------------------------------

    /** The SHA-256 digest of the decrypted plaintext did not match. */
    public static final class DigestMismatch extends CryptoError {
        private final String field;
        private final String expected;
        private final String actual;

        public DigestMismatch(String field, String expected, String actual) {
            super("Digest mismatch for field '" + field + "': expected=" + expected + " actual=" + actual);
            this.field    = field;
            this.expected = expected;
            this.actual   = actual;
        }

        public String field()    { return field; }
        public String expected() { return expected; }
        public String actual()   { return actual; }
    }

    // -------------------------------------------------------------------------

    /** The binary ciphertext payload has an unexpected structure. */
    public static final class MalformedPayload extends CryptoError {
        private final String field;

        public MalformedPayload(String field, String reason) {
            super("Malformed payload for field '" + field + "': " + reason);
            this.field = field;
        }

        public String field() { return field; }
    }
}
