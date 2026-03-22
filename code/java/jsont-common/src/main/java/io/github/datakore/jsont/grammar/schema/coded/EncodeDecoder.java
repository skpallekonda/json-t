package io.github.datakore.jsont.grammar.schema.coded;

import io.github.datakore.jsont.grammar.schema.ast.FieldModel;
import io.github.datakore.jsont.grammar.schema.ast.JsonBaseType;

public interface EncodeDecoder {
    Object decode(JsonBaseType type, String raw);

    String encode(FieldModel field, Object object);
}
