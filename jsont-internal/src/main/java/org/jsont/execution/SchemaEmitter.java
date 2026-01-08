package org.jsont.execution;

import org.jsont.adapters.EmitterContext;
import org.jsont.grammar.schema.ast.EnumModel;
import org.jsont.grammar.schema.ast.SchemaModel;

public interface SchemaEmitter {
    void emitSchema(SchemaModel schema, EmitterContext context);

    void emitEnum(EnumModel enumModel, EmitterContext context);

    String emitRequiredCatalog(SchemaModel dataSchema, EmitterContext emitterContext);

    <T> String stringify(EmitterContext context, T target, Class<T> targetClass);
}
