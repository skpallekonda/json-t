package io.github.datakore.jsont.internal.transform.handler;

import io.github.datakore.jsont.crypto.CryptoContext;
import io.github.datakore.jsont.error.JsonTError;
import io.github.datakore.jsont.model.EvalContext;
import io.github.datakore.jsont.model.JsonTValue;
import io.github.datakore.jsont.model.SchemaOperation;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Handles {@link SchemaOperation.Transform}: replaces a field's value with an expression result. */
public final class TransformHandler implements RowOperationHandler {

    public static final TransformHandler INSTANCE = new TransformHandler();

    private TransformHandler() {}

    @Override
    public boolean supports(SchemaOperation op) {
        return op instanceof SchemaOperation.Transform;
    }

    @Override
    public LinkedHashMap<String, JsonTValue> apply(
            SchemaOperation op,
            LinkedHashMap<String, JsonTValue> working,
            CryptoContext ctx) throws JsonTError.Transform {
        SchemaOperation.Transform transform = (SchemaOperation.Transform) op;
        String key = transform.target().dotJoined();
        if (!working.containsKey(key)) throw new JsonTError.Transform.FieldNotFound(key);
        Set<String> needed = new HashSet<>(FieldRefCollector.collect(transform.expr()));
        EvalContext evalCtx = EvalContext.create();
        for (Map.Entry<String, JsonTValue> entry : working.entrySet()) {
            if (needed.contains(entry.getKey())) evalCtx.bind(entry.getKey(), entry.getValue());
        }
        try {
            working.put(key, transform.expr().evaluate(evalCtx));
            return working;
        } catch (JsonTError.Eval e) {
            throw new JsonTError.Transform("transform failed for '" + key + "': " + e.getMessage(), e);
        }
    }
}
