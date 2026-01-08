package org.jsont.execution;

import org.jsont.exception.SchemaException;
import org.jsont.grammar.data.RowNode;
import org.jsont.grammar.schema.ast.SchemaCatalog;
import org.jsont.grammar.schema.ast.SchemaModel;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

public class DataPipeline implements DataStream {
    private final Sinks.Many<RowNode> sink =
            Sinks.many().unicast().onBackpressureBuffer();
    private final SchemaCatalog catalog;
    private SchemaModel dataSchema;

    public DataPipeline(SchemaCatalog catalog) {
        this.catalog = catalog;
    }

    @Override
    public void onRowParsed(RowNode row) {
        sink.tryEmitNext(row);
    }

    @Override
    public void onEOF() {
        sink.tryEmitComplete();
    }

    @Override
    public Flux<RowNode> rows() {
        return sink.asFlux();
    }

    @Override
    public SchemaModel getDataSchema() {
        return dataSchema;
    }

    @Override
    public void setDataSchema(String schema) {
        if (catalog == null) {
            throw new SchemaException("Empty catalog");
        }
        this.dataSchema = this.catalog.getSchema(schema);
        if (this.dataSchema == null) {
            throw new SchemaException("No such schema exists in the catalog");
        }
    }
}
