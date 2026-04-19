package io.github.datakore.jsont.test;

// =============================================================================
// CryptoTests — Steps 5 & 6: stream-level DEK model (Java)
// =============================================================================
// Coverage:
//   5.1 — PassthroughCryptoConfig: wrap/unwrap DEK is identity
//   5.2 — CryptoContext (AES-GCM): encrypt/decrypt field round-trip
//   5.3 — RowWriter (encrypted): sensitive plaintext → encrypted on wire
//   5.4 — RowWriter (encrypted): Encrypted value re-encoded as-is
//   5.5 — RowWriter (encrypted): non-sensitive field written normally
//   5.6 — RowWriter.writeStream: produces header + data rows
//   5.7 — FieldPayload: assemble/parse round-trip
//   6.1 — JsonTValue.decryptStr: Encrypted with valid payload → plaintext
//   6.2 — JsonTValue.decryptStr: non-encrypted → Optional.empty()
//   6.3 — JsonTValue.decryptBytes: Encrypted → bytes
//   6.4 — JsonTValue.decryptBytes: null value → Optional.empty()
//   6.5 — JsonTValue.decryptBytes: digest mismatch → DigestMismatch
//   6.6 — JsonTRow.decryptField: correct index → plaintext
//   6.7 — JsonTRow.decryptField: out-of-range → IndexOutOfBoundsException
//   6.8 — JsonTRow.decryptField: non-encrypted → Optional.empty()
//   6.9 — RowTransformer.transformWithContext: Decrypt op decrypts Encrypted → plain
//   6.10 — RowTransformer.transformWithContext: Decrypt on plaintext is idempotent
//   6.11 — RowTransformer.transform (no crypto): Decrypt op → Transform error
//   6.12 — RowTransformer.transformWithContext on straight schema → row unchanged
// =============================================================================

import io.github.datakore.jsont.crypto.AlgoVersion;
import io.github.datakore.jsont.crypto.CryptoContext;
import io.github.datakore.jsont.crypto.CryptoError;
import io.github.datakore.jsont.crypto.EncryptedField;
import io.github.datakore.jsont.crypto.KekMode;
import io.github.datakore.jsont.crypto.PassthroughCryptoConfig;
import io.github.datakore.jsont.builder.JsonTFieldBuilder;
import io.github.datakore.jsont.builder.JsonTSchemaBuilder;
import io.github.datakore.jsont.builder.SchemaRegistry;
import io.github.datakore.jsont.error.JsonTError;
import io.github.datakore.jsont.internal.crypto.FieldPayload;
import io.github.datakore.jsont.model.JsonTRow;
import io.github.datakore.jsont.model.JsonTSchema;
import io.github.datakore.jsont.model.JsonTValue;
import io.github.datakore.jsont.model.ScalarType;
import io.github.datakore.jsont.model.SchemaOperation;
import io.github.datakore.jsont.stringify.RowWriter;
import io.github.datakore.jsont.transform.RowTransformer;

import org.junit.jupiter.api.Test;

import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CryptoTests {

    // =========================================================================
    // Helpers
    // =========================================================================

    private static final PassthroughCryptoConfig PASSTHROUGH = new PassthroughCryptoConfig();

    /** Create an AES-GCM encrypt {@link CryptoContext} backed by passthrough key wrapping. */
    static CryptoContext encCtx() throws CryptoError {
        return CryptoContext.forEncrypt(AlgoVersion.AES_GCM, KekMode.PUBLIC_KEY, PASSTHROUGH);
    }

    /** Create a decrypt {@link CryptoContext} that mirrors the given encrypt context. */
    static CryptoContext decCtx(CryptoContext enc) throws CryptoError {
        return CryptoContext.forDecrypt(enc.version(), enc.encDek(), PASSTHROUGH);
    }

    /**
     * Assemble a per-field {@link FieldPayload} by encrypting {@code plaintext}
     * with the supplied encrypt context.
     */
    static byte[] buildPayload(String plaintext, CryptoContext encCtx) throws Exception {
        byte[] ptBytes = plaintext.getBytes(StandardCharsets.UTF_8);
        byte[] digest  = MessageDigest.getInstance("SHA-256").digest(ptBytes);
        EncryptedField ef = encCtx.encryptField(ptBytes);
        return FieldPayload.assemble(ef.iv(), digest, ef.encContent());
    }

    /** Build a schema with one plain "name" field and one sensitive "ssn" field. */
    static JsonTSchema sensitiveSchema() throws Exception {
        return JsonTSchemaBuilder.straight("Person")
                .fieldFrom(JsonTFieldBuilder.scalar("name", ScalarType.STR))
                .fieldFrom(JsonTFieldBuilder.scalar("ssn",  ScalarType.STR).sensitive())
                .build();
    }

    /** Build parent + derived + registry for Decrypt transform tests. */
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
    // 5.1 — PassthroughCryptoConfig: wrap/unwrap DEK is identity
    // =========================================================================

    @Test
    void passthrough_wrapUnwrapDek_isIdentity() throws CryptoError {
        byte[] dek = new byte[32];
        for (int i = 0; i < 32; i++) dek[i] = (byte) i;

        byte[] encDek    = PASSTHROUGH.wrapDek(CryptoContext.VERSION_AES_PUBKEY, dek);
        byte[] unwrapped = PASSTHROUGH.unwrapDek(CryptoContext.VERSION_AES_PUBKEY, encDek);
        assertArrayEquals(dek, unwrapped);
    }

    // =========================================================================
    // 5.2 — CryptoContext (AES-GCM): encrypt/decrypt field round-trip
    // =========================================================================

    @Test
    void cryptoContext_encryptDecryptField_roundTrip() throws CryptoError {
        byte[] plaintext = "hello world".getBytes(StandardCharsets.UTF_8);

        try (CryptoContext enc = encCtx();
             CryptoContext dec = decCtx(enc)) {
            EncryptedField ef = enc.encryptField(plaintext);
            byte[] decrypted  = dec.decryptField(ef.iv(), ef.encContent());
            assertArrayEquals(plaintext, decrypted);
        }
    }

    // =========================================================================
    // 5.3 — RowWriter (encrypted): sensitive plaintext → encrypted on wire
    // =========================================================================

    @Test
    void writeRow_sensitivePlaintext_encryptedOnWire() throws Exception {
        JsonTSchema schema = sensitiveSchema();

        JsonTRow row = JsonTRow.of(
                JsonTValue.text("Alice"),
                JsonTValue.text("123-45-6789")
        );

        StringWriter sw = new StringWriter();
        try (CryptoContext enc = encCtx();
             RowWriter writer = new RowWriter(schema, enc)) {
            writer.writeStream(List.of(row), sw);
        }
        String out = sw.toString();

        assertTrue(out.contains("\"Alice\""), "name should be plain: " + out);
        assertFalse(out.contains("\"123-45-6789\""), "ssn must be encrypted: " + out);
    }

    // =========================================================================
    // 5.4 — RowWriter (encrypted): Encrypted value re-encoded as-is
    // =========================================================================

    @Test
    void writeRow_encryptedValue_reEncodedAsIs() throws Exception {
        JsonTSchema schema = sensitiveSchema();

        byte[] existingPayload;
        try (CryptoContext enc = encCtx()) {
            existingPayload = buildPayload("already-encrypted", enc);
        }

        JsonTRow row = JsonTRow.of(
                JsonTValue.text("Bob"),
                JsonTValue.encrypted(existingPayload)
        );

        StringWriter sw = new StringWriter();
        try (CryptoContext enc = encCtx();
             RowWriter writer = new RowWriter(schema, enc)) {
            writer.writeStream(List.of(row), sw);
        }
        String out = sw.toString();

        String expectedB64 = Base64.getEncoder().encodeToString(existingPayload);
        assertTrue(out.contains(expectedB64), "existing payload must be re-encoded as-is: " + out);
    }

    // =========================================================================
    // 5.5 — RowWriter (encrypted): non-sensitive field written normally
    // =========================================================================

    @Test
    void writeRow_nonSensitiveField_writtenNormally() throws Exception {
        JsonTSchema schema = sensitiveSchema();

        JsonTRow row = JsonTRow.of(
                JsonTValue.text("Carol"),
                JsonTValue.text("999-00-1234")
        );

        StringWriter sw = new StringWriter();
        try (CryptoContext enc = encCtx();
             RowWriter writer = new RowWriter(schema, enc)) {
            writer.writeStream(List.of(row), sw);
        }
        String out = sw.toString();

        assertTrue(out.contains("\"Carol\""), "name should be plain: " + out);
    }

    // =========================================================================
    // 5.6 — RowWriter.writeStream: produces header + data rows
    // =========================================================================

    @Test
    void writeStream_producesHeaderAndDataRows() throws Exception {
        JsonTSchema schema = sensitiveSchema();

        JsonTRow row = JsonTRow.of(
                JsonTValue.text("Dave"),
                JsonTValue.text("111-22-3333")
        );

        StringWriter sw = new StringWriter();
        try (CryptoContext enc = encCtx();
             RowWriter writer = new RowWriter(schema, enc)) {
            writer.writeStream(List.of(row), sw);
        }
        String out = sw.toString();

        assertTrue(out.contains(",\n"), "stream must separate header and data rows: " + out);
        assertTrue(out.contains("\"Dave\""), "data row name must be plain: " + out);
        assertTrue(out.contains("ENCRYPTED_HEADER"), "header row must be present: " + out);
    }

    // =========================================================================
    // 5.7 — FieldPayload: assemble/parse round-trip
    // =========================================================================

    @Test
    void fieldPayload_assembleAndParse_roundTrip() throws Exception {
        byte[] iv         = new byte[12];
        byte[] digest     = MessageDigest.getInstance("SHA-256").digest("test".getBytes(StandardCharsets.UTF_8));
        byte[] encContent = "ciphertext".getBytes(StandardCharsets.UTF_8);

        byte[] payload = FieldPayload.assemble(iv, digest, encContent);
        FieldPayload.Parsed parsed = FieldPayload.parse(payload, "field");

        assertArrayEquals(iv,         parsed.iv());
        assertArrayEquals(digest,     parsed.digest());
        assertArrayEquals(encContent, parsed.encContent());
    }

    // =========================================================================
    // 6.1 — JsonTValue.decryptStr: Encrypted with valid payload → plaintext
    // =========================================================================

    @Test
    void decryptStr_onEncrypted_returnsPlaintext() throws Exception {
        try (CryptoContext enc = encCtx();
             CryptoContext dec = decCtx(enc)) {
            JsonTValue val = JsonTValue.encrypted(buildPayload("hello", enc));
            var result = val.decryptStr("f", dec);
            assertTrue(result.isPresent());
            assertEquals("hello", result.get());
        }
    }

    // =========================================================================
    // 6.2 — JsonTValue.decryptStr: non-encrypted → Optional.empty()
    // =========================================================================

    @Test
    void decryptStr_onPlainString_returnsEmpty() throws Exception {
        try (CryptoContext enc = encCtx();
             CryptoContext dec = decCtx(enc)) {
            var result = JsonTValue.text("plaintext").decryptStr("f", dec);
            assertTrue(result.isEmpty());
        }
    }

    // =========================================================================
    // 6.3 — JsonTValue.decryptBytes: Encrypted → bytes
    // =========================================================================

    @Test
    void decryptBytes_onEncrypted_returnsBytes() throws Exception {
        byte[] plaintext = "raw bytes".getBytes(StandardCharsets.UTF_8);

        try (CryptoContext enc = encCtx();
             CryptoContext dec = decCtx(enc)) {
            var result = JsonTValue.encrypted(buildPayload("raw bytes", enc)).decryptBytes("f", dec);
            assertTrue(result.isPresent());
            assertArrayEquals(plaintext, result.get());
        }
    }

    // =========================================================================
    // 6.4 — JsonTValue.decryptBytes: null value → Optional.empty()
    // =========================================================================

    @Test
    void decryptBytes_onNull_returnsEmpty() throws Exception {
        try (CryptoContext enc = encCtx();
             CryptoContext dec = decCtx(enc)) {
            var result = JsonTValue.nullValue().decryptBytes("f", dec);
            assertTrue(result.isEmpty());
        }
    }

    // =========================================================================
    // 6.5 — JsonTValue.decryptBytes: digest mismatch → DigestMismatch
    // =========================================================================

    @Test
    void decryptBytes_digestMismatch_throws() throws Exception {
        try (CryptoContext enc = encCtx();
             CryptoContext dec = decCtx(enc)) {
            // Encrypt legit content but pair with a wrong (all-zeros) digest.
            byte[] pt = "data".getBytes(StandardCharsets.UTF_8);
            EncryptedField ef  = enc.encryptField(pt);
            byte[] wrongDigest = new byte[32]; // all zeros — mismatch for "data"
            JsonTValue val = JsonTValue.encrypted(
                    FieldPayload.assemble(ef.iv(), wrongDigest, ef.encContent()));

            assertThrows(CryptoError.DigestMismatch.class,
                    () -> val.decryptBytes("f", dec));
        }
    }

    // =========================================================================
    // 6.6 — JsonTRow.decryptField: correct index → plaintext
    // =========================================================================

    @Test
    void rowDecryptField_atValidEncryptedIndex_returnsPlaintext() throws Exception {
        try (CryptoContext enc = encCtx();
             CryptoContext dec = decCtx(enc)) {
            JsonTRow row = JsonTRow.of(
                    JsonTValue.text("Alice"),
                    JsonTValue.encrypted(buildPayload("123-45-6789", enc))
            );

            var result = row.decryptField(1, "ssn", dec);
            assertTrue(result.isPresent());
            assertEquals("123-45-6789", result.get());
        }
    }

    // =========================================================================
    // 6.7 — JsonTRow.decryptField: out-of-range → IndexOutOfBoundsException
    // =========================================================================

    @Test
    void rowDecryptField_outOfRange_throwsIndexOutOfBounds() throws Exception {
        try (CryptoContext enc = encCtx();
             CryptoContext dec = decCtx(enc)) {
            JsonTRow row = JsonTRow.of(JsonTValue.text("Alice"));
            assertThrows(IndexOutOfBoundsException.class,
                    () -> row.decryptField(99, "ssn", dec));
        }
    }

    // =========================================================================
    // 6.8 — JsonTRow.decryptField: non-encrypted → Optional.empty()
    // =========================================================================

    @Test
    void rowDecryptField_nonEncrypted_returnsEmpty() throws Exception {
        try (CryptoContext enc = encCtx();
             CryptoContext dec = decCtx(enc)) {
            JsonTRow row = JsonTRow.of(
                    JsonTValue.text("Alice"),
                    JsonTValue.text("plain ssn")
            );

            var result = row.decryptField(1, "ssn", dec);
            assertTrue(result.isEmpty());
        }
    }

    // =========================================================================
    // 6.9 — RowTransformer.transformWithContext: Decrypt op decrypts Encrypted → plain
    // =========================================================================

    @Test
    void transformWithContext_decryptsEncryptedField() throws Exception {
        Object[] objs = personWithDecrypt();
        JsonTSchema derived     = (JsonTSchema)    objs[1];
        SchemaRegistry registry = (SchemaRegistry) objs[2];

        try (CryptoContext enc = encCtx();
             CryptoContext dec = decCtx(enc)) {
            JsonTRow row = JsonTRow.of(
                    JsonTValue.text("Alice"),
                    JsonTValue.encrypted(buildPayload("123-45-6789", enc))
            );

            JsonTRow result = RowTransformer.of(derived, registry).transformWithContext(row, dec);

            assertEquals(JsonTValue.text("Alice"),       result.get(0));
            assertEquals(JsonTValue.text("123-45-6789"), result.get(1));
        }
    }

    // =========================================================================
    // 6.10 — RowTransformer.transformWithContext: Decrypt on plaintext is idempotent
    // =========================================================================

    @Test
    void transformWithContext_idempotentOnPlaintextField() throws Exception {
        Object[] objs = personWithDecrypt();
        JsonTSchema derived     = (JsonTSchema)    objs[1];
        SchemaRegistry registry = (SchemaRegistry) objs[2];

        try (CryptoContext dec = decCtx(encCtx())) {
            JsonTRow row = JsonTRow.of(
                    JsonTValue.text("Bob"),
                    JsonTValue.text("000-11-2222")
            );

            JsonTRow result = RowTransformer.of(derived, registry).transformWithContext(row, dec);
            assertEquals(JsonTValue.text("000-11-2222"), result.get(1));
        }
    }

    // =========================================================================
    // 6.11 — RowTransformer.transform (no crypto): Decrypt op → Transform error
    // =========================================================================

    @Test
    void transform_withoutCrypto_decryptOpThrows() throws Exception {
        Object[] objs = personWithDecrypt();
        JsonTSchema derived     = (JsonTSchema)    objs[1];
        SchemaRegistry registry = (SchemaRegistry) objs[2];

        try (CryptoContext enc = encCtx()) {
            JsonTRow row = JsonTRow.of(
                    JsonTValue.text("Carol"),
                    JsonTValue.encrypted(buildPayload("secret", enc))
            );

            RowTransformer transformer = RowTransformer.of(derived, registry);
            assertThrows(JsonTError.Transform.class, () -> transformer.transform(row));
        }
    }

    // =========================================================================
    // 6.12 — RowTransformer.transformWithContext on straight schema → row unchanged
    // =========================================================================

    @Test
    void transformWithContext_straightSchema_rowUnchanged() throws Exception {
        JsonTSchema schema = JsonTSchemaBuilder.straight("Simple")
                .fieldFrom(JsonTFieldBuilder.scalar("x", ScalarType.I32))
                .build();
        SchemaRegistry registry = SchemaRegistry.empty().register(schema);

        try (CryptoContext dec = decCtx(encCtx())) {
            JsonTRow row    = JsonTRow.of(JsonTValue.i32(42));
            JsonTRow result = RowTransformer.of(schema, registry).transformWithContext(row, dec);
            assertEquals(row.values(), result.values());
        }
    }
}
