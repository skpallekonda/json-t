package io.github.datakore.jsont.internal.transform.handler;

import io.github.datakore.jsont.crypto.CryptoContext;
import io.github.datakore.jsont.error.JsonTError;
import io.github.datakore.jsont.model.FieldPath;
import io.github.datakore.jsont.model.JsonTValue;
import io.github.datakore.jsont.model.SchemaOperation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;

/** Handles {@link SchemaOperation.Project}: keeps only the listed fields in declaration order. */
public final class ProjectHandler implements RowOperationHandler, FieldResolutionHandler {

    public static final ProjectHandler INSTANCE = new ProjectHandler();

    private ProjectHandler() {}

    @Override
    public boolean supports(SchemaOperation op) {
        return op instanceof SchemaOperation.Project;
    }

    @Override
    public LinkedHashMap<String, JsonTValue> apply(
            SchemaOperation op,
            LinkedHashMap<String, JsonTValue> working,
            CryptoContext ctx) throws JsonTError.Transform {
        SchemaOperation.Project project = (SchemaOperation.Project) op;
        for (FieldPath path : project.paths()) {
            if (!working.containsKey(path.dotJoined()))
                throw new JsonTError.Transform.FieldNotFound(path.dotJoined());
        }
        LinkedHashMap<String, JsonTValue> projected = new LinkedHashMap<>();
        for (FieldPath path : project.paths()) {
            projected.put(path.dotJoined(), working.get(path.dotJoined()));
        }
        return projected;
    }

    @Override
    public List<String> apply(SchemaOperation op, List<String> fieldNames) {
        SchemaOperation.Project project = (SchemaOperation.Project) op;
        return project.paths().stream()
                .map(FieldPath::dotJoined)
                .collect(Collectors.toCollection(ArrayList::new));
    }
}
