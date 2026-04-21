package io.github.datakore.jsont.internal.validate.scope;

import io.github.datakore.jsont.error.BuildError;
import io.github.datakore.jsont.model.FieldPath;
import io.github.datakore.jsont.model.SchemaOperation;

import java.util.Map;

/** Validates {@link SchemaOperation.Project}: each projected field must exist in scope. */
public final class ProjectScopeValidator implements OperationScopeValidator {

    public static final ProjectScopeValidator INSTANCE = new ProjectScopeValidator();

    private ProjectScopeValidator() {}

    @Override
    public boolean supports(SchemaOperation op) {
        return op instanceof SchemaOperation.Project;
    }

    @Override
    public void validate(SchemaOperation op, Map<String, Boolean> scope, String parentName) throws BuildError {
        SchemaOperation.Project p = (SchemaOperation.Project) op;
        for (FieldPath path : p.paths()) {
            String name = path.dotJoined();
            if (name.contains(".")) continue; // nested paths skipped
            if (!scope.containsKey(name))
                throw new BuildError("project references field '" + name + "' which is not in scope");
        }
        scope.keySet().retainAll(
                p.paths().stream()
                        .map(FieldPath::dotJoined)
                        .filter(n -> !n.contains("."))
                        .toList());
    }
}
