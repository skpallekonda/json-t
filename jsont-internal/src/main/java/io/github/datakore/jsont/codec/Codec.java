package io.github.datakore.jsont.codec;

import io.github.datakore.jsont.grammar.types.ValueType;

public interface Codec<T extends ValueType> {
    Object decode(Object raw, ValueType type);
}
