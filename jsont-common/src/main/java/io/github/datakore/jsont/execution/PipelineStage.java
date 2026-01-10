package io.github.datakore.jsont.execution;

import io.github.datakore.jsont.grammar.data.RowNode;

public interface PipelineStage {
    Object convertTo(RowNode input);
}
