package io.github.datakore.jsont.values;

import io.github.datakore.jsont.grammar.types.ValueType;

public class ScalarValue implements DataValue {
    private final ValueType type;
    private final Object raw;
    private final Object value;

    public ScalarValue(
            ValueType type,
            Object raw,
            Object value
    ) {
        this.type = type;
        this.raw = raw;
        this.value = value;
    }

    public ValueType type() {
        return type;
    }

    public Object raw() {
        return raw;
    }

    public Object value() {
        return value;
    }

    public <T> T as(Class<T> clazz) {
        return clazz.cast(value);
    }

    @Override
    public boolean isScalar() {
        return true;
    }

    @Override
    public Object scalar() {
        return this.value;
    }
}
