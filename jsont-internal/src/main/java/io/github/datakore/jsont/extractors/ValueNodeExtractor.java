package io.github.datakore.jsont.extractors;

import io.github.datakore.jsont.grammar.data.ValueNode;
import io.github.datakore.jsont.grammar.types.ValueHolder;
import io.github.datakore.jsont.grammar.types.ValueType;

public interface ValueNodeExtractor {
    ValueHolder extract(ValueNode node, ValueType valueType, boolean optional);
}
