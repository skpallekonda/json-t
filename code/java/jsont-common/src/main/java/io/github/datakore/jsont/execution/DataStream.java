package io.github.datakore.jsont.execution;

import io.github.datakore.jsont.errors.ValidationError;
import io.github.datakore.jsont.grammar.data.RowNode;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

public interface DataStream {
    void onRowParsed(RowNode row);

    void onEOF();

    Flux<RowNode> rows();


    void onRowError(int rowIndex, List<ValidationError> errors);
}
