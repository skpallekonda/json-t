package org.jsont.execution;

import org.jsont.adapters.AdapterContext;
import org.jsont.grammar.data.RowNode;
import org.jsont.grammar.schema.ast.SchemaModel;

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
