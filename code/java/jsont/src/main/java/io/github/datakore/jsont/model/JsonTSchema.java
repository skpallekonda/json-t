package io.github.datakore.jsont.model;

import io.github.datakore.jsont.error.BuildError;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable schema definition, either straight or derived. 
 * Please use {@link JsonTSchemaBuilder.build()} for creating instances.
 */
public final class JsonTSchema {

    private final String name;
    private final SchemaKind kind;
    private final List<JsonTField> fields;        // non-empty for STRAIGHT, empty for DERIVED
    private final String derivedFrom;             // null for STRAIGHT
    private final List<SchemaOperation> operations; // empty for STRAIGHT
    private final JsonTValidationBlock validation;  // null means no validation block

    /** Use {@code JsonTSchemaBuilder} for validated construction. */
    public JsonTSchema(String name,
                SchemaKind kind,
                List<JsonTField> fields,
                String derivedFrom,
                List<SchemaOperation> operations,
                JsonTValidationBlock validation) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(kind, "kind");
        this.name = name;
        this.kind = kind;
        this.fields = List.copyOf(fields);
        this.derivedFrom = derivedFrom;
        this.operations = List.copyOf(operations);
        this.validation = validation;
    }

    // ─── Accessors ────────────────────────────────────────────────────────────

    /** Schema name — must be a valid {@code SCHEMAID} (starts with uppercase). */
    public String name() { return name; }

    /** Returns whether this is a straight or derived schema. */
    public SchemaKind kind() { return kind; }

    public List<JsonTField> fields() { return fields; }

    /** Empty for straight schemas. */
    public Optional<String> derivedFrom() { return Optional.ofNullable(derivedFrom); }

    /** Transformation operations, applied in order. Empty for straight schemas. */
    public List<SchemaOperation> operations() { return operations; }

    /** Uniqueness constraints and boolean rules. Empty for derived schemas. */
    public Optional<JsonTValidationBlock> validation() { return Optional.ofNullable(validation); }

    /** Returns {@code true} for straight schemas. */
    public boolean isStraight() { return kind == SchemaKind.STRAIGHT; }

    /** Returns {@code true} for derived schemas. */
    public boolean isDerived() { return kind == SchemaKind.DERIVED; }

    /**
     * Finds a field by name within a straight schema.
     *
     * @param fieldName the field name to look up
     * @return the matching field, or {@link Optional#empty()}
     */
    public Optional<JsonTField> findField(String fieldName) {
        return fields.stream().filter(f -> f.name().equals(fieldName)).findFirst();
    }

    /** Returns the number of fields in a straight schema (0 for derived). */
    public int fieldCount() { return fields.size(); }

    /**
     * This validates the transformation pipeline against the parent schema. 
     * It checks for field existence, renames, and encryption states properly.
     */
    public void validateWithParent(JsonTSchema parent) throws BuildError {
        if (kind != SchemaKind.DERIVED) return;
        if (parent.kind != SchemaKind.STRAIGHT) return; // nested derived: skip for now

        // ── Initial scope: (fieldName → isEncrypted) in parent declaration order ──
        // LinkedHashMap preserves insertion order (mirrors field declaration order).
        Map<String, Boolean> scope = new LinkedHashMap<>();
        for (JsonTField f : parent.fields()) {
            scope.put(f.name(), f.sensitive());
        }

        for (SchemaOperation op : operations) {

            // ── Decrypt ──────────────────────────────────────────────────────────
            if (op instanceof SchemaOperation.Decrypt d) {
                for (String fname : d.fields()) {
                    if (!scope.containsKey(fname)) {
                        throw new BuildError("decrypt references field '" + fname
                                + "' which is not in scope at this point");
                    }
                    if (!scope.get(fname)) {
                        throw new BuildError("decrypt references field '" + fname
                                + "' which is not marked sensitive (~) in parent '" + parent.name() + "'");
                    }
                    scope.put(fname, false); // mark as decrypted (plain)
                }

            // ── Project ──────────────────────────────────────────────────────────
            } else if (op instanceof SchemaOperation.Project p) {
                for (FieldPath path : p.paths()) {
                    String name = path.dotJoined();
                    if (name.contains(".")) continue; // nested: skip
                    if (!scope.containsKey(name)) {
                        throw new BuildError("project references field '" + name
                                + "' which is not in scope at this point");
                    }
                }
                // Retain only the projected fields (in their original scope order).
                scope.keySet().retainAll(
                        p.paths().stream()
                                .map(FieldPath::dotJoined)
                                .filter(n -> !n.contains("."))
                                .toList());

            // ── Exclude ──────────────────────────────────────────────────────────
            } else if (op instanceof SchemaOperation.Exclude e) {
                for (FieldPath path : e.paths()) {
                    String name = path.dotJoined();
                    if (name.contains(".")) continue;
                    if (!scope.containsKey(name)) {
                        throw new BuildError("exclude references field '" + name
                                + "' which is not in scope at this point");
                    }
                    scope.remove(name);
                }

            // ── Rename ───────────────────────────────────────────────────────────
            } else if (op instanceof SchemaOperation.Rename r) {
                for (RenamePair pair : r.pairs()) {
                    String from = pair.from().dotJoined();
                    if (from.contains(".")) continue;
                    String to = pair.to();
                    if (!scope.containsKey(from)) {
                        throw new BuildError("rename references field '" + from
                                + "' which is not in scope at this point");
                    }
                    if (scope.containsKey(to)) {
                        throw new BuildError("rename to '" + to
                                + "' conflicts with an existing field in scope");
                    }
                    boolean wasEncrypted = scope.remove(from);
                    scope.put(to, wasEncrypted);
                }

            // ── Transform ────────────────────────────────────────────────────────
            } else if (op instanceof SchemaOperation.Transform t) {
                for (String ref : collectFieldRefs(t.expr())) {
                    if (!scope.containsKey(ref)) {
                        throw new BuildError("transform expression references field '" + ref
                                + "' which is not in scope at this point");
                    }
                    if (scope.get(ref)) {
                        throw new BuildError("transform expression references encrypted field '" + ref
                                + "'; add decrypt(" + ref + ") first");
                    }
                }
                String tgt = t.target().leaf();
                if (!scope.containsKey(tgt)) {
                    throw new BuildError("transform target '" + tgt
                            + "' is not in scope at this point");
                }
                if (scope.get(tgt)) {
                    throw new BuildError("transform target '" + tgt
                            + "' is encrypted; add decrypt(" + tgt + ") first");
                }

            // ── Filter ───────────────────────────────────────────────────────────
            } else if (op instanceof SchemaOperation.Filter f) {
                for (String ref : collectFieldRefs(f.predicate())) {
                    if (!scope.containsKey(ref)) {
                        throw new BuildError("filter expression references field '" + ref
                                + "' which is not in scope at this point");
                    }
                    if (scope.get(ref)) {
                        throw new BuildError("filter expression references encrypted field '" + ref
                                + "'; add decrypt(" + ref + ") first");
                    }
                }
            }
        }
    }

    /** Collect all top-level field names referenced by an expression tree. */
    private static List<String> collectFieldRefs(JsonTExpression expr) {
        List<String> refs = new ArrayList<>();
        collectFieldRefsInto(expr, refs);
        return refs;
    }

    private static void collectFieldRefsInto(JsonTExpression expr, List<String> refs) {
        if (expr instanceof JsonTExpression.FieldRef ref) {
            refs.add(ref.path().leaf());
        } else if (expr instanceof JsonTExpression.Binary bin) {
            collectFieldRefsInto(bin.lhs(), refs);
            collectFieldRefsInto(bin.rhs(), refs);
        } else if (expr instanceof JsonTExpression.Unary un) {
            collectFieldRefsInto(un.operand(), refs);
        }
        // Literal: no refs
    }

    @Override
    public String toString() {
        if (kind == SchemaKind.DERIVED) {
            return "schema " + name + " derived " + derivedFrom + " { " + operations.size() + " operation(s) }";
        }
        return "schema " + name + " { " + fields.size() + " field(s) }";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof JsonTSchema s)) return false;
        return name.equals(s.name)
                && kind == s.kind
                && fields.equals(s.fields)
                && Objects.equals(derivedFrom, s.derivedFrom)
                && operations.equals(s.operations)
                && Objects.equals(validation, s.validation);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, kind, fields, derivedFrom, operations, validation);
    }
}
