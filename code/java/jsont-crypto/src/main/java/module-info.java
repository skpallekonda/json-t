/**
 * JsonT-Crypto — RSA-OAEP + AES-256-GCM encryption support for JsonT.
 *
 * <p>Provides the {@code CryptoConfig} interface and built-in implementations:
 * <ul>
 *   <li>{@code PassthroughCryptoConfig} — identity (no encryption)</li>
 *   <li>{@code EnvCryptoConfig} — RSA-OAEP-SHA256 key wrap + AES-256-GCM
 *       with key material read from environment variables at call time</li>
 * </ul>
 */
module io.github.datakore.jsont.crypto {

    exports io.github.datakore.jsont.crypto;

    requires org.bouncycastle.provider;
    requires org.bouncycastle.pkix;
}
