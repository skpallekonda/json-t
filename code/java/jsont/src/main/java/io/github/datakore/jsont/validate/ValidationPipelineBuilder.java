package io.github.datakore.jsont.validate;

import io.github.datakore.jsont.builder.SchemaResolver;
import io.github.datakore.jsont.crypto.CryptoContext;
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
    private CryptoContext cryptoContext = null;
    private SchemaResolver registry = null;

    public ValidationPipelineBuilder(JsonTSchema schema) {
        this.schema = schema;
    }

    public ValidationPipelineBuilder withoutConsole() { this.hasConsole = false; return this; }
    public ValidationPipelineBuilder withSink(DiagnosticSink sink) { this.extraSinks.add(sink); return this; }

    /** Number of worker threads used by {@link ValidationPipeline#validateStream}. Default: available processors. */
    public ValidationPipelineBuilder workers(int n) { this.workers = n; return this; }

    /** Bounded queue depth between producer and workers in {@link ValidationPipeline#validateStream}. Default: 256. */
    public ValidationPipelineBuilder bufferCapacity(int n) { this.bufferCapacity = n; return this; }

    /**
     * Attach a {@link CryptoContext} so the pipeline can perform Decrypt operations.
     * The context is stored as-is — the caller retains ownership and must close it.
     */
    public ValidationPipelineBuilder withCryptoContext(CryptoContext ctx) { this.cryptoContext = ctx; return this; }

    /** Attach a {@link SchemaResolver} so the pipeline can promote sensitive fields in nested objects. */
    public ValidationPipelineBuilder withRegistry(SchemaResolver registry) { this.registry = registry; return this; }

    private static boolean hasSensitiveField(List<JsonTField> fields, SchemaResolver registry) {
        for (JsonTField f : fields) {
            if (f.sensitive()) return true;
            if (f.kind().isObject() && registry != null) {
                JsonTSchema nested = registry.resolve(f.objectRef()).orElse(null);
                if (nested != null && hasSensitiveField(nested.fields(), registry)) return true;
            }
        }
        return false;
    }

    public ValidationPipeline build() {
        List<JsonTField> fields;
        if (schema.kind() == SchemaKind.STRAIGHT) {
            fields = schema.fields();
        } else {
            fields = List.of();
        }

        if (cryptoContext == null && hasSensitiveField(fields, registry)) {
            throw new IllegalStateException(
                "Schema '" + schema.name() + "' contains sensitive fields — call withCryptoContext() before build()");
        }

        JsonTValidationBlock validation = schema.validation().orElse(null);

        List<DiagnosticSink> sinks = new ArrayList<>();
        if (hasConsole) {
            sinks.add(new ConsoleSink());
        }
        sinks.addAll(extraSinks);

        return new ValidationPipeline(fields, validation, schema.name(), sinks, workers, bufferCapacity, cryptoContext, registry);
    }
}
