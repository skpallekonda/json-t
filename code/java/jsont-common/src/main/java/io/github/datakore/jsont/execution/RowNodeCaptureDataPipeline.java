package io.github.datakore.jsont.execution;

import io.github.datakore.jsont.errors.ValidationError;
import io.github.datakore.jsont.grammar.data.RowNode;
import reactor.core.publisher.Flux;

import java.util.List;

public class RowNodeCaptureDataPipeline implements DataStream {
    private RowNode result;
    private List<ValidationError> errors;


    @Override
    public void onRowParsed(RowNode row) {
        result = row;
    }

    public RowNode getResult() {
        return result;
    }

    @Override
    public void onEOF() {
        // do nothing
    }

    @Override
    public Flux<RowNode> rows() {
        return null;
    }

    @Override
    public void onRowError(int rowIndex, List<ValidationError> errors) {
        this.errors = errors;
    }

    public boolean hasError() {
        return errors != null && !errors.isEmpty() && errors.stream().anyMatch(err -> err.severity().isFatal());
    }

    public List<ValidationError> getError() {
        return this.errors;
    }
}
