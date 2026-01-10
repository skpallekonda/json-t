package io.github.datakore.jsont.execution;

import io.github.datakore.jsont.adapters.AdapterContext;
import io.github.datakore.jsont.grammar.types.ValueHolder;

public interface ValueConverter {
    Object convert(
            ValueHolder value,
            AdapterContext context);
}
