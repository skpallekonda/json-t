package org.jsont.extractors;

import org.jsont.grammar.data.ValueNode;
import org.jsont.grammar.types.ValueHolder;
import org.jsont.grammar.types.ValueType;

public interface ValueNodeExtractor {
    ValueHolder extract(ValueNode node, ValueType valueType, boolean optional);
}
