package io.github.datakore.jsont.execution;

import io.github.datakore.jsont.adapters.EmitterContext;
import io.github.datakore.jsont.grammar.schema.ast.EnumModel;
import io.github.datakore.jsont.grammar.schema.ast.SchemaModel;

public interface SchemaEmitter {
    void emitSchema(SchemaModel schema, EmitterContext context);

    void emitEnum(EnumModel enumModel, EmitterContext context);

    String emitRequiredCatalog(SchemaModel dataSchema, EmitterContext emitterContext);

    <T> String stringify(EmitterContext context, T target, Class<T> targetClass);
}
