package io.github.datakore.jsont.transform;

import io.github.datakore.jsont.builder.SchemaRegistry;
import io.github.datakore.jsont.crypto.CryptoConfig;
import io.github.datakore.jsont.error.JsonTError;
import io.github.datakore.jsont.internal.transform.OperationApplicator;
import io.github.datakore.jsont.model.FieldPath;
import io.github.datakore.jsont.model.JsonTExpression;
import io.github.datakore.jsont.model.JsonTField;
import io.github.datakore.jsont.model.JsonTRow;
import io.github.datakore.jsont.model.JsonTRule;
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
        try {
            if (schema.kind() == SchemaKind.STRAIGHT) {
                validateStraightSchema();
            } else {
                validateDerivedSchema();
            }
        } catch (JsonTError.Transform.CyclicDerivation e) {
            throw new JsonTError.SchemaInvalid(e.getMessage());
        } catch (JsonTError.Transform.UnknownSchema e) {
            throw new JsonTError.SchemaInvalid(e.getMessage());
        } catch (JsonTError.Transform e) {
            throw new JsonTError.SchemaInvalid(e.getMessage());
        }
    }

    private void validateStraightSchema() throws JsonTError.SchemaInvalid {
        // 1. Check object field references
        for (JsonTField field : schema.fields()) {
            if (field.kind().isObject()) {
                String ref = field.objectRef();
                if (!registry.contains(ref)) {
                    throw new JsonTError.SchemaInvalid(
                            "Field '" + field.name() + "' references unknown schema: " + ref);
                }
            }
        }

        List<String> fieldNames = new ArrayList<>();
        for (JsonTField f : schema.fields()) {
            fieldNames.add(f.name());
        }

        // 2. Check validation rules
        if (schema.validation().isPresent()) {
            var vb = schema.validation().get();
            for (JsonTRule rule : vb.rules()) {
                List<JsonTExpression> exprs = new ArrayList<>();
                if (rule instanceof JsonTRule.Expression e) {
                    exprs.add(e.expr());
                } else if (rule instanceof JsonTRule.ConditionalRequirement cr) {
                    exprs.add(cr.condition());
                }
                for (JsonTExpression expr : exprs) {
                    for (String ref : OperationApplicator.collectFieldRefs(expr)) {
                        if (!fieldNames.contains(ref)) {
                            throw new JsonTError.SchemaInvalid(
                                    "Rule references unknown field: " + ref);
                        }
                    }
                }
            }

            // 3. Check uniqueKeys
            for (List<FieldPath> group : vb.uniqueKeys()) {
                for (FieldPath path : group) {
                    if (!fieldNames.contains(path.leaf())) {
                        throw new JsonTError.SchemaInvalid(
                                "UniqueKey references unknown field: " + path.leaf());
                    }
                }
            }
        }
    }

    private void validateDerivedSchema() throws JsonTError.SchemaInvalid, JsonTError.Transform {
        String from = schema.derivedFrom().get();

        // 1. Parent exists
        if (!registry.contains(from)) {
            throw new JsonTError.SchemaInvalid("Derived schema '" + schema.name()
                    + "' references unknown parent schema: " + from);
        }

        // 2. No cycle — resolveEffectiveFields will throw CyclicDerivation if there is
        List<String> chain = new ArrayList<>(List.of(schema.name()));
        JsonTSchema parent = registry.resolve(from).get();
        List<String> outputFields = OperationApplicator.resolveEffectiveFields(parent, registry, chain);

        // 3. Simulate operations to validate field references
        List<String> currentFields = new ArrayList<>(outputFields);
        JsonTSchema parentForOps = registry.resolve(from).get();
        List<String> chainForOps = new ArrayList<>(List.of(schema.name()));
        currentFields = new ArrayList<>(
                OperationApplicator.resolveEffectiveFields(parentForOps, registry, chainForOps));

        for (SchemaOperation op : schema.operations()) {
            if (op instanceof SchemaOperation.Rename rename) {
                for (io.github.datakore.jsont.model.RenamePair pair : rename.pairs()) {
                    String oldName = pair.from().leaf();
                    if (!currentFields.contains(oldName)) {
                        throw new JsonTError.SchemaInvalid(
                                "Rename references unknown field: " + oldName);
                    }
                    int idx = currentFields.indexOf(oldName);
                    currentFields.set(idx, pair.to());
                }
            } else if (op instanceof SchemaOperation.Exclude exclude) {
                for (FieldPath path : exclude.paths()) {
                    String name = path.leaf();
                    if (!currentFields.contains(name)) {
                        throw new JsonTError.SchemaInvalid(
                                "Exclude references unknown field: " + name);
                    }
                    currentFields.remove(name);
                }
            } else if (op instanceof SchemaOperation.Project project) {
                for (FieldPath path : project.paths()) {
                    if (!currentFields.contains(path.leaf())) {
                        throw new JsonTError.SchemaInvalid(
                                "Project references unknown field: " + path.leaf());
                    }
                }
                List<String> projected = new ArrayList<>();
                for (FieldPath path : project.paths()) {
                    projected.add(path.leaf());
                }
                currentFields = projected;
            } else if (op instanceof SchemaOperation.Filter filter) {
                for (String ref : OperationApplicator.collectFieldRefs(filter.predicate())) {
                    if (!currentFields.contains(ref)) {
                        throw new JsonTError.SchemaInvalid(
                                "Filter expression references unknown field: " + ref);
                    }
                }
            } else if (op instanceof SchemaOperation.Transform transform) {
                String target = transform.target().leaf();
                if (!currentFields.contains(target)) {
                    throw new JsonTError.SchemaInvalid(
                            "Transform references unknown field: " + target);
                }
                for (String ref : OperationApplicator.collectFieldRefs(transform.expr())) {
                    if (!currentFields.contains(ref)) {
                        throw new JsonTError.SchemaInvalid(
                                "Transform expression references unknown field: " + ref);
                    }
                }
            }
        }

        // 4. If derived schema has validation block, check against output fields
        if (schema.validation().isPresent()) {
            var vb = schema.validation().get();
            for (JsonTRule rule : vb.rules()) {
                // Extract the expression(s) to validate field refs against the output schema
                List<JsonTExpression> exprs = new ArrayList<>();
                if (rule instanceof JsonTRule.Expression e) {
                    exprs.add(e.expr());
                } else if (rule instanceof JsonTRule.ConditionalRequirement cr) {
                    exprs.add(cr.condition());
                }
                for (JsonTExpression expr : exprs) {
                    for (String ref : OperationApplicator.collectFieldRefs(expr)) {
                        if (!currentFields.contains(ref)) {
                            throw new JsonTError.SchemaInvalid(
                                    "Rule references unknown field: " + ref);
                        }
                    }
                }
            }
            for (List<FieldPath> group : vb.uniqueKeys()) {
                for (FieldPath path : group) {
                    if (!currentFields.contains(path.leaf())) {
                        throw new JsonTError.SchemaInvalid(
                                "UniqueKey references unknown field: " + path.leaf());
                    }
                }
            }
        }
    }
}
