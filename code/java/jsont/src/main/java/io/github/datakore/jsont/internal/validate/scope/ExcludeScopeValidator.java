package io.github.datakore.jsont.internal.validate.scope;

import io.github.datakore.jsont.error.BuildError;
import io.github.datakore.jsont.model.FieldPath;
import io.github.datakore.jsont.model.SchemaOperation;

import java.util.Map;

/** Validates {@link SchemaOperation.Exclude}: each excluded field must exist in scope. */
public final class ExcludeScopeValidator implements OperationScopeValidator {

    public static final ExcludeScopeValidator INSTANCE = new ExcludeScopeValidator();

    private ExcludeScopeValidator() {}

    @Override
    public boolean supports(SchemaOperation op) {
        return op instanceof SchemaOperation.Exclude;
    }

    @Override
    public void validate(SchemaOperation op, Map<String, Boolean> scope, String parentName) throws BuildError {
        SchemaOperation.Exclude e = (SchemaOperation.Exclude) op;
        for (FieldPath path : e.paths()) {
            String name = path.dotJoined();
            if (name.contains(".")) continue;
            if (!scope.containsKey(name))
                throw new BuildError("exclude references field '" + name + "' which is not in scope");
            scope.remove(name);
        }
    }
}
