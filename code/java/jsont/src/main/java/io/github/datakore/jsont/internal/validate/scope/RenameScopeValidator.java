package io.github.datakore.jsont.internal.validate.scope;

import io.github.datakore.jsont.error.BuildError;
import io.github.datakore.jsont.model.RenamePair;
import io.github.datakore.jsont.model.SchemaOperation;

import java.util.LinkedHashMap;
import java.util.Map;

/** Validates {@link SchemaOperation.Rename}: source must exist; target must not conflict. */
public final class RenameScopeValidator implements OperationScopeValidator {

    public static final RenameScopeValidator INSTANCE = new RenameScopeValidator();

    private RenameScopeValidator() {}

    @Override
    public boolean supports(SchemaOperation op) {
        return op instanceof SchemaOperation.Rename;
    }

    @Override
    public void validate(SchemaOperation op, Map<String, Boolean> scope, String parentName) throws BuildError {
        SchemaOperation.Rename r = (SchemaOperation.Rename) op;
        for (RenamePair pair : r.pairs()) {
            String from = pair.from().dotJoined();
            if (from.contains(".")) continue;
            String to = pair.to();
            if (!scope.containsKey(from))
                throw new BuildError("rename references field '" + from + "' which is not in scope");
            if (scope.containsKey(to))
                throw new BuildError("rename to '" + to + "' conflicts with an existing field in scope");
            // Preserve insertion order: rebuild the entry under the new key.
            boolean wasEncrypted = scope.remove(from);
            ((LinkedHashMap<String, Boolean>) scope).put(to, wasEncrypted);
        }
    }
}
