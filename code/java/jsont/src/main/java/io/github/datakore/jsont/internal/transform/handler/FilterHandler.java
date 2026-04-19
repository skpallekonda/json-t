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

/**
 * Handles {@link SchemaOperation.Filter}: drops rows where the predicate is false.
 * Raises {@link JsonTError.Transform.Filtered} as a row-skip signal (not a hard error).
 */
public final class FilterHandler implements RowOperationHandler {

    public static final FilterHandler INSTANCE = new FilterHandler();

    private FilterHandler() {}

    @Override
    public boolean supports(SchemaOperation op) {
        return op instanceof SchemaOperation.Filter;
    }

    @Override
    public LinkedHashMap<String, JsonTValue> apply(
            SchemaOperation op,
            LinkedHashMap<String, JsonTValue> working,
            CryptoContext ctx) throws JsonTError.Transform {
        SchemaOperation.Filter filter = (SchemaOperation.Filter) op;
        Set<String> needed = new HashSet<>(FieldRefCollector.collect(filter.predicate()));
        EvalContext evalCtx = EvalContext.create();
        for (Map.Entry<String, JsonTValue> entry : working.entrySet()) {
            if (needed.contains(entry.getKey())) evalCtx.bind(entry.getKey(), entry.getValue());
        }
        try {
            JsonTValue result = filter.predicate().evaluate(evalCtx);
            if (result instanceof JsonTValue.Bool b) {
                if (b.value()) return working;
                throw new JsonTError.Transform.Filtered();
            }
            throw new JsonTError.Transform(
                    "filter expression returned non-boolean: " + result.getClass().getSimpleName());
        } catch (JsonTError.Eval e) {
            throw new JsonTError.Transform("filter evaluation failed: " + e.getMessage(), e);
        }
    }
}
