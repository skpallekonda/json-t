package io.github.datakore.jsont.pipeline;

import io.github.datakore.jsont.adapters.AdapterRegistry;
import io.github.datakore.jsont.adapters.SchemaAdapter;
import io.github.datakore.jsont.grammar.data.RowNode;
import io.github.datakore.jsont.grammar.schema.ast.FieldModel;
import io.github.datakore.jsont.grammar.schema.ast.NamespaceT;
import io.github.datakore.jsont.grammar.schema.ast.SchemaModel;
import io.github.datakore.jsont.grammar.types.ArrayType;
import io.github.datakore.jsont.grammar.types.ObjectType;
import io.github.datakore.jsont.util.StepCounter;
import reactor.core.publisher.Flux;
import reactor.core.publisher.ParallelFlux;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

public class ConvertStage<T> implements PipelineStage<RowNode, T> {

    private final NamespaceT namespace;
    private final AdapterRegistry adapterRegistry;
    private final Class<T> targetType;
    private final Consumer<StepCounter> monitor;
    private final int parallelism;

    public ConvertStage(NamespaceT namespace, AdapterRegistry adapterRegistry, Class<T> targetType, Consumer<StepCounter> monitor, int parallelism) {
        this.namespace = namespace;
        this.adapterRegistry = adapterRegistry;
        this.targetType = targetType;
        this.monitor = monitor;
        this.parallelism = parallelism;
    }

    @Override
    public Flux<T> execute(Flux<RowNode> input) {
        AtomicLong counter = new AtomicLong();
        SchemaModel schema = namespace.findSchema(targetType.getSimpleName());
        SchemaAdapter<?> adapter = adapterRegistry.resolve(schema.name());

        ParallelFlux<T> parallelFlux = input.parallel(parallelism)
                .runOn(Schedulers.parallel())
                .map(row -> {
                    @SuppressWarnings("unchecked")
                    T result = (T) convertToType(row.values(), schema, adapter);
                    return result;
                })
                .doOnNext(obj -> monitor(monitor, "convert", counter.incrementAndGet()));

        return parallelFlux.sequential();
    }

    @SuppressWarnings("unchecked")
    private Object convertToType(Map<String, Object> map, SchemaModel schema, SchemaAdapter<?> adapter) {
        Object object = adapter.createTarget();
        for (int i = 0; i < schema.fields().size(); i++) {
            FieldModel fm = schema.fields().get(i);
            Object value = map.get(fm.getFieldName());
            if (fm.getFieldType() instanceof ObjectType && value instanceof Map) {
                ObjectType objectType = (ObjectType) fm.getFieldType();
                SchemaModel nestedSchema = namespace.findSchema(objectType.type());
                SchemaAdapter<?> nestedAdapter = adapterRegistry.resolve(nestedSchema.name());
                value = convertToType((Map<String, Object>) value, nestedSchema, nestedAdapter);
            }
            if (fm.getFieldType() instanceof ArrayType && value instanceof List) {
                ArrayType arrayType = (ArrayType) fm.getFieldType();
                if (arrayType.getElementType() instanceof ObjectType) {
                    int size = ((List<Map<String, Object>>) value).size();
                    List<Object> listValue = new ArrayList<>(size);
                    for (int j = 0; j < size; j++) {
                        Object listElementValue = ((List<Map<String, Object>>) value).get(j);
                        ObjectType objectType = (ObjectType) arrayType.getElementType();
                        SchemaModel nestedSchema = namespace.findSchema(objectType.type());
                        SchemaAdapter<?> nestedAdapter = adapterRegistry.resolve(nestedSchema.name());
                        listElementValue = convertToType((Map<String, Object>) listElementValue, nestedSchema, nestedAdapter);
                        listValue.add(listElementValue);
                    }
                    value = listValue;
                }
            }
            adapter.set(object, fm.getFieldName(), value);
        }
        return object;
    }
}
