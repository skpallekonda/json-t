package io.github.datakore.jsont.transform;

import io.github.datakore.jsont.builder.SchemaRegistry;
import io.github.datakore.jsont.crypto.CryptoConfig;
import io.github.datakore.jsont.error.JsonTError;
import io.github.datakore.jsont.internal.transform.OperationApplicator;
import io.github.datakore.jsont.internal.validate.SchemaValidator;
import io.github.datakore.jsont.model.JsonTRow;
import io.github.datakore.jsont.model.JsonTSchema;
import io.github.datakore.jsont.model.JsonTValue;
import io.github.datakore.jsont.model.SchemaKind;
import io.github.datakore.jsont.model.SchemaOperation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

public final class RowTransformer {

    private final JsonTSchema schema;
    private final SchemaRegistry registry;

    private RowTransformer(JsonTSchema schema, SchemaRegistry registry) {
        this.schema = schema;
        this.registry = registry;
    }

    public static RowTransformer of(JsonTSchema schema, SchemaRegistry registry) {
        return new RowTransformer(schema, registry);
    }

    public JsonTRow transform(JsonTRow row) throws JsonTError.Transform {
        if (schema.kind() == SchemaKind.STRAIGHT) {
            return row;
        }

        // DERIVED
        String from = schema.derivedFrom().get();
        JsonTSchema parent = registry.resolve(from)
                .orElseThrow(() -> new JsonTError.Transform.UnknownSchema(from));

        List<String> chain = new ArrayList<>(List.of(schema.name()));
        List<String> parentFields = OperationApplicator.resolveEffectiveFields(parent, registry, chain);

        if (parentFields.size() != row.values().size()) {
            throw new JsonTError.Transform.FieldNotFound(
                    "row has " + row.values().size() + " values but parent schema has "
                            + parentFields.size() + " output fields");
        }

        // Build working map
        LinkedHashMap<String, JsonTValue> working = new LinkedHashMap<>();
        for (int i = 0; i < parentFields.size(); i++) {
            working.put(parentFields.get(i), row.values().get(i));
        }

        // Apply operations (no CryptoConfig — Decrypt ops will throw if present)
        for (SchemaOperation op : schema.operations()) {
            working = OperationApplicator.applyOperation(op, working, null);
        }

        return JsonTRow.at(row.index(), new ArrayList<>(working.values()));
    }

    /**
     * Transform one row with a {@link CryptoConfig} so that {@code decrypt(...)}
     * operations can actually decrypt their fields.
     *
     * <p>Identical to {@link #transform(JsonTRow)} except that {@code Decrypt}
     * operations call {@code crypto.decrypt()} rather than throwing.
     *
     * @param row    the row to transform (must match parent schema layout)
     * @param crypto the crypto implementation to use for decryption
     * @return the transformed row
     * @throws JsonTError.Transform on any structural or crypto failure
     */
    public JsonTRow transformWithCrypto(JsonTRow row, CryptoConfig crypto) throws JsonTError.Transform {
        if (schema.kind() == SchemaKind.STRAIGHT) {
            return row;
        }

        String from = schema.derivedFrom().get();
        JsonTSchema parent = registry.resolve(from)
                .orElseThrow(() -> new JsonTError.Transform.UnknownSchema(from));

        List<String> chain = new ArrayList<>(List.of(schema.name()));
        List<String> parentFields = OperationApplicator.resolveEffectiveFields(parent, registry, chain);

        if (parentFields.size() != row.values().size()) {
            throw new JsonTError.Transform.FieldNotFound(
                    "row has " + row.values().size() + " values but parent schema has "
                            + parentFields.size() + " output fields");
        }

        LinkedHashMap<String, JsonTValue> working = new LinkedHashMap<>();
        for (int i = 0; i < parentFields.size(); i++) {
            working.put(parentFields.get(i), row.values().get(i));
        }

        for (SchemaOperation op : schema.operations()) {
            working = OperationApplicator.applyOperation(op, working, crypto);
        }

        return JsonTRow.at(row.index(), new ArrayList<>(working.values()));
    }

    public void validateSchema() throws JsonTError.SchemaInvalid {
        // Build a plain map from the registry so SchemaValidator can operate
        // without a circular package dependency.
        LinkedHashMap<String, JsonTSchema> map = new LinkedHashMap<>();
        for (String name : registry.names()) {
            map.put(name, registry.resolveOrThrow(name));
        }
        SchemaValidator.validate(schema, map);
    }
}
