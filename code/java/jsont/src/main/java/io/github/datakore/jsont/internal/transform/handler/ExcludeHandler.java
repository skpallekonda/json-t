package io.github.datakore.jsont.internal.transform.handler;

import io.github.datakore.jsont.crypto.CryptoContext;
import io.github.datakore.jsont.error.JsonTError;
import io.github.datakore.jsont.model.FieldPath;
import io.github.datakore.jsont.model.JsonTValue;
import io.github.datakore.jsont.model.SchemaOperation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/** Handles {@link SchemaOperation.Exclude}: drops listed fields from the row. */
public final class ExcludeHandler implements RowOperationHandler, FieldResolutionHandler {

    public static final ExcludeHandler INSTANCE = new ExcludeHandler();

    private ExcludeHandler() {}

    @Override
    public boolean supports(SchemaOperation op) {
        return op instanceof SchemaOperation.Exclude;
    }

    @Override
    public LinkedHashMap<String, JsonTValue> apply(
            SchemaOperation op,
            LinkedHashMap<String, JsonTValue> working,
            CryptoContext ctx) throws JsonTError.Transform {
        SchemaOperation.Exclude exclude = (SchemaOperation.Exclude) op;
        for (FieldPath path : exclude.paths()) {
            String key = path.dotJoined();
            if (!working.containsKey(key)) throw new JsonTError.Transform.FieldNotFound(key);
            working.remove(key);
        }
        return working;
    }

    @Override
    public List<String> apply(SchemaOperation op, List<String> fieldNames) {
        SchemaOperation.Exclude exclude = (SchemaOperation.Exclude) op;
        List<String> result = new ArrayList<>(fieldNames);
        exclude.paths().forEach(p -> result.remove(p.dotJoined()));
        return result;
    }
}
