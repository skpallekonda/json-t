package io.github.datakore.jsont.internal.validate.scope;

import io.github.datakore.jsont.error.BuildError;
import io.github.datakore.jsont.internal.transform.handler.FieldRefCollector;
import io.github.datakore.jsont.model.SchemaOperation;

import java.util.Map;

/**
 * Validates {@link SchemaOperation.Transform}: all referenced fields must be in scope and
 * plaintext; the target field must also be in scope and plaintext.
 */
public final class TransformScopeValidator implements OperationScopeValidator {

    public static final TransformScopeValidator INSTANCE = new TransformScopeValidator();

    private TransformScopeValidator() {}

    @Override
    public boolean supports(SchemaOperation op) {
        return op instanceof SchemaOperation.Transform;
    }

    @Override
    public void validate(SchemaOperation op, Map<String, Boolean> scope, String parentName) throws BuildError {
        SchemaOperation.Transform t = (SchemaOperation.Transform) op;
        for (String ref : FieldRefCollector.collect(t.expr())) {
            if (!scope.containsKey(ref))
                throw new BuildError("transform expression references field '" + ref + "' which is not in scope");
            if (scope.get(ref))
                throw new BuildError("transform expression references encrypted field '" + ref
                        + "'; add decrypt(" + ref + ") first");
        }
        String tgt = t.target().dotJoined();
        if (!scope.containsKey(tgt))
            throw new BuildError("transform target '" + tgt + "' is not in scope");
        if (scope.get(tgt))
            throw new BuildError("transform target '" + tgt + "' is encrypted; add decrypt(" + tgt + ") first");
    }
}
