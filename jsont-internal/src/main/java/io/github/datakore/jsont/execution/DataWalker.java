package io.github.datakore.jsont.execution;

import io.github.datakore.jsont.adapters.AdapterContext;
import io.github.datakore.jsont.grammar.data.RowNode;
import io.github.datakore.jsont.grammar.schema.ast.SchemaModel;

import java.util.ArrayList;
import java.util.List;

public class DataWalker {
    private final RowMapper rowMapper;
    private final AdapterContext adapterContext;

    public DataWalker(RowMapper rowMapper, AdapterContext context) {
        this.adapterContext = context;
        this.rowMapper = rowMapper;
    }

    public List<Object> walk(
            List<RowNode> rows,
            SchemaModel schema
    ) {
        List<Object> result = new ArrayList<>();

        for (RowNode row : rows) {
            result.add(rowMapper.mapRow(row, schema, adapterContext));
        }

        return result;
    }
}
