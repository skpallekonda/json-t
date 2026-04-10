package io.github.datakore.jsont.crypto;

/**
 * Thrown when a {@link CryptoConfig} encrypt or decrypt operation fails.
 */
public class CryptoError extends Exception {

    private final String fieldName;

    public CryptoError(String fieldName, String message) {
        super(message);
        this.fieldName = fieldName;
    }

    public CryptoError(String fieldName, String message, Throwable cause) {
        super(message, cause);
        this.fieldName = fieldName;
    }

    /** The name of the field that triggered the error. */
    public String fieldName() { return fieldName; }
}
