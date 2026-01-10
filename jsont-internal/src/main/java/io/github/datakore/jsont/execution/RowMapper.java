package io.github.datakore.jsont.execution;

import io.github.datakore.jsont.adapters.AdapterContext;
import io.github.datakore.jsont.grammar.data.RowNode;
import io.github.datakore.jsont.grammar.schema.ast.SchemaModel;

public interface RowMapper {
    Object mapRow(RowNode row, SchemaModel schema, AdapterContext context);
}
