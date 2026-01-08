package org.jsont.execution;

import org.jsont.adapters.AdapterContext;
import org.jsont.grammar.data.RowNode;
import org.jsont.grammar.schema.ast.SchemaModel;

public interface RowMapper {
    Object mapRow(RowNode row, SchemaModel schema, AdapterContext context);
}
