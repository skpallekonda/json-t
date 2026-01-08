package org.jsont.adapters;

import org.jsont.grammar.schema.ast.SchemaCatalog;

public interface AdapterContext {
    /**
     * TODO: Remove this method
     * Resolve enum identifiers
     */
    Object adaptEnum(String enumName, String symbol);

    /**
     * Access schema metadata (read-only)
     */
    SchemaCatalog schemaCatalog();

    AdapterRegistry adapterRegistry();
}
