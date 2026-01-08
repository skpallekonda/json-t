package org.jsont.execution;

import org.jsont.adapters.AdapterContext;
import org.jsont.grammar.types.ValueHolder;

public interface ValueConverter {
    Object convert(
            ValueHolder value,
            AdapterContext context);
}
