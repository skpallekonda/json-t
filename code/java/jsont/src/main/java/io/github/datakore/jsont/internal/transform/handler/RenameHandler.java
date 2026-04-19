package io.github.datakore.jsont.internal.transform.handler;

import io.github.datakore.jsont.crypto.CryptoContext;
import io.github.datakore.jsont.error.JsonTError;
import io.github.datakore.jsont.model.JsonTValue;
import io.github.datakore.jsont.model.RenamePair;
import io.github.datakore.jsont.model.SchemaOperation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Handles {@link SchemaOperation.Rename}: renames one or more fields in declaration order. */
public final class RenameHandler implements RowOperationHandler, FieldResolutionHandler {

    public static final RenameHandler INSTANCE = new RenameHandler();

    private RenameHandler() {}

    @Override
    public boolean supports(SchemaOperation op) {
        return op instanceof SchemaOperation.Rename;
    }

    @Override
    public LinkedHashMap<String, JsonTValue> apply(
            SchemaOperation op,
            LinkedHashMap<String, JsonTValue> working,
            CryptoContext ctx) throws JsonTError.Transform {
        SchemaOperation.Rename rename = (SchemaOperation.Rename) op;
        for (RenamePair pair : rename.pairs()) {
            String oldKey = pair.from().leaf();
            if (!working.containsKey(oldKey)) throw new JsonTError.Transform.FieldNotFound(oldKey);
            LinkedHashMap<String, JsonTValue> rebuilt = new LinkedHashMap<>();
            for (Map.Entry<String, JsonTValue> e : working.entrySet()) {
                rebuilt.put(e.getKey().equals(oldKey) ? pair.to() : e.getKey(), e.getValue());
            }
            working = rebuilt;
        }
        return working;
    }

    @Override
    public List<String> apply(SchemaOperation op, List<String> fieldNames) {
        SchemaOperation.Rename rename = (SchemaOperation.Rename) op;
        List<String> result = new ArrayList<>(fieldNames);
        for (RenamePair pair : rename.pairs()) {
            int idx = result.indexOf(pair.from().leaf());
            if (idx >= 0) result.set(idx, pair.to());
        }
        return result;
    }
}
