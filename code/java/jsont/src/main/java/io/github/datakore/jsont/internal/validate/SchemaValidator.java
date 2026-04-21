package io.github.datakore.jsont.internal.validate;

import io.github.datakore.jsont.builder.SchemaResolver;
import io.github.datakore.jsont.error.JsonTError;
import io.github.datakore.jsont.internal.transform.OperationApplicator;
import io.github.datakore.jsont.model.FieldPath;
import io.github.datakore.jsont.model.JsonTExpression;
import io.github.datakore.jsont.model.JsonTField;
import io.github.datakore.jsont.model.JsonTRule;
import io.github.datakore.jsont.model.JsonTSchema;
import io.github.datakore.jsont.model.JsonTValidationBlock;
import io.github.datakore.jsont.model.RenamePair;
import io.github.datakore.jsont.model.SchemaKind;
import io.github.datakore.jsont.model.SchemaOperation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Static structural validation for {@link JsonTSchema} instances.
 *
 * <p>Operates on a plain {@code Map<String, JsonTSchema>} so it can be called
 * from both {@code SchemaRegistry.fromNamespace} (builder package) and
 * {@code RowTransformer.validateSchema} (transform package) without creating
 * a circular package dependency.
 *
 * <p>Checks performed for each schema:
 * <ul>
 *   <li>Straight: object field references exist in the registry</li>
 *   <li>Straight: validation rule / unique-key field refs are valid</li>
 *   <li>Derived: parent schema exists and has no derivation cycle</li>
 *   <li>Derived: every operation refers to fields that exist at that point in
 *       the pipeline</li>
 *   <li>Derived: validation block (if present) refers to output fields only</li>
 * </ul>
 */
public final class SchemaValidator {

    private SchemaValidator() {}

    // ─── Public entry points ──────────────────────────────────────────────────

    /**
     * Validates every schema in {@code schemas} against the full map.
     *
     * @throws JsonTError.SchemaInvalid if any schema fails validation
     */
    public static void validateAll(Map<String, JsonTSchema> schemas)
            throws JsonTError.SchemaInvalid {
        for (JsonTSchema schema : schemas.values()) {
            validate(schema, schemas);
        }
    }

    /**
     * Validates a single schema against the provided map.
     *
     * @throws JsonTError.SchemaInvalid if the schema fails validation
     */
    public static void validate(JsonTSchema schema, Map<String, JsonTSchema> schemas)
            throws JsonTError.SchemaInvalid {
        try {
            if (schema.kind() == SchemaKind.STRAIGHT) {
                validateStraight(schema, schemas);
            } else {
                validateDerived(schema, schemas);
            }
        } catch (JsonTError.Transform.CyclicDerivation e) {
            throw new JsonTError.SchemaInvalid(e.getMessage());
        } catch (JsonTError.Transform.UnknownSchema e) {
            throw new JsonTError.SchemaInvalid(e.getMessage());
        } catch (JsonTError.Transform e) {
            throw new JsonTError.SchemaInvalid(e.getMessage());
        }
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    private static void validateStraight(JsonTSchema schema, Map<String, JsonTSchema> schemas)
            throws JsonTError.SchemaInvalid {
        // 1. Object field references must exist
        for (JsonTField field : schema.fields()) {
            if (field.kind().isObject()) {
                String ref = field.objectRef();
                if (!schemas.containsKey(ref)) {
                    throw new JsonTError.SchemaInvalid(
                            "Field '" + field.name() + "' references unknown schema: " + ref);
                }
            }
        }

        List<String> fieldNames = new ArrayList<>();
        for (JsonTField f : schema.fields()) {
            fieldNames.add(f.name());
        }

        // 2. Validation rule / unique-key field refs
        if (schema.validation().isPresent()) {
            validateBlock(schema.validation().get(), fieldNames, schema.name());
        }
    }

    private static void validateDerived(JsonTSchema schema, Map<String, JsonTSchema> schemas)
            throws JsonTError.SchemaInvalid, JsonTError.Transform {
        String from = schema.derivedFrom()
                .orElseThrow(() -> new JsonTError.SchemaInvalid(
                        "Derived schema '" + schema.name() + "' has no parent declared"));

        // 1. Parent must exist
        if (!schemas.containsKey(from)) {
            throw new JsonTError.SchemaInvalid("Derived schema '" + schema.name()
                    + "' references unknown parent schema: " + from);
        }

        // 2. Resolve effective parent fields (detects cycles)
        MapSchemaRegistry mapRegistry = new MapSchemaRegistry(schemas);
        List<String> chain = new ArrayList<>(List.of(schema.name()));
        JsonTSchema parent = schemas.get(from);
        List<String> currentFields = new ArrayList<>(
                OperationApplicator.resolveEffectiveFields(parent, mapRegistry, chain));

        // 3. Validate operations against the evolving field set
        for (SchemaOperation op : schema.operations()) {
            if (op instanceof SchemaOperation.Rename rename) {
                for (RenamePair pair : rename.pairs()) {
                    String oldName = pair.from().dotJoined();
                    if (!currentFields.contains(oldName)) {
                        throw new JsonTError.SchemaInvalid(
                                "Rename references unknown field: " + oldName);
                    }
                    int idx = currentFields.indexOf(oldName);
                    currentFields.set(idx, pair.to());
                }
            } else if (op instanceof SchemaOperation.Exclude exclude) {
                for (FieldPath path : exclude.paths()) {
                    String name = path.dotJoined();
                    if (!currentFields.contains(name)) {
                        throw new JsonTError.SchemaInvalid(
                                "Exclude references unknown field: " + name);
                    }
                    currentFields.remove(name);
                }
            } else if (op instanceof SchemaOperation.Project project) {
                for (FieldPath path : project.paths()) {
                    if (!currentFields.contains(path.dotJoined())) {
                        throw new JsonTError.SchemaInvalid(
                                "Project references unknown field: " + path.dotJoined());
                    }
                }
                List<String> projected = new ArrayList<>();
                for (FieldPath path : project.paths()) {
                    projected.add(path.dotJoined());
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
                String target = transform.target().dotJoined();
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
            // Decrypt operation has no field-existence requirement to validate statically
        }

        // 4. Validation block on derived schema validated against output fields
        if (schema.validation().isPresent()) {
            validateBlock(schema.validation().get(), currentFields, schema.name());
        }
    }

    private static void validateBlock(
            JsonTValidationBlock vb,
            List<String> fieldNames,
            String schemaName) throws JsonTError.SchemaInvalid {
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
                                "[" + schemaName + "] Rule references unknown field: " + ref);
                    }
                }
            }
        }
        for (List<FieldPath> group : vb.uniqueKeys()) {
            for (FieldPath path : group) {
                if (!fieldNames.contains(path.dotJoined())) {
                    throw new JsonTError.SchemaInvalid(
                            "[" + schemaName + "] UniqueKey references unknown field: "
                                    + path.dotJoined());
                }
            }
        }
    }

    // ─── Thin adapter so OperationApplicator can use a Map as a registry ─────

    /**
     * Minimal registry adapter backed by a {@code Map<String, JsonTSchema>}.
     * Avoids importing {@code SchemaRegistry} (which would cause a circular
     * package dependency: builder → internal → builder).
     */
    private static final class MapSchemaRegistry implements SchemaResolver {

        private final Map<String, JsonTSchema> schemas;

        MapSchemaRegistry(Map<String, JsonTSchema> schemas) {
            this.schemas = schemas;
        }

        @Override
        public boolean contains(String name) {
            return schemas.containsKey(name);
        }

        @Override
        public java.util.Optional<JsonTSchema> resolve(String name) {
            return java.util.Optional.ofNullable(schemas.get(name));
        }

        @Override
        public JsonTSchema resolveOrThrow(String name) {
            JsonTSchema s = schemas.get(name);
            if (s == null)
                throw new JsonTError.Transform.UnknownSchema(name);
            return s;
        }
    }
}
