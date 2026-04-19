package io.github.datakore.jsont.internal.validate.scope;

import io.github.datakore.jsont.error.BuildError;
import io.github.datakore.jsont.internal.transform.handler.FieldRefCollector;
import io.github.datakore.jsont.model.SchemaOperation;

import java.util.Map;

/**
 * Validates {@link SchemaOperation.Filter}: all referenced fields must be in scope and plaintext.
 * Filter does not change the scope shape.
 */
public final class FilterScopeValidator implements OperationScopeValidator {

    public static final FilterScopeValidator INSTANCE = new FilterScopeValidator();

    private FilterScopeValidator() {}

    @Override
    public boolean supports(SchemaOperation op) {
        return op instanceof SchemaOperation.Filter;
    }

    @Override
    public void validate(SchemaOperation op, Map<String, Boolean> scope, String parentName) throws BuildError {
        SchemaOperation.Filter f = (SchemaOperation.Filter) op;
        for (String ref : FieldRefCollector.collect(f.predicate())) {
            if (!scope.containsKey(ref))
                throw new BuildError("filter expression references field '" + ref + "' which is not in scope");
            if (scope.get(ref))
                throw new BuildError("filter expression references encrypted field '" + ref
                        + "'; add decrypt(" + ref + ") first");
        }
    }
}
