package io.github.datakore.jsont;

// =============================================================================
// EncryptedCrossCompatTest — Full-stream encrypted cross-compat (Java ↔ Rust)
// =============================================================================
// Verifies that a complete encrypted JsonT stream written by Java can be
// decrypted by Rust, and vice versa.  Tests the full wire format end-to-end:
//
//   EncryptHeader row  →  10 data rows with 2 sensitive (encrypted) fields
//
// Schema: CricketMatchEncrypted (11 fields, fields 4 and 7 are sensitive~)
//   0  matchId          str
//   1  matchCode        str
//   2  teamAId          str
//   3  teamAName        str
//   4  teamACoachName~  str  (always "Rahul Dravid", encrypted)
//   5  teamBId          str
//   6  teamBName        str
//   7  teamBCoachName~  str  (always "Matthew Mott", encrypted)
//   8  matchNumber      u16
//   9  prizeMoneyUsd    d128
//  10  dataSource       str
//
// Keys: RSA 2048 from code/cross-compat/keys/ (same pair as jsont-crypto tests)
// Algorithm: ChaCha20-Poly1305 + RSA-OAEP-SHA256 (VERSION_CHACHA_PUBKEY)
//
// Run order:
//   cargo test --test encrypted_cross_compat_tests   → writes rust-encrypted.jsont
//   mvn test -Dtest=EncryptedCrossCompatTest          → reads Rust file, writes java-encrypted.jsont
//   cargo test --test encrypted_cross_compat_tests   → reads Java file (verify pass)
// =============================================================================

import io.github.datakore.jsont.builder.JsonTFieldBuilder;
import io.github.datakore.jsont.builder.JsonTSchemaBuilder;
import io.github.datakore.jsont.crypto.AlgoVersion;
import io.github.datakore.jsont.crypto.CryptoContext;
import io.github.datakore.jsont.crypto.KekMode;
import io.github.datakore.jsont.crypto.PublicKeyCryptoConfig;
import io.github.datakore.jsont.internal.crypto.EncryptHeaderParser;
import io.github.datakore.jsont.internal.crypto.FieldPayload;
import io.github.datakore.jsont.model.JsonTRow;
import io.github.datakore.jsont.model.JsonTSchema;
import io.github.datakore.jsont.model.JsonTValue;
import io.github.datakore.jsont.model.ScalarType;
import io.github.datakore.jsont.stringify.RowWriter;
import io.github.datakore.jsont.validate.ValidationPipeline;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EncryptedCrossCompatTest {

    // Paths relative to Maven's working directory (module root: code/java/jsont)
    private static final Path JAVA_ENC_OUT = Path.of("../../cross-compat/java-encrypted.jsont");
    private static final Path RUST_ENC_IN  = Path.of("../../cross-compat/rust-encrypted.jsont");
    private static final Path KEYS_DIR     = Path.of("../../cross-compat/keys");

    // ── Schema ────────────────────────────────────────────────────────────────

    private static JsonTSchema encryptedSchema() throws Exception {
        return JsonTSchemaBuilder.straight("CricketMatchEncrypted")
                .fieldFrom(JsonTFieldBuilder.scalar("matchId",        ScalarType.STR))
                .fieldFrom(JsonTFieldBuilder.scalar("matchCode",      ScalarType.STR))
                .fieldFrom(JsonTFieldBuilder.scalar("teamAId",        ScalarType.STR))
                .fieldFrom(JsonTFieldBuilder.scalar("teamAName",      ScalarType.STR))
                .fieldFrom(JsonTFieldBuilder.scalar("teamACoachName", ScalarType.STR).sensitive())
                .fieldFrom(JsonTFieldBuilder.scalar("teamBId",        ScalarType.STR))
                .fieldFrom(JsonTFieldBuilder.scalar("teamBName",      ScalarType.STR))
                .fieldFrom(JsonTFieldBuilder.scalar("teamBCoachName", ScalarType.STR).sensitive())
                .fieldFrom(JsonTFieldBuilder.scalar("matchNumber",    ScalarType.U16))
                .fieldFrom(JsonTFieldBuilder.scalar("prizeMoneyUsd",  ScalarType.D128))
                .fieldFrom(JsonTFieldBuilder.scalar("dataSource",     ScalarType.STR))
                .build();
    }

    // ── Row factory ───────────────────────────────────────────────────────────

    private static JsonTRow makeEncRow(long i) {
        BigDecimal prize = new BigDecimal(100_000 + i * 10_000).movePointLeft(2);
        List<JsonTValue> v = new ArrayList<>(11);
        v.add(JsonTValue.text(String.format("cc000000-0000-0000-0000-enc%09d", i)));
        v.add(JsonTValue.text(String.format("XCOMPAT-ENCR-%02d", i)));
        v.add(JsonTValue.text("IND"));
        v.add(JsonTValue.text("India"));
        v.add(JsonTValue.text("Rahul Dravid"));          // sensitive: teamACoachName
        v.add(JsonTValue.text("ENG"));
        v.add(JsonTValue.text("England"));
        v.add(JsonTValue.text("Matthew Mott"));          // sensitive: teamBCoachName
        v.add(JsonTValue.u16((int) (1000 + i)));
        v.add(JsonTValue.d128(prize));
        v.add(JsonTValue.text("ICC API"));
        return JsonTRow.of(v.toArray(new JsonTValue[0]));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static PublicKeyCryptoConfig loadRsaCfg() throws IOException {
        String pubPem  = Files.readString(KEYS_DIR.resolve("rsa_2048_public.pem"));
        String privPem = Files.readString(KEYS_DIR.resolve("rsa_2048_private.pem"));
        return PublicKeyCryptoConfig.ofKeys(pubPem, privPem);
    }

    /** Decrypt a sensitive Encrypted field value; asserts it is Encrypted first. */
    private static String decryptSensitive(
            JsonTRow row, int idx, String fieldName, CryptoContext ctx) throws Exception {
        JsonTValue val = row.values().get(idx);
        assertInstanceOf(JsonTValue.Encrypted.class, val,
                "field[" + idx + "] (" + fieldName + ") expected Encrypted");
        byte[] envelope = ((JsonTValue.Encrypted) val).envelope();
        FieldPayload.Parsed p = FieldPayload.parse(envelope, fieldName);
        byte[] plaintext = ctx.decryptField(p.iv(), p.encContent());
        return new String(plaintext, StandardCharsets.UTF_8);
    }

    // =========================================================================
    // Gen: write java-encrypted.jsont (only if not already committed)
    // =========================================================================

    @Test
    void genJavaEncryptedJsont() throws Exception {
        if (Files.exists(JAVA_ENC_OUT)) {
            System.out.println("java-encrypted.jsont already committed — skipping generation");
            return;
        }
        Files.createDirectories(JAVA_ENC_OUT.getParent());

        PublicKeyCryptoConfig cfg = loadRsaCfg();
        JsonTSchema schema = encryptedSchema();

        List<JsonTRow> rows = new ArrayList<>(10);
        for (int i = 0; i < 10; i++) rows.add(makeEncRow(i));

        try (CryptoContext ctx = CryptoContext.forEncrypt(AlgoVersion.CHACHA20_POLY1305, KekMode.PUBLIC_KEY, cfg);
             BufferedWriter w  = Files.newBufferedWriter(JAVA_ENC_OUT, StandardCharsets.UTF_8);
             RowWriter writer  = new RowWriter(schema, ctx)) {
            writer.writeStream(rows, w);
        }

        System.out.println("Generated java-encrypted.jsont (10 rows) — commit this file");
    }

    // =========================================================================
    // Verify: decrypt rust-encrypted.jsont written by Rust test
    // =========================================================================

    @Test
    void verifyRustEncryptedJsont() throws Exception {
        assertTrue(Files.exists(RUST_ENC_IN),
                "rust-encrypted.jsont not found — run 'cargo test --test encrypted_cross_compat_tests' first and commit the file");

        String content = Files.readString(RUST_ENC_IN, StandardCharsets.UTF_8);
        List<JsonTRow> allRows = new ArrayList<>();
        JsonT.parseRows(content, allRows::add);
        assertFalse(allRows.isEmpty(), "rust-encrypted.jsont has no rows");

        // Row 0 must be the EncryptHeader.
        EncryptHeaderParser.ParsedHeader header = EncryptHeaderParser.tryParse(allRows.get(0))
                .orElseThrow(() -> new AssertionError(
                        "rust-encrypted.jsont: first row is not a valid EncryptHeader"));

        PublicKeyCryptoConfig cfg = loadRsaCfg();
        JsonTSchema schema = encryptedSchema();

        List<JsonTRow> dataRows = allRows.subList(1, allRows.size());
        assertEquals(10, dataRows.size(), "expected 10 data rows in rust-encrypted.jsont");

        try (CryptoContext ctx = CryptoContext.forDecrypt(header.version(), header.encDek(), cfg)) {
            ValidationPipeline pipeline = ValidationPipeline.builder(schema)
                    .withCryptoContext(ctx)
                    .build();

            List<JsonTRow> cleanRows = pipeline.validateRows(dataRows);
            assertEquals(10, cleanRows.size(), "all rows should pass validation");

            for (int i = 0; i < cleanRows.size(); i++) {
                JsonTRow row = cleanRows.get(i);

                String coachA = decryptSensitive(row, 4, "teamACoachName", ctx);
                assertEquals("Rahul Dravid", coachA, "row " + i + ": teamACoachName mismatch");

                String coachB = decryptSensitive(row, 7, "teamBCoachName", ctx);
                assertEquals("Matthew Mott", coachB, "row " + i + ": teamBCoachName mismatch");
            }
        }

        System.out.println("verifyRustEncryptedJsont: all 10 rows decrypted and verified ✓");
    }
}
