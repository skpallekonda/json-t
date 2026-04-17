package io.github.datakore.jsont.test;

// =============================================================================
// CryptoTests — Steps 5 & 6: stream-level DEK model (Java)
// =============================================================================
// Coverage:
//   5.1 — PassthroughCryptoConfig: wrap/unwrap DEK is identity
//   5.2 — PassthroughCryptoConfig: encrypt/decrypt field round-trip
//   5.3 — RowWriter.writeRowWithDek: sensitive plaintext → encrypted on wire
//   5.4 — RowWriter.writeRowWithDek: Encrypted value re-encoded as-is
//   5.5 — RowWriter.writeRowWithDek: non-sensitive field written normally
//   5.6 — RowWriter.writeEncryptedStream: produces header + data rows
//   5.7 — FieldPayload: assemble/parse round-trip
//   6.1 — JsonTValue.decryptStr: Encrypted with valid payload → plaintext
//   6.2 — JsonTValue.decryptStr: non-encrypted → Optional.empty()
//   6.3 — JsonTValue.decryptBytes: Encrypted → bytes
//   6.4 — JsonTValue.decryptBytes: null value → Optional.empty()
//   6.5 — JsonTValue.decryptBytes: digest mismatch → DigestMismatch
//   6.6 — JsonTRow.decryptField: correct index → plaintext
//   6.7 — JsonTRow.decryptField: out-of-range → IndexOutOfBoundsException
//   6.8 — JsonTRow.decryptField: non-encrypted → Optional.empty()
//   6.9 — RowTransformer.transformWithCrypto: Decrypt op decrypts Encrypted → plain
//   6.10 — RowTransformer.transformWithCrypto: Decrypt on plaintext is idempotent
//   6.11 — RowTransformer.transform (no crypto): Decrypt op → Transform error
//   6.12 — RowTransformer.transformWithCrypto on straight schema → row unchanged
// =============================================================================

import io.github.datakore.jsont.crypto.CryptoConfig;
import io.github.datakore.jsont.crypto.CryptoContext;
import io.github.datakore.jsont.crypto.CryptoError;
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

    /** Fixed 32-byte zero DEK used across all passthrough tests. */
    static final byte[] TEST_DEK = new byte[32];

    /**
     * Build a {@link CryptoContext} whose encDek is the result of
     * {@code PassthroughCryptoConfig.wrapDek(TEST_DEK)}, so that
     * {@code unwrapDek} during decryption returns {@code TEST_DEK}.
     */
    static CryptoContext testContext() throws CryptoError {
        PassthroughCryptoConfig crypto = new PassthroughCryptoConfig();
        byte[] encDek = crypto.wrapDek(CryptoContext.VERSION_AES_PUBKEY, TEST_DEK);
        return new CryptoContext(CryptoContext.VERSION_AES_PUBKEY, encDek);
    }

    /**
     * Assemble a properly-structured per-field FieldPayload for the given
     * plaintext, using {@link PassthroughCryptoConfig} and {@link #TEST_DEK}.
     */
    static byte[] buildPayload(String plaintext) throws Exception {
        PassthroughCryptoConfig crypto = new PassthroughCryptoConfig();
        byte[] ptBytes = plaintext.getBytes(StandardCharsets.UTF_8);
        byte[] digest  = MessageDigest.getInstance("SHA-256").digest(ptBytes);
        CryptoConfig.EncryptedField ef = crypto.encryptField(TEST_DEK, ptBytes);
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
        PassthroughCryptoConfig crypto = new PassthroughCryptoConfig();
        byte[] encDek    = crypto.wrapDek(CryptoContext.VERSION_AES_PUBKEY, TEST_DEK);
        byte[] unwrapped = crypto.unwrapDek(CryptoContext.VERSION_AES_PUBKEY, encDek);
        assertArrayEquals(TEST_DEK, unwrapped);
    }

    // =========================================================================
    // 5.2 — PassthroughCryptoConfig: encrypt/decrypt field round-trip
    // =========================================================================

    @Test
    void passthrough_encryptDecryptField_roundTrip() throws CryptoError {
        PassthroughCryptoConfig crypto = new PassthroughCryptoConfig();
        byte[] plaintext = "hello world".getBytes(StandardCharsets.UTF_8);
        CryptoConfig.EncryptedField ef = crypto.encryptField(TEST_DEK, plaintext);
        byte[] decrypted = crypto.decryptField(TEST_DEK, ef.iv(), ef.encContent());
        assertArrayEquals(plaintext, decrypted);
    }

    // =========================================================================
    // 5.3 — RowWriter.writeRowWithDek: sensitive plaintext → encrypted on wire
    // =========================================================================

    @Test
    void writeRowWithDek_sensitivePlaintext_encryptedOnWire() throws Exception {
        JsonTSchema schema = sensitiveSchema();
        PassthroughCryptoConfig crypto = new PassthroughCryptoConfig();

        JsonTRow row = JsonTRow.of(
                JsonTValue.text("Alice"),
                JsonTValue.text("123-45-6789")
        );

        StringWriter sw = new StringWriter();
        RowWriter.writeRowWithDek(row, schema.fields(), TEST_DEK, crypto, sw);
        String out = sw.toString();

        assertTrue(out.contains("\"Alice\""), "name should be plain: " + out);
        // ssn must not appear as plaintext
        assertFalse(out.contains("\"123-45-6789\""), "ssn must be encrypted: " + out);
    }

    // =========================================================================
    // 5.4 — RowWriter.writeRowWithDek: Encrypted value re-encoded as-is
    // =========================================================================

    @Test
    void writeRowWithDek_encryptedValue_reEncodedAsIs() throws Exception {
        JsonTSchema schema = sensitiveSchema();
        PassthroughCryptoConfig crypto = new PassthroughCryptoConfig();

        byte[] existingPayload = buildPayload("already-encrypted");
        JsonTRow row = JsonTRow.of(
                JsonTValue.text("Bob"),
                JsonTValue.encrypted(existingPayload)
        );

        StringWriter sw = new StringWriter();
        RowWriter.writeRowWithDek(row, schema.fields(), TEST_DEK, crypto, sw);
        String out = sw.toString();

        String expectedB64 = Base64.getEncoder().encodeToString(existingPayload);
        assertTrue(out.contains(expectedB64), "existing payload must be re-encoded as-is: " + out);
    }

    // =========================================================================
    // 5.5 — RowWriter.writeRowWithDek: non-sensitive field written normally
    // =========================================================================

    @Test
    void writeRowWithDek_nonSensitiveField_writtenNormally() throws Exception {
        JsonTSchema schema = sensitiveSchema();
        PassthroughCryptoConfig crypto = new PassthroughCryptoConfig();

        JsonTRow row = JsonTRow.of(
                JsonTValue.text("Carol"),
                JsonTValue.text("999-00-1234")
        );

        StringWriter sw = new StringWriter();
        RowWriter.writeRowWithDek(row, schema.fields(), TEST_DEK, crypto, sw);
        String out = sw.toString();

        assertTrue(out.startsWith("{\"Carol\""), "name should be plain first field: " + out);
    }

    // =========================================================================
    // 5.6 — RowWriter.writeEncryptedStream: produces header + data rows
    // =========================================================================

    @Test
    void writeEncryptedStream_producesHeaderAndDataRows() throws Exception {
        JsonTSchema schema = sensitiveSchema();
        PassthroughCryptoConfig crypto = new PassthroughCryptoConfig();

        JsonTRow row = JsonTRow.of(
                JsonTValue.text("Dave"),
                JsonTValue.text("111-22-3333")
        );

        StringWriter sw = new StringWriter();
        RowWriter.writeEncryptedStream(List.of(row), schema.fields(), crypto,
                CryptoContext.VERSION_AES_PUBKEY, sw);
        String out = sw.toString();

        assertTrue(out.contains(",\n"), "stream must separate header and data rows: " + out);
        assertTrue(out.contains("\"Dave\""), "data row name must be plain: " + out);
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
        CryptoContext ctx = testContext();
        PassthroughCryptoConfig crypto = new PassthroughCryptoConfig();

        JsonTValue val = JsonTValue.encrypted(buildPayload("hello"));
        var result = val.decryptStr("f", ctx, crypto);

        assertTrue(result.isPresent());
        assertEquals("hello", result.get());
    }

    // =========================================================================
    // 6.2 — JsonTValue.decryptStr: non-encrypted → Optional.empty()
    // =========================================================================

    @Test
    void decryptStr_onPlainString_returnsEmpty() throws Exception {
        CryptoContext ctx = testContext();
        PassthroughCryptoConfig crypto = new PassthroughCryptoConfig();

        var result = JsonTValue.text("plaintext").decryptStr("f", ctx, crypto);
        assertTrue(result.isEmpty());
    }

    // =========================================================================
    // 6.3 — JsonTValue.decryptBytes: Encrypted → bytes
    // =========================================================================

    @Test
    void decryptBytes_onEncrypted_returnsBytes() throws Exception {
        CryptoContext ctx = testContext();
        PassthroughCryptoConfig crypto = new PassthroughCryptoConfig();

        byte[] plaintext = "raw bytes".getBytes(StandardCharsets.UTF_8);
        var result = JsonTValue.encrypted(buildPayload("raw bytes")).decryptBytes("f", ctx, crypto);

        assertTrue(result.isPresent());
        assertArrayEquals(plaintext, result.get());
    }

    // =========================================================================
    // 6.4 — JsonTValue.decryptBytes: null value → Optional.empty()
    // =========================================================================

    @Test
    void decryptBytes_onNull_returnsEmpty() throws Exception {
        CryptoContext ctx = testContext();
        PassthroughCryptoConfig crypto = new PassthroughCryptoConfig();

        var result = JsonTValue.nullValue().decryptBytes("f", ctx, crypto);
        assertTrue(result.isEmpty());
    }

    // =========================================================================
    // 6.5 — JsonTValue.decryptBytes: digest mismatch → DigestMismatch
    // =========================================================================

    @Test
    void decryptBytes_digestMismatch_throws() throws Exception {
        CryptoContext ctx = testContext();
        PassthroughCryptoConfig crypto = new PassthroughCryptoConfig();

        // Assemble a payload with a deliberately wrong (all-zeros) digest.
        byte[] iv         = new byte[12];
        byte[] wrongDigest = new byte[32]; // all zeros — wrong for any non-trivial plaintext
        byte[] encContent = "data".getBytes(StandardCharsets.UTF_8);
        JsonTValue val    = JsonTValue.encrypted(FieldPayload.assemble(iv, wrongDigest, encContent));

        assertThrows(CryptoError.DigestMismatch.class,
                () -> val.decryptBytes("f", ctx, crypto));
    }

    // =========================================================================
    // 6.6 — JsonTRow.decryptField: correct index → plaintext
    // =========================================================================

    @Test
    void rowDecryptField_atValidEncryptedIndex_returnsPlaintext() throws Exception {
        CryptoContext ctx = testContext();
        PassthroughCryptoConfig crypto = new PassthroughCryptoConfig();

        JsonTRow row = JsonTRow.of(
                JsonTValue.text("Alice"),
                JsonTValue.encrypted(buildPayload("123-45-6789"))
        );

        var result = row.decryptField(1, "ssn", ctx, crypto);
        assertTrue(result.isPresent());
        assertEquals("123-45-6789", result.get());
    }

    // =========================================================================
    // 6.7 — JsonTRow.decryptField: out-of-range → IndexOutOfBoundsException
    // =========================================================================

    @Test
    void rowDecryptField_outOfRange_throwsIndexOutOfBounds() throws Exception {
        CryptoContext ctx = testContext();
        PassthroughCryptoConfig crypto = new PassthroughCryptoConfig();
        JsonTRow row = JsonTRow.of(JsonTValue.text("Alice"));

        assertThrows(IndexOutOfBoundsException.class,
                () -> row.decryptField(99, "ssn", ctx, crypto));
    }

    // =========================================================================
    // 6.8 — JsonTRow.decryptField: non-encrypted → Optional.empty()
    // =========================================================================

    @Test
    void rowDecryptField_nonEncrypted_returnsEmpty() throws Exception {
        CryptoContext ctx = testContext();
        PassthroughCryptoConfig crypto = new PassthroughCryptoConfig();

        JsonTRow row = JsonTRow.of(
                JsonTValue.text("Alice"),
                JsonTValue.text("plain ssn")
        );

        var result = row.decryptField(1, "ssn", ctx, crypto);
        assertTrue(result.isEmpty());
    }

    // =========================================================================
    // 6.9 — RowTransformer.transformWithCrypto: Decrypt op decrypts Encrypted → plain
    // =========================================================================

    @Test
    void transformWithCrypto_decryptsEncryptedField() throws Exception {
        Object[] objs = personWithDecrypt();
        JsonTSchema derived     = (JsonTSchema)    objs[1];
        SchemaRegistry registry = (SchemaRegistry) objs[2];
        CryptoContext ctx       = testContext();
        PassthroughCryptoConfig crypto = new PassthroughCryptoConfig();

        JsonTRow row = JsonTRow.of(
                JsonTValue.text("Alice"),
                JsonTValue.encrypted(buildPayload("123-45-6789"))
        );

        JsonTRow result = RowTransformer.of(derived, registry).transformWithCrypto(row, ctx, crypto);

        assertEquals(JsonTValue.text("Alice"),       result.get(0));
        assertEquals(JsonTValue.text("123-45-6789"), result.get(1));
    }

    // =========================================================================
    // 6.10 — RowTransformer.transformWithCrypto: Decrypt on plaintext is idempotent
    // =========================================================================

    @Test
    void transformWithCrypto_idempotentOnPlaintextField() throws Exception {
        Object[] objs = personWithDecrypt();
        JsonTSchema derived     = (JsonTSchema)    objs[1];
        SchemaRegistry registry = (SchemaRegistry) objs[2];
        CryptoContext ctx       = testContext();
        PassthroughCryptoConfig crypto = new PassthroughCryptoConfig();

        JsonTRow row = JsonTRow.of(
                JsonTValue.text("Bob"),
                JsonTValue.text("000-11-2222")
        );

        JsonTRow result = RowTransformer.of(derived, registry).transformWithCrypto(row, ctx, crypto);
        assertEquals(JsonTValue.text("000-11-2222"), result.get(1));
    }

    // =========================================================================
    // 6.11 — RowTransformer.transform (no crypto): Decrypt op → Transform error
    // =========================================================================

    @Test
    void transform_withoutCrypto_decryptOpThrows() throws Exception {
        Object[] objs = personWithDecrypt();
        JsonTSchema derived     = (JsonTSchema)    objs[1];
        SchemaRegistry registry = (SchemaRegistry) objs[2];

        JsonTRow row = JsonTRow.of(
                JsonTValue.text("Carol"),
                JsonTValue.encrypted(buildPayload("secret"))
        );

        RowTransformer transformer = RowTransformer.of(derived, registry);
        assertThrows(JsonTError.Transform.class, () -> transformer.transform(row));
    }

    // =========================================================================
    // 6.12 — RowTransformer.transformWithCrypto on straight schema → row unchanged
    // =========================================================================

    @Test
    void transformWithCrypto_straightSchema_rowUnchanged() throws Exception {
        JsonTSchema schema = JsonTSchemaBuilder.straight("Simple")
                .fieldFrom(JsonTFieldBuilder.scalar("x", ScalarType.I32))
                .build();
        SchemaRegistry registry = SchemaRegistry.empty().register(schema);
        CryptoContext ctx       = testContext();
        PassthroughCryptoConfig crypto = new PassthroughCryptoConfig();

        JsonTRow row    = JsonTRow.of(JsonTValue.i32(42));
        JsonTRow result = RowTransformer.of(schema, registry).transformWithCrypto(row, ctx, crypto);

        assertEquals(row.values(), result.values());
    }
}
