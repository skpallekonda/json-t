package io.github.datakore.jsont.model;

import java.util.List;
import java.util.Objects;

/**
 * Sealed hierarchy of operations that can be applied to derive one schema from another.
 *
 * <p>Operations are applied in declaration order. More than one operation of each
 * kind may appear in a derived schema.
 *
 * <pre>{@code
 *   JsonTSchema summary = JsonTSchemaBuilder.derived("OrderSummary", "Order")
 *       .operation(SchemaOperation.project(List.of(
 *           FieldPath.single("id"),
 *           FieldPath.single("product"))))
 *       .operation(SchemaOperation.rename(List.of(
 *           RenamePair.of("product", "productName"))))
 *       .build();
 * }</pre>
 */
public sealed interface SchemaOperation
        permits SchemaOperation.Rename,
                SchemaOperation.Exclude,
                SchemaOperation.Project,
                SchemaOperation.Filter,
                SchemaOperation.Transform {

    // ─── Variants ─────────────────────────────────────────────────────────────

    /**
     * Renames one or more fields.
     * Fields not listed are kept unchanged.
     */
    record Rename(List<RenamePair> pairs) implements SchemaOperation {
        public Rename {
            Objects.requireNonNull(pairs, "pairs must not be null");
            if (pairs.isEmpty()) throw new IllegalArgumentException("Rename must specify at least one pair");
            pairs = List.copyOf(pairs);
        }
    }

    /**
     * Removes specific fields from the row.
     * All other fields are kept.
     */
    record Exclude(List<FieldPath> paths) implements SchemaOperation {
        public Exclude {
            Objects.requireNonNull(paths, "paths must not be null");
            if (paths.isEmpty()) throw new IllegalArgumentException("Exclude must specify at least one path");
            paths = List.copyOf(paths);
        }
    }

    /**
     * Keeps only the specified fields (in the given order), discarding the rest.
     * This is the inverse of {@link Exclude}.
     */
    record Project(List<FieldPath> paths) implements SchemaOperation {
        public Project {
            Objects.requireNonNull(paths, "paths must not be null");
            if (paths.isEmpty()) throw new IllegalArgumentException("Project must specify at least one path");
            paths = List.copyOf(paths);
        }
    }

    /**
     * Drops rows for which {@code predicate} evaluates to {@code false}.
     * When a row is filtered out a {@code JsonTError.Transform.Filtered} signal
     * is raised — callers should skip the row rather than treat it as an error.
     */
    record Filter(JsonTExpression predicate) implements SchemaOperation {
        public Filter {
            Objects.requireNonNull(predicate, "predicate must not be null");
        }
    }

    /**
     * Replaces the value of {@code target} with the result of evaluating {@code expr}.
     * The evaluation context is the current field map of the row.
     */
    record Transform(FieldPath target, JsonTExpression expr) implements SchemaOperation {
        public Transform {
            Objects.requireNonNull(target, "target must not be null");
            Objects.requireNonNull(expr, "expr must not be null");
        }
    }

    // ─── Static factories ─────────────────────────────────────────────────────

    static SchemaOperation rename(List<RenamePair> pairs) {
        return new Rename(pairs);
    }

    static SchemaOperation rename(RenamePair... pairs) {
        return new Rename(List.of(pairs));
    }

    static SchemaOperation exclude(List<FieldPath> paths) {
        return new Exclude(paths);
    }

    static SchemaOperation exclude(FieldPath... paths) {
        return new Exclude(List.of(paths));
    }

    static SchemaOperation project(List<FieldPath> paths) {
        return new Project(paths);
    }

    static SchemaOperation project(FieldPath... paths) {
        return new Project(List.of(paths));
    }

    static SchemaOperation filter(JsonTExpression predicate) {
        return new Filter(predicate);
    }

    static SchemaOperation transform(FieldPath target, JsonTExpression expr) {
        return new Transform(target, expr);
    }

    static SchemaOperation transform(String targetField, JsonTExpression expr) {
        return new Transform(FieldPath.single(targetField), expr);
    }
}
