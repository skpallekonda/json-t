package org.jsont.core;

import org.jsont.adapters.AdapterContext;
import org.jsont.execution.DataStream;
import org.jsont.execution.RowMapper;
import org.jsont.grammar.schema.ast.SchemaModel;

public class JsonTExecutionBuilder {

    private final DataStream dataStream;
    private final RowMapper rowMapper;
    private final AdapterContext adapterContext;
    private final SchemaModel schemaModel;

    public JsonTExecutionBuilder(DataStream dataStream,
                                 RowMapper rowMapper,
                                 AdapterContext adapterContext,
                                 SchemaModel schemaModel) {
        this.dataStream = dataStream;
        this.rowMapper = rowMapper;
        this.adapterContext = adapterContext;
        this.schemaModel = schemaModel;
    }

    public <T> JsonTExecution<T> as(Class<T> targetType) {
        return new JsonTExecution<>(dataStream, rowMapper, adapterContext, schemaModel, targetType);
    }
}
