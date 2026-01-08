package org.jsont.execution;

import org.jsont.grammar.data.RowNode;
import org.jsont.grammar.schema.ast.SchemaModel;
import reactor.core.publisher.Flux;

public interface DataStream {
    void onRowParsed(RowNode row);

    void onEOF();

    Flux<RowNode> rows();

    SchemaModel getDataSchema();

    void setDataSchema(String schema);
}
