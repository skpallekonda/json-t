package io.github.datakore.jsont.pipeline;

import io.github.datakore.jsont.errors.collector.ErrorCollector;
import io.github.datakore.jsont.grammar.data.RowNode;
import io.github.datakore.jsont.grammar.schema.ast.NamespaceT;
import io.github.datakore.jsont.grammar.schema.ast.SchemaModel;
import io.github.datakore.jsont.util.StepCounter;
import io.github.datakore.jsont.validator.SchemaValidator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.ParallelFlux;
import reactor.core.scheduler.Schedulers;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

public class ValidateStage implements PipelineStage<RowNode, RowNode> {

    private final NamespaceT namespace;
    private final Class<?> targetType;
    private final Consumer<StepCounter> monitor;
    private final int parallelism;
    private final SchemaValidator validator;

    public ValidateStage(NamespaceT namespace, ErrorCollector errorCollector, Class<?> targetType, Consumer<StepCounter> monitor, int parallelism) {
        this.namespace = namespace;
        this.targetType = targetType;
        this.monitor = monitor;
        this.parallelism = parallelism;
        validator = new SchemaValidator(namespace, errorCollector);
    }

    @Override
    public Flux<RowNode> execute(Flux<RowNode> input) {
        SchemaModel schema = namespace.findSchema(targetType.getSimpleName());
        AtomicLong counter = new AtomicLong();

        ParallelFlux<RowNode> parallelFlux = input.parallel(parallelism)
                .runOn(Schedulers.parallel())
                .map(row -> {
                    try {
                        validator.validate(schema, row.getRowIndex(), row.values());
                        return Optional.of(row);
                    } catch (Exception e) {
                        return Optional.<RowNode>empty();
                    }
                })
                .filter(Optional::isPresent)
                .map(Optional::get)
                .doOnNext(row -> monitor(monitor, "validate", counter.incrementAndGet()));

        return parallelFlux.sequential();
    }
}
