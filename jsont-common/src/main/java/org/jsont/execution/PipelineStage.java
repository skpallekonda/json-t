package org.jsont.execution;

import org.jsont.grammar.data.RowNode;

public interface PipelineStage {
    Object convertTo(RowNode input);
}
