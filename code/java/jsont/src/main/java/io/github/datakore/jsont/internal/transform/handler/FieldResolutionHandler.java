package io.github.datakore.jsont.internal.transform.handler;

import io.github.datakore.jsont.model.SchemaOperation;

import java.util.List;

/** Computes the effect of one {@link SchemaOperation} on the ordered list of field names. */
public interface FieldResolutionHandler {

    /** Returns {@code true} if this handler applies to {@code op}. */
    boolean supports(SchemaOperation op);

    /**
     * Return the field-name list after applying {@code op}.
     *
     * @param op         the operation (matches {@link #supports})
     * @param fieldNames the current ordered field names (must not be mutated directly)
     * @return the updated ordered field names
     */
    List<String> apply(SchemaOperation op, List<String> fieldNames);
}
