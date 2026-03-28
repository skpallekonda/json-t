package io.github.datakore.jsont.internal.transform;

import io.github.datakore.jsont.builder.SchemaRegistry;
import io.github.datakore.jsont.error.JsonTError;
import io.github.datakore.jsont.model.BinaryOp;
import io.github.datakore.jsont.model.EvalContext;
import io.github.datakore.jsont.model.FieldPath;
import io.github.datakore.jsont.model.JsonTExpression;
import io.github.datakore.jsont.model.JsonTField;
import io.github.datakore.jsont.model.JsonTSchema;
import io.github.datakore.jsont.model.JsonTValue;
import io.github.datakore.jsont.model.RenamePair;
import io.github.datakore.jsont.model.SchemaKind;
import io.github.datakore.jsont.model.SchemaOperation;
import io.github.datakore.jsont.model.UnaryOp;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class OperationApplicator {

    public static LinkedHashMap<String, JsonTValue> applyOperation(
            SchemaOperation op,
            LinkedHashMap<String, JsonTValue> working) throws JsonTError.Transform {

        if (op instanceof SchemaOperation.Rename rename) {
            for (RenamePair pair : rename.pairs()) {
                String oldKey = pair.from().leaf();
                if (!working.containsKey(oldKey)) {
                    throw new JsonTError.Transform.FieldNotFound(oldKey);
                }
                // Rebuild map renaming that key
                LinkedHashMap<String, JsonTValue> rebuilt = new LinkedHashMap<>();
                for (Map.Entry<String, JsonTValue> entry : working.entrySet()) {
                    if (entry.getKey().equals(oldKey)) {
                        rebuilt.put(pair.to(), entry.getValue());
                    } else {
                        rebuilt.put(entry.getKey(), entry.getValue());
                    }
                }
                working = rebuilt;
            }
            return working;
        }

        if (op instanceof SchemaOperation.Exclude exclude) {
            for (FieldPath path : exclude.paths()) {
                String key = path.leaf();
                if (!working.containsKey(key)) {
                    throw new JsonTError.Transform.FieldNotFound(key);
                }
                working.remove(key);
            }
            return working;
        }

        if (op instanceof SchemaOperation.Project project) {
            for (FieldPath path : project.paths()) {
                if (!working.containsKey(path.leaf())) {
                    throw new JsonTError.Transform.FieldNotFound(path.leaf());
                }
            }
            LinkedHashMap<String, JsonTValue> projected = new LinkedHashMap<>();
            for (FieldPath path : project.paths()) {
                projected.put(path.leaf(), working.get(path.leaf()));
            }
            return projected;
        }

        if (op instanceof SchemaOperation.Filter filter) {
            // Bind only the fields referenced by the predicate expression.
            Set<String> needed = new HashSet<>(collectFieldRefs(filter.predicate()));
            EvalContext ctx = EvalContext.create();
            for (Map.Entry<String, JsonTValue> entry : working.entrySet()) {
                if (needed.contains(entry.getKey())) ctx.bind(entry.getKey(), entry.getValue());
            }
            try {
                JsonTValue result = filter.predicate().evaluate(ctx);
                if (result instanceof JsonTValue.Bool b) {
                    if (b.value()) {
                        return working;
                    } else {
                        throw new JsonTError.Transform.Filtered();
                    }
                } else {
                    throw new JsonTError.Transform(
                            "filter expression returned non-boolean: " + result.getClass().getSimpleName());
                }
            } catch (JsonTError.Eval e) {
                throw new JsonTError.Transform("filter evaluation failed: " + e.getMessage(), e);
            }
        }

        if (op instanceof SchemaOperation.Transform transform) {
            String key = transform.target().leaf();
            if (!working.containsKey(key)) {
                throw new JsonTError.Transform.FieldNotFound(key);
            }
            // Bind only the fields referenced by the transform expression.
            Set<String> needed = new HashSet<>(collectFieldRefs(transform.expr()));
            EvalContext ctx = EvalContext.create();
            for (Map.Entry<String, JsonTValue> entry : working.entrySet()) {
                if (needed.contains(entry.getKey())) ctx.bind(entry.getKey(), entry.getValue());
            }
            try {
                JsonTValue newVal = transform.expr().evaluate(ctx);
                working.put(key, newVal);
                return working;
            } catch (JsonTError.Eval e) {
                throw new JsonTError.Transform(
                        "transform failed for '" + key + "': " + e.getMessage(), e);
            }
        }

        // Unknown operation type — return unchanged
        return working;
    }

    public static List<String> resolveEffectiveFields(
            JsonTSchema schema,
            SchemaRegistry registry,
            List<String> chain) throws JsonTError.Transform {

        // Cycle detection
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

        // DERIVED
        String from = schema.derivedFrom().get();
        JsonTSchema parent = registry.resolve(from)
                .orElseThrow(() -> new JsonTError.Transform.UnknownSchema(from));

        List<String> fieldNames = resolveEffectiveFields(parent, registry, chain);

        for (SchemaOperation op : schema.operations()) {
            if (op instanceof SchemaOperation.Rename rename) {
                for (RenamePair pair : rename.pairs()) {
                    String oldName = pair.from().leaf();
                    int idx = fieldNames.indexOf(oldName);
                    if (idx >= 0) {
                        fieldNames.set(idx, pair.to());
                    }
                }
            } else if (op instanceof SchemaOperation.Exclude exclude) {
                for (FieldPath path : exclude.paths()) {
                    fieldNames.remove(path.leaf());
                }
            } else if (op instanceof SchemaOperation.Project project) {
                List<String> projected = new ArrayList<>();
                for (FieldPath path : project.paths()) {
                    projected.add(path.leaf());
                }
                fieldNames = projected;
            }
            // Filter and Transform don't change the field list structure
        }

        return fieldNames;
    }

    public static List<String> collectFieldRefs(JsonTExpression expr) {
        List<String> refs = new ArrayList<>();
        collectFieldRefsInto(expr, refs);
        return refs;
    }

    private static void collectFieldRefsInto(JsonTExpression expr, List<String> refs) {
        if (expr instanceof JsonTExpression.Literal) {
            // no refs
        } else if (expr instanceof JsonTExpression.FieldRef ref) {
            refs.add(ref.path().leaf());
        } else if (expr instanceof JsonTExpression.Binary bin) {
            collectFieldRefsInto(bin.lhs(), refs);
            collectFieldRefsInto(bin.rhs(), refs);
        } else if (expr instanceof JsonTExpression.Unary un) {
            collectFieldRefsInto(un.operand(), refs);
        }
    }
}
