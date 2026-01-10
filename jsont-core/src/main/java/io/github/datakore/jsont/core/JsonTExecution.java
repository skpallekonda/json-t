package io.github.datakore.jsont.core;

import io.github.datakore.jsont.adapters.AdapterContext;
import io.github.datakore.jsont.execution.DataStream;
import io.github.datakore.jsont.execution.RowMapper;
import io.github.datakore.jsont.grammar.schema.ast.SchemaModel;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.util.List;

public class JsonTExecution<T> {

    private final DataStream dataStream;
    private final RowMapper rowMapper;
    private final AdapterContext adapterContext;
    private final Class<T> targetType;
    private final SchemaModel schemaModel;

    public JsonTExecution(DataStream dataStream,
                          RowMapper rowMapper,
                          AdapterContext adapterContext,
                          SchemaModel schemaModel,
                          Class<T> targetType) {
        this.dataStream = dataStream;
        this.rowMapper = rowMapper;
        this.adapterContext = adapterContext;
        this.schemaModel = schemaModel;
        this.targetType = targetType;
    }

    // Async, parallel, backpressure-aware
    public Flux<T> stream() {
        return dataStream.rows()
                .onBackpressureBuffer()
                .parallel()
                .runOn(Schedulers.parallel())
                .map(row -> rowMapper.mapRow(row, dataStream.getDataSchema(), adapterContext))
                .map(targetType::cast)
                .sequential();
    }

    public List<T> list() {
        return dataStream.rows()
                .map(row -> rowMapper.mapRow(row, dataStream.getDataSchema(), adapterContext))
                .map(targetType::cast)
                .collectList()
                .block();
    }

}
