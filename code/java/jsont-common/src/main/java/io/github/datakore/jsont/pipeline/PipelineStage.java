package io.github.datakore.jsont.pipeline;

import io.github.datakore.jsont.util.StepCounter;
import reactor.core.publisher.Flux;

import java.util.function.Consumer;

public interface PipelineStage<I, O> {
    Flux<O> execute(Flux<I> input);
    
    default void monitor(Consumer<StepCounter> monitor, String stageName, long count) {
        if (monitor != null) {
            monitor.accept(new StepCounter(stageName, count));
        }
    }
}
