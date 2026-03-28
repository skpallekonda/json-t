package io.github.datakore.jsont.validate;

import io.github.datakore.jsont.diagnostic.DiagnosticSink;
import io.github.datakore.jsont.internal.diagnostic.ConsoleSink;
import io.github.datakore.jsont.model.JsonTField;
import io.github.datakore.jsont.model.JsonTSchema;
import io.github.datakore.jsont.model.JsonTValidationBlock;
import io.github.datakore.jsont.model.SchemaKind;

import java.util.ArrayList;
import java.util.List;

public final class ValidationPipelineBuilder {

    private final JsonTSchema schema;
    private boolean hasConsole = true;
    private final List<DiagnosticSink> extraSinks = new ArrayList<>();
    private int workers = Runtime.getRuntime().availableProcessors();
    private int bufferCapacity = 256;

    public ValidationPipelineBuilder(JsonTSchema schema) {
        this.schema = schema;
    }

    public ValidationPipelineBuilder withoutConsole() { this.hasConsole = false; return this; }
    public ValidationPipelineBuilder withSink(DiagnosticSink sink) { this.extraSinks.add(sink); return this; }

    /** Number of worker threads used by {@link ValidationPipeline#validateStream}. Default: available processors. */
    public ValidationPipelineBuilder workers(int n) { this.workers = n; return this; }

    /** Bounded queue depth between producer and workers in {@link ValidationPipeline#validateStream}. Default: 256. */
    public ValidationPipelineBuilder bufferCapacity(int n) { this.bufferCapacity = n; return this; }

    public ValidationPipeline build() {
        List<JsonTField> fields;
        if (schema.kind() == SchemaKind.STRAIGHT) {
            fields = schema.fields();
        } else {
            fields = List.of();
        }

        JsonTValidationBlock validation = schema.validation().orElse(null);

        List<DiagnosticSink> sinks = new ArrayList<>();
        if (hasConsole) {
            sinks.add(new ConsoleSink());
        }
        sinks.addAll(extraSinks);

        return new ValidationPipeline(fields, validation, schema.name(), sinks, workers, bufferCapacity);
    }
}
