package io.github.datakore.jsont.internal.transform.handler;

import io.github.datakore.jsont.crypto.CryptoContext;
import io.github.datakore.jsont.error.JsonTError;
import io.github.datakore.jsont.model.JsonTValue;
import io.github.datakore.jsont.model.SchemaOperation;

import java.util.LinkedHashMap;

/** Applies one {@link SchemaOperation} type to the working field map of a row. */
public interface RowOperationHandler {

    /** Returns {@code true} if this handler can process {@code op}. */
    boolean supports(SchemaOperation op);

    /**
     * Apply the operation, returning the updated working map.
     *
     * @param op      the operation to apply (matches {@link #supports})
     * @param working the current mutable field state (name → value)
     * @param ctx     stream-level crypto context; {@code null} when no Decrypt ops are present
     * @return the updated field map (may be the same instance or a new one)
     * @throws JsonTError.Transform on structural or crypto failure
     */
    LinkedHashMap<String, JsonTValue> apply(
            SchemaOperation op,
            LinkedHashMap<String, JsonTValue> working,
            CryptoContext ctx) throws JsonTError.Transform;
}
