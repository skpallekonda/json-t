package io.github.datakore.jsont.internal.validate.scope;

import io.github.datakore.jsont.error.BuildError;
import io.github.datakore.jsont.model.SchemaOperation;

import java.util.Map;

/**
 * Validates one {@link SchemaOperation} type against the current field scope
 * and mutates the scope to reflect the operation's effect.
 *
 * <p>Scope is a {@code LinkedHashMap<fieldName, isEncrypted>} maintained in field-declaration
 * order. Each validator checks preconditions (field exists, encryption state) then updates
 * the scope so subsequent validators see the correct post-operation state.
 */
public interface OperationScopeValidator {

    /** Returns {@code true} if this validator applies to {@code op}. */
    boolean supports(SchemaOperation op);

    /**
     * Validate {@code op} against {@code scope} and update the scope to reflect the effect.
     *
     * @param op         the operation to validate (matches {@link #supports})
     * @param scope      mutable map of (fieldName → isEncrypted) in current field order
     * @param parentName the parent schema name used in error messages
     * @throws BuildError if the operation is structurally invalid in this scope
     */
    void validate(SchemaOperation op, Map<String, Boolean> scope, String parentName) throws BuildError;
}
