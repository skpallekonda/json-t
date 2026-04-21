package io.github.datakore.jsont.crypto;

// =============================================================================
// CrossCompatTest — Java ↔ Rust cross-compatibility
// =============================================================================
// Each language generates fixture files and verifies the other language's fixtures.
//
// Convention (ECDH roles):
//   Rust = party A: host_priv = ec_a_private.pem, peer_pub = ec_b_public.der
//   Java = party B: host_priv = ec_b_private.pem, peer_pub = ec_a_public.der
//   ECDH symmetry: ECDH(a_priv, b_pub) == ECDH(b_priv, a_pub) ✓
//
// Run order (CI):
//   cargo test --test cross_compat_tests   → writes fixtures/rust_*.txt
//   mvn test -Dtest=CrossCompatTest        → reads Rust fixtures, writes fixtures/java_*.txt
//   cargo test --test cross_compat_tests   → reads Java fixtures (verify step)
//
// Algorithm: ChaCha20-Poly1305 (VERSION_CHACHA_PUBKEY=0x0010, VERSION_CHACHA_ECDH=0x0011)
// =============================================================================

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CrossCompatTest {

    // ── Path helpers ──────────────────────────────────────────────────────────

    /** Resolves the cross-compat directory relative to the Maven module basedir. */
    private static Path crossCompatDir() {
        // Maven sets project.basedir via surefire systemPropertyVariables.
        // Fallback to user.dir if not set.
        String base = System.getProperty("project.basedir",
                System.getProperty("user.dir", "."));
        return Paths.get(base, "..", "..", "..", "code", "cross-compat")
                    .normalize()
                    .toAbsolutePath();
    }

    private static Path keysDir()    { return crossCompatDir().resolve("keys"); }
    private static Path fixturesDir() {
        Path d = crossCompatDir().resolve("fixtures");
        try { Files.createDirectories(d); } catch (IOException ignored) {}
        return d;
    }

    // ── Fixture I/O ───────────────────────────────────────────────────────────

    private static void writeFixture(Path path, Map<String, String> fields) throws IOException {
        StringBuilder sb = new StringBuilder();
        fields.forEach((k, v) -> sb.append(k).append('=').append(v).append('\n'));
        Files.writeString(path, sb.toString().stripTrailing(), StandardCharsets.UTF_8);
    }

    private static Map<String, String> readFixture(Path path) throws IOException {
        Map<String, String> map = new LinkedHashMap<>();
        for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
            int eq = line.indexOf('=');
            if (eq > 0) map.put(line.substring(0, eq), line.substring(eq + 1));
        }
        return map;
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) sb.append(String.format("%02x", b & 0xFF));
        return sb.toString();
    }

    private static byte[] fromHex(String hex) {
        byte[] out = new byte[hex.length() / 2];
        for (int i = 0; i < out.length; i++)
            out[i] = (byte) Integer.parseInt(hex, i * 2, i * 2 + 2, 16);
        return out;
    }

    // ── RSA + ChaCha20 ────────────────────────────────────────────────────────

    /**
     * Java encrypts with RSA public key + ChaCha20; writes fixture for Rust to decrypt.
     */
    @Test
    void genJavaRsaChaCha20Fixture() throws Exception {
        Path keys    = keysDir();
        String pubPem  = Files.readString(keys.resolve("rsa_2048_public.pem"));
        String privPem = Files.readString(keys.resolve("rsa_2048_private.pem"));

        PublicKeyCryptoConfig cfg = PublicKeyCryptoConfig.ofKeys(pubPem, privPem);

        byte[] plaintext = "cross-compat: java->rust RSA+ChaCha20".getBytes(StandardCharsets.UTF_8);
        byte[] dek       = new byte[32];
        for (int i = 0; i < dek.length; i++) dek[i] = (byte) (i + 65); // 65..96

        byte[] encDek = cfg.wrapDek(CryptoContext.VERSION_CHACHA_PUBKEY, dek);
        try (CryptoContext ctx = CryptoContext.forDecrypt(CryptoContext.VERSION_CHACHA_PUBKEY, encDek, cfg)) {
            EncryptedField ef = ctx.encryptField(plaintext);

            // Verify own round-trip before writing.
            byte[] dec = ctx.decryptField(ef);
            assertArrayEquals(plaintext, dec, "Java RSA self round-trip failed");

            Path fixturePath = fixturesDir().resolve("java_rsa_chacha20.txt");
            if (!Files.exists(fixturePath)) {
                Map<String, String> fields = new LinkedHashMap<>();
                fields.put("generator",     "java");
                fields.put("scenario",      "rsa_chacha20");
                fields.put("version",       String.valueOf(CryptoContext.VERSION_CHACHA_PUBKEY));
                fields.put("plaintext_hex", toHex(plaintext));
                fields.put("enc_dek_hex",   toHex(encDek));
                fields.put("iv_hex",        toHex(ef.iv()));
                fields.put("ciphertext_hex",toHex(ef.encContent()));
                writeFixture(fixturePath, fields);
                System.out.println("Generated java_rsa_chacha20.txt — commit this file");
            }
        }
    }

    /**
     * Java decrypts the fixture written by Rust (RSA + ChaCha20).
     * Skipped gracefully when the Rust fixture does not exist yet.
     */
    @Test
    void verifyRustRsaChaCha20Fixture() throws Exception {
        Path fixturePath = fixturesDir().resolve("rust_rsa_chacha20.txt");
        assertTrue(Files.exists(fixturePath),
            "rust_rsa_chacha20.txt not found — run 'cargo test --test cross_compat_tests' once and commit the file");

        Map<String, String> f = readFixture(fixturePath);
        Path keys = keysDir();
        String pubPem  = Files.readString(keys.resolve("rsa_2048_public.pem"));
        String privPem = Files.readString(keys.resolve("rsa_2048_private.pem"));

        PublicKeyCryptoConfig cfg = PublicKeyCryptoConfig.ofKeys(pubPem, privPem);

        int    version  = Integer.parseInt(f.get("version"));
        byte[] encDek   = fromHex(f.get("enc_dek_hex"));
        byte[] iv       = fromHex(f.get("iv_hex"));
        byte[] cipher   = fromHex(f.get("ciphertext_hex"));
        byte[] expected = fromHex(f.get("plaintext_hex"));

        try (CryptoContext ctx = CryptoContext.forDecrypt(version, encDek, cfg)) {
            byte[] recovered = ctx.decryptField(iv, cipher);
            assertArrayEquals(expected, recovered,
                    "Java failed to decrypt Rust RSA+ChaCha20 fixture");
            System.out.println("PASS: decrypted Rust RSA+ChaCha20 → \""
                    + new String(recovered, StandardCharsets.UTF_8) + "\"");
        }
    }

    // ── ECDH + ChaCha20 ──────────────────────────────────────────────────────

    /**
     * Java (party B) encrypts with ECDH + ChaCha20; writes fixture for Rust (party A) to decrypt.
     * Java uses: peer_pub = ec_a_public.der, host_priv = ec_b_private.pem
     * Rust uses: peer_pub = ec_b_public.der, host_priv = ec_a_private.pem (same ECDH secret)
     */
    @Test
    void genJavaEcdhChaCha20Fixture() throws Exception {
        Path keys = keysDir();
        byte[] ecAPubDer  = Files.readAllBytes(keys.resolve("ec_a_public.der"));
        String ecBPrivPem = Files.readString(keys.resolve("ec_b_private.pem"));

        EcdhCryptoConfig cfg = EcdhCryptoConfig.ofKeys(ecAPubDer, ecBPrivPem);

        byte[] plaintext = "cross-compat: java->rust ECDH+ChaCha20".getBytes(StandardCharsets.UTF_8);
        byte[] dek       = new byte[32];
        for (int i = 0; i < dek.length; i++) dek[i] = (byte) (i + 97); // 97..128

        byte[] encDek = cfg.wrapDek(CryptoContext.VERSION_CHACHA_ECDH, dek);
        try (CryptoContext ctx = CryptoContext.forDecrypt(CryptoContext.VERSION_CHACHA_ECDH, encDek, cfg)) {
            EncryptedField ef = ctx.encryptField(plaintext);

            // Verify own round-trip before writing.
            byte[] dec = ctx.decryptField(ef);
            assertArrayEquals(plaintext, dec, "Java ECDH self round-trip failed");

            Path fixturePath = fixturesDir().resolve("java_ecdh_chacha20.txt");
            if (!Files.exists(fixturePath)) {
                Map<String, String> fields = new LinkedHashMap<>();
                fields.put("generator",     "java");
                fields.put("scenario",      "ecdh_chacha20");
                fields.put("version",       String.valueOf(CryptoContext.VERSION_CHACHA_ECDH));
                fields.put("plaintext_hex", toHex(plaintext));
                fields.put("enc_dek_hex",   toHex(encDek));
                fields.put("iv_hex",        toHex(ef.iv()));
                fields.put("ciphertext_hex",toHex(ef.encContent()));
                writeFixture(fixturePath, fields);
                System.out.println("Generated java_ecdh_chacha20.txt — commit this file");
            }
        }
    }

    /**
     * Java (party B) decrypts the ECDH fixture written by Rust (party A).
     * Rust used: peer_pub = ec_b_public.der, host_priv = ec_a_private.pem
     * Java uses: peer_pub = ec_a_public.der, host_priv = ec_b_private.pem (same ECDH secret)
     */
    @Test
    void verifyRustEcdhChaCha20Fixture() throws Exception {
        Path fixturePath = fixturesDir().resolve("rust_ecdh_chacha20.txt");
        assertTrue(Files.exists(fixturePath),
            "rust_ecdh_chacha20.txt not found — run 'cargo test --test cross_compat_tests' once and commit the file");

        Map<String, String> f = readFixture(fixturePath);
        Path keys = keysDir();
        byte[] ecAPubDer  = Files.readAllBytes(keys.resolve("ec_a_public.der"));
        String ecBPrivPem = Files.readString(keys.resolve("ec_b_private.pem"));

        EcdhCryptoConfig cfg = EcdhCryptoConfig.ofKeys(ecAPubDer, ecBPrivPem);

        int    version  = Integer.parseInt(f.get("version"));
        byte[] encDek   = fromHex(f.get("enc_dek_hex"));
        byte[] iv       = fromHex(f.get("iv_hex"));
        byte[] cipher   = fromHex(f.get("ciphertext_hex"));
        byte[] expected = fromHex(f.get("plaintext_hex"));

        try (CryptoContext ctx = CryptoContext.forDecrypt(version, encDek, cfg)) {
            byte[] recovered = ctx.decryptField(iv, cipher);
            assertArrayEquals(expected, recovered,
                    "Java failed to decrypt Rust ECDH+ChaCha20 fixture");
            System.out.println("PASS: decrypted Rust ECDH+ChaCha20 → \""
                    + new String(recovered, StandardCharsets.UTF_8) + "\"");
        }
    }
}
