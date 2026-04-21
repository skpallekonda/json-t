package io.github.datakore.jsont.internal.validate.scope;

import io.github.datakore.jsont.error.BuildError;
import io.github.datakore.jsont.model.JsonTField;
import io.github.datakore.jsont.model.SchemaOperation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Orchestrates per-operation scope validation for a derived schema.
 *
 * <p>Each operation type is handled by a dedicated {@link OperationScopeValidator}.
 * Adding a new operation type only requires implementing that interface and
 * registering the instance in {@link #VALIDATORS}.
 */
public final class ScopeValidation {

    private static final List<OperationScopeValidator> VALIDATORS = List.of(
            DecryptScopeValidator.INSTANCE,
            ProjectScopeValidator.INSTANCE,
            ExcludeScopeValidator.INSTANCE,
            RenameScopeValidator.INSTANCE,
            TransformScopeValidator.INSTANCE,
            FilterScopeValidator.INSTANCE
    );

    private ScopeValidation() {}

    /**
     * Validate all {@code operations} against the parent schema's field set.
     *
     * @param operations the derived schema's operation list (in declaration order)
     * @param parentFields the parent schema's declared fields (sets initial scope)
     * @param parentName   the parent schema name (for error messages)
     * @throws BuildError if any operation is invalid given the current scope
     */
    public static void validate(
            List<SchemaOperation> operations,
            List<JsonTField> parentFields,
            String parentName) throws BuildError {

        Map<String, Boolean> scope = new LinkedHashMap<>();
        for (JsonTField f : parentFields) {
            scope.put(f.name(), f.sensitive());
        }

        for (SchemaOperation op : operations) {
            for (OperationScopeValidator validator : VALIDATORS) {
                if (validator.supports(op)) {
                    validator.validate(op, scope, parentName);
                    break;
                }
            }
        }
    }
}
