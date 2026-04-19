package io.github.datakore.jsont.internal.validate.scope;

import io.github.datakore.jsont.error.BuildError;
import io.github.datakore.jsont.model.SchemaOperation;

import java.util.Map;

/** Validates {@link SchemaOperation.Decrypt}: each field must exist and be marked sensitive. */
public final class DecryptScopeValidator implements OperationScopeValidator {

    public static final DecryptScopeValidator INSTANCE = new DecryptScopeValidator();

    private DecryptScopeValidator() {}

    @Override
    public boolean supports(SchemaOperation op) {
        return op instanceof SchemaOperation.Decrypt;
    }

    @Override
    public void validate(SchemaOperation op, Map<String, Boolean> scope, String parentName) throws BuildError {
        SchemaOperation.Decrypt d = (SchemaOperation.Decrypt) op;
        for (String fname : d.fields()) {
            if (!scope.containsKey(fname))
                throw new BuildError("decrypt references field '" + fname + "' which is not in scope");
            if (!scope.get(fname))
                throw new BuildError("decrypt references field '" + fname
                        + "' which is not marked sensitive (~) in parent '" + parentName + "'");
            scope.put(fname, false); // mark decrypted (now plaintext)
        }
    }
}
