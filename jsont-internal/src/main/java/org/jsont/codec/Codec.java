package org.jsont.codec;

import org.jsont.grammar.types.ValueType;

public interface Codec<T extends ValueType> {
    Object decode(Object raw, ValueType type);
}
