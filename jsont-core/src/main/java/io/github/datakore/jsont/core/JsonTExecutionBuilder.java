package io.github.datakore.jsont.core;

import io.github.datakore.jsont.adapters.AdapterContext;
import io.github.datakore.jsont.execution.DataStream;
import io.github.datakore.jsont.execution.RowMapper;
import io.github.datakore.jsont.grammar.schema.ast.SchemaModel;

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
