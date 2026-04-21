package io.github.datakore.jsont.internal.transform.handler;

import io.github.datakore.jsont.crypto.CryptoContext;
import io.github.datakore.jsont.crypto.CryptoError;
import io.github.datakore.jsont.error.JsonTError;
import io.github.datakore.jsont.model.JsonTValue;
import io.github.datakore.jsont.model.SchemaOperation;

import java.util.LinkedHashMap;

/**
 * Handles {@link SchemaOperation.Decrypt}: decrypts named fields in-place using the stream DEK.
 * Already-plaintext fields are skipped (idempotent). Requires a non-null {@link CryptoContext}.
 */
public final class DecryptHandler implements RowOperationHandler {

    public static final DecryptHandler INSTANCE = new DecryptHandler();

    private DecryptHandler() {}

    @Override
    public boolean supports(SchemaOperation op) {
        return op instanceof SchemaOperation.Decrypt;
    }

    @Override
    public LinkedHashMap<String, JsonTValue> apply(
            SchemaOperation op,
            LinkedHashMap<String, JsonTValue> working,
            CryptoContext ctx) throws JsonTError.Transform {
        if (ctx == null) {
            throw new JsonTError.Transform.DecryptFailed("",
                    "Decrypt operation requires a CryptoContext; " +
                    "use transformWithContext instead of transform");
        }
        SchemaOperation.Decrypt decrypt = (SchemaOperation.Decrypt) op;
        for (String fieldName : decrypt.fields()) {
            if (!working.containsKey(fieldName))
                throw new JsonTError.Transform.FieldNotFound(fieldName);
            JsonTValue current = working.get(fieldName);
            if (current instanceof JsonTValue.Encrypted) {
                try {
                    String text = current.decryptStr(fieldName, ctx)
                            .orElseThrow(() -> new JsonTError.Transform.DecryptFailed(
                                    fieldName, "Encrypted value returned empty from decryptStr"));
                    working.put(fieldName, JsonTValue.text(text));
                } catch (CryptoError e) {
                    throw new JsonTError.Transform.DecryptFailed(fieldName, e.getMessage());
                }
            }
            // Already plaintext — idempotent, leave unchanged.
        }
        return working;
    }
}
