package io.github.datakore.jsont.crypto;

// =============================================================================
// CryptoTests — Steps 10.5-10.7: Stringify+Crypto, Transform Decrypt,
//                On-demand Decrypt (Java)
// =============================================================================
// Coverage:
//   10.5 — RowWriter.writeRow (schema-aware): sensitive plaintext → encrypted wire
//   10.5 — RowWriter.writeRow (schema-aware): Encrypted value re-encoded to base64
//   10.5 — RowWriter.writeRow (schema-aware): non-sensitive field written normally
//   10.5 — PassthroughCryptoConfig: identity round-trip
//   10.6 — RowTransformer.transformWithCrypto: Decrypt op decrypts Encrypted → plain
//   10.6 — RowTransformer.transformWithCrypto: Decrypt on plaintext is idempotent
//   10.6 — RowTransformer.transform (no crypto): Decrypt op → Transform error
//   10.6 — RowTransformer.transformWithCrypto on straight schema → row unchanged
//   10.7 — JsonTValue.decryptStr: Encrypted → Optional.of(plaintext)
//   10.7 — JsonTValue.decryptStr: non-encrypted → Optional.empty()
//   10.7 — JsonTValue.decryptBytes: Encrypted → Optional.of(bytes)
//   10.7 — JsonTRow.decryptField: correct index → Optional.of(plaintext)
//   10.7 — JsonTRow.decryptField: out-of-range index → IndexOutOfBoundsException
//   10.7 — JsonTRow.decryptField: non-encrypted field → Optional.empty()
// =============================================================================

import io.github.datakore.jsont.builder.JsonTFieldBuilder;
import io.github.datakore.jsont.builder.JsonTSchemaBuilder;
import io.github.datakore.jsont.builder.SchemaRegistry;
import io.github.datakore.jsont.error.JsonTError;
import io.github.datakore.jsont.model.JsonTRow;
import io.github.datakore.jsont.model.JsonTSchema;
import io.github.datakore.jsont.model.JsonTValue;
import io.github.datakore.jsont.model.ScalarType;
import io.github.datakore.jsont.model.SchemaOperation;
import io.github.datakore.jsont.stringify.RowWriter;
import io.github.datakore.jsont.transform.RowTransformer;

import org.junit.jupiter.api.Test;

import java.io.StringWriter;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class CryptoTests {

    // =========================================================================
    // Helpers
    // =========================================================================

    /** Build a schema with one plain "name" field and one sensitive "ssn" field. */
    static JsonTSchema sensitiveSchema() throws Exception {
        return JsonTSchemaBuilder.straight("Person")
                .fieldFrom(JsonTFieldBuilder.scalar("name", ScalarType.STR))
                .fieldFrom(JsonTFieldBuilder.scalar("ssn",  ScalarType.STR).sensitive())
                .build();
    }

    /** Produce the plain-base64 wire representation of bytes (no prefix). */
    static String base64Wire(byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }

    /** Build parent+derived+registry for Decrypt transform tests. */
    static Object[] personWithDecrypt() throws Exception {
        JsonTSchema parent = JsonTSchemaBuilder.straight("Person")
                .fieldFrom(JsonTFieldBuilder.scalar("name", ScalarType.STR))
                .fieldFrom(JsonTFieldBuilder.scalar("ssn",  ScalarType.STR).sensitive())
                .build();

        JsonTSchema derived = JsonTSchemaBuilder.derived("PersonDecrypted", "Person")
                .operation(SchemaOperation.decrypt("ssn"))
                .build();

        SchemaRegistry registry = SchemaRegistry.empty()
                .register(parent)
                .register(derived);

        return new Object[]{parent, derived, registry};
    }

    // =========================================================================
    // 10.5 — RowWriter.writeRow (schema-aware)
    // =========================================================================

    @Test
    void writeRow_sensitivePlaintext_encryptedOnWire() throws Exception {
        JsonTSchema schema = sensitiveSchema();
        CryptoConfig crypto = new PassthroughCryptoConfig();

        JsonTRow row = JsonTRow.of(
                JsonTValue.text("Alice"),
                JsonTValue.text("123-45-6789")
        );

        StringWriter sw = new StringWriter();
        RowWriter.writeRow(row, schema.fields(), crypto, sw);
        String out = sw.toString();

        assertTrue(out.contains("\"Alice\""), "name should be plain: " + out);
        // Passthrough crypto is identity — plain base64 of "123-45-6789" UTF-8 bytes must appear.
        String expectedB64 = base64Wire("123-45-6789".getBytes());
        assertTrue(out.contains(expectedB64), "base64 payload mismatch: " + out);
        // The ssn must not appear in plaintext.
        assertFalse(out.contains("\"123-45-6789\""), "ssn must be encrypted: " + out);
    }

    @Test
    void writeRow_encryptedValue_reEncodedWithoutCryptoCall() throws Exception {
        JsonTSchema schema = sensitiveSchema();
        CryptoConfig crypto = new PassthroughCryptoConfig();

        byte[] ciphertext = "already-ciphertext".getBytes();
        JsonTRow row = JsonTRow.of(
                JsonTValue.text("Bob"),
                JsonTValue.encrypted(ciphertext)
        );

        StringWriter sw = new StringWriter();
        RowWriter.writeRow(row, schema.fields(), crypto, sw);
        String out = sw.toString();

        assertTrue(out.contains(base64Wire(ciphertext)), "ciphertext re-encoding mismatch: " + out);
    }

    @Test
    void writeRow_nonSensitiveField_writtenNormally() throws Exception {
        JsonTSchema schema = sensitiveSchema();
        CryptoConfig crypto = new PassthroughCryptoConfig();

        JsonTRow row = JsonTRow.of(
                JsonTValue.text("Carol"),
                JsonTValue.text("999-00-1234")
        );

        StringWriter sw = new StringWriter();
        RowWriter.writeRow(row, schema.fields(), crypto, sw);
        String out = sw.toString();

        // name (non-sensitive) must be written as a plain quoted first field.
        assertTrue(out.startsWith("{\"Carol\""), "name should be plain first field: " + out);
    }

    @Test
    void passthroughCrypto_identityRoundTrip() throws CryptoError {
        PassthroughCryptoConfig crypto = new PassthroughCryptoConfig();
        byte[] plaintext = "hello world".getBytes();
        byte[] ciphertext = crypto.encrypt("f", plaintext);
        byte[] decrypted  = crypto.decrypt("f", ciphertext);
        assertArrayEquals(plaintext, decrypted);
    }

    // =========================================================================
    // 10.6 — RowTransformer.transformWithCrypto
    // =========================================================================

    @Test
    void transformWithCrypto_decryptsEncryptedField() throws Exception {
        Object[] ctx = personWithDecrypt();
        JsonTSchema derived    = (JsonTSchema)    ctx[1];
        SchemaRegistry registry = (SchemaRegistry) ctx[2];
        CryptoConfig crypto = new PassthroughCryptoConfig();

        // SSN stored as Encrypted bytes (passthrough: bytes == UTF-8 plaintext).
        JsonTRow row = JsonTRow.of(
                JsonTValue.text("Alice"),
                JsonTValue.encrypted("123-45-6789".getBytes())
        );

        JsonTRow result = RowTransformer.of(derived, registry).transformWithCrypto(row, crypto);

        assertEquals(JsonTValue.text("Alice"), result.get(0));
        assertEquals(JsonTValue.text("123-45-6789"), result.get(1));
    }

    @Test
    void transformWithCrypto_idempotentOnPlaintextField() throws Exception {
        Object[] ctx = personWithDecrypt();
        JsonTSchema derived    = (JsonTSchema)    ctx[1];
        SchemaRegistry registry = (SchemaRegistry) ctx[2];
        CryptoConfig crypto = new PassthroughCryptoConfig();

        JsonTRow row = JsonTRow.of(
                JsonTValue.text("Bob"),
                JsonTValue.text("000-11-2222")
        );

        JsonTRow result = RowTransformer.of(derived, registry).transformWithCrypto(row, crypto);
        assertEquals(JsonTValue.text("000-11-2222"), result.get(1));
    }

    @Test
    void transform_withoutCrypto_decryptOpThrows() throws Exception {
        Object[] ctx = personWithDecrypt();
        JsonTSchema derived    = (JsonTSchema)    ctx[1];
        SchemaRegistry registry = (SchemaRegistry) ctx[2];

        JsonTRow row = JsonTRow.of(
                JsonTValue.text("Carol"),
                JsonTValue.encrypted("secret".getBytes())
        );

        RowTransformer transformer = RowTransformer.of(derived, registry);
        // transform() passes null CryptoConfig — Decrypt must throw.
        assertThrows(JsonTError.Transform.class, () -> transformer.transform(row));
    }

    @Test
    void transformWithCrypto_straightSchema_rowUnchanged() throws Exception {
        JsonTSchema schema = JsonTSchemaBuilder.straight("Simple")
                .fieldFrom(JsonTFieldBuilder.scalar("x", ScalarType.I32))
                .build();
        SchemaRegistry registry = SchemaRegistry.empty().register(schema);
        CryptoConfig crypto = new PassthroughCryptoConfig();

        JsonTRow row = JsonTRow.of(JsonTValue.i32(42));
        JsonTRow result = RowTransformer.of(schema, registry).transformWithCrypto(row, crypto);

        assertEquals(row.values(), result.values());
    }

    // =========================================================================
    // 10.7 — on-demand decrypt API
    // =========================================================================

    @Test
    void decryptStr_onEncrypted_returnsPlaintext() throws CryptoError {
        CryptoConfig crypto = new PassthroughCryptoConfig();
        JsonTValue val = JsonTValue.encrypted("hello".getBytes());
        var result = val.decryptStr("f", crypto);
        assertTrue(result.isPresent());
        assertEquals("hello", result.get());
    }

    @Test
    void decryptStr_onPlainString_returnsEmpty() throws CryptoError {
        CryptoConfig crypto = new PassthroughCryptoConfig();
        JsonTValue val = JsonTValue.text("plaintext");
        var result = val.decryptStr("f", crypto);
        assertTrue(result.isEmpty());
    }

    @Test
    void decryptBytes_onEncrypted_returnsBytes() throws CryptoError {
        CryptoConfig crypto = new PassthroughCryptoConfig();
        byte[] bytes = "raw bytes".getBytes();
        JsonTValue val = JsonTValue.encrypted(bytes);
        var result = val.decryptBytes("f", crypto);
        assertTrue(result.isPresent());
        assertArrayEquals(bytes, result.get());
    }

    @Test
    void decryptBytes_onNull_returnsEmpty() throws CryptoError {
        CryptoConfig crypto = new PassthroughCryptoConfig();
        JsonTValue val = JsonTValue.nullValue();
        var result = val.decryptBytes("f", crypto);
        assertTrue(result.isEmpty());
    }

    @Test
    void rowDecryptField_atValidEncryptedIndex_returnsPlaintext() throws CryptoError {
        CryptoConfig crypto = new PassthroughCryptoConfig();
        JsonTRow row = JsonTRow.of(
                JsonTValue.text("Alice"),
                JsonTValue.encrypted("123-45-6789".getBytes())
        );
        var result = row.decryptField(1, "ssn", crypto);
        assertTrue(result.isPresent());
        assertEquals("123-45-6789", result.get());
    }

    @Test
    void rowDecryptField_outOfRange_throwsIndexOutOfBounds() {
        CryptoConfig crypto = new PassthroughCryptoConfig();
        JsonTRow row = JsonTRow.of(JsonTValue.text("Alice"));
        assertThrows(IndexOutOfBoundsException.class,
                () -> row.decryptField(99, "ssn", crypto));
    }

    @Test
    void rowDecryptField_nonEncrypted_returnsEmpty() throws CryptoError {
        CryptoConfig crypto = new PassthroughCryptoConfig();
        JsonTRow row = JsonTRow.of(
                JsonTValue.text("Alice"),
                JsonTValue.text("plain ssn")
        );
        var result = row.decryptField(1, "ssn", crypto);
        assertTrue(result.isEmpty());
    }
}
