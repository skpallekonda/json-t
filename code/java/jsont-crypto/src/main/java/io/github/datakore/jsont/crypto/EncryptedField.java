package io.github.datakore.jsont.crypto;

/**
 * Result of a single field encryption: the per-field IV and ciphertext+tag bytes.
 *
 * @param iv         the nonce used for this field (unique per DEK usage)
 * @param encContent the ciphertext + authentication tag
 */
public record EncryptedField(byte[] iv, byte[] encContent) {

    public EncryptedField {
        iv         = iv.clone();
        encContent = encContent.clone();
    }
}
