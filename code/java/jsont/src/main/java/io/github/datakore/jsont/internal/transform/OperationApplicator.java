package io.github.datakore.jsont.internal.transform;

import io.github.datakore.jsont.builder.SchemaResolver;
import io.github.datakore.jsont.crypto.CryptoContext;
import io.github.datakore.jsont.error.JsonTError;
import io.github.datakore.jsont.internal.transform.handler.DecryptHandler;
import io.github.datakore.jsont.internal.transform.handler.ExcludeHandler;
import io.github.datakore.jsont.internal.transform.handler.FieldRefCollector;
import io.github.datakore.jsont.internal.transform.handler.FieldResolutionHandler;
import io.github.datakore.jsont.internal.transform.handler.FilterHandler;
import io.github.datakore.jsont.internal.transform.handler.ProjectHandler;
import io.github.datakore.jsont.internal.transform.handler.RenameHandler;
import io.github.datakore.jsont.internal.transform.handler.RowOperationHandler;
import io.github.datakore.jsont.internal.transform.handler.TransformHandler;
import io.github.datakore.jsont.model.JsonTExpression;
import io.github.datakore.jsont.model.JsonTField;
import io.github.datakore.jsont.model.JsonTSchema;
import io.github.datakore.jsont.model.JsonTValue;
import io.github.datakore.jsont.model.SchemaKind;
import io.github.datakore.jsont.model.SchemaOperation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Dispatches each {@link SchemaOperation} to a dedicated {@link RowOperationHandler}.
 *
 * <p>Adding a new operation type only requires implementing {@link RowOperationHandler}
 * (and optionally {@link FieldResolutionHandler}) and registering the handler below.
 */
public class OperationApplicator {

    private static final List<RowOperationHandler> ROW_HANDLERS = List.of(
            RenameHandler.INSTANCE,
            ExcludeHandler.INSTANCE,
            ProjectHandler.INSTANCE,
            FilterHandler.INSTANCE,
            TransformHandler.INSTANCE,
            DecryptHandler.INSTANCE
    );

    private static final List<FieldResolutionHandler> FIELD_HANDLERS = List.of(
            RenameHandler.INSTANCE,
            ExcludeHandler.INSTANCE,
            ProjectHandler.INSTANCE
            // Filter, Transform, Decrypt do not change the field-name shape
    );

    /**
     * Apply one operation to the working field map.
     *
     * @param op      the operation to apply
     * @param working the current (name → value) state
     * @param ctx     stream-level {@link CryptoContext} — required for Decrypt; {@code null} otherwise
     * @return the updated working map
     * @throws JsonTError.Transform on structural or crypto failure
     */
    public static LinkedHashMap<String, JsonTValue> applyOperation(
            SchemaOperation op,
            LinkedHashMap<String, JsonTValue> working,
            CryptoContext ctx) throws JsonTError.Transform {

        for (RowOperationHandler handler : ROW_HANDLERS) {
            if (handler.supports(op)) return handler.apply(op, working, ctx);
        }
        return working; // unknown op type — pass through unchanged
    }

    /**
     * Resolve the ordered list of output field names produced by {@code schema}, walking
     * the derivation chain via {@code registry} and applying shape-changing operations.
     */
    public static List<String> resolveEffectiveFields(
            JsonTSchema schema,
            SchemaResolver registry,
            List<String> chain) throws JsonTError.Transform {

        if (chain.contains(schema.name())) {
            chain.add(schema.name());
            throw new JsonTError.Transform.CyclicDerivation(chain.toString());
        }
        chain.add(schema.name());

        if (schema.kind() == SchemaKind.STRAIGHT) {
            return schema.fields().stream()
                    .map(JsonTField::name)
                    .collect(Collectors.toCollection(ArrayList::new));
        }

        String from = schema.derivedFrom().get();
        JsonTSchema parent = registry.resolve(from)
                .orElseThrow(() -> new JsonTError.Transform.UnknownSchema(from));

        List<String> fieldNames = resolveEffectiveFields(parent, registry, chain);

        for (SchemaOperation op : schema.operations()) {
            for (FieldResolutionHandler handler : FIELD_HANDLERS) {
                if (handler.supports(op)) {
                    fieldNames = handler.apply(op, fieldNames);
                    break;
                }
            }
        }
        return fieldNames;
    }

    /** Collect all field names referenced in an expression tree. */
    public static List<String> collectFieldRefs(JsonTExpression expr) {
        return FieldRefCollector.collect(expr);
    }
}
