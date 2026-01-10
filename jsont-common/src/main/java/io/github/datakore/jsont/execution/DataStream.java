package io.github.datakore.jsont.execution;

import io.github.datakore.jsont.grammar.data.RowNode;
import io.github.datakore.jsont.grammar.schema.ast.SchemaModel;
import reactor.core.publisher.Flux;

public interface DataStream {
    void onRowParsed(RowNode row);

    void onEOF();

    Flux<RowNode> rows();

    SchemaModel getDataSchema();

    void setDataSchema(String schema);
}
