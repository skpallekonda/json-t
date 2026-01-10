package io.github.datakore.jsont.values;

import io.github.datakore.jsont.grammar.types.ValueType;

import java.util.Map;

public class ObjectValue implements DataValue {

    private final ValueType type;
    private final Object raw;
    private final Map<String, DataValue> fields;

    public ObjectValue(
            ValueType type,
            Object raw,
            Map<String, DataValue> elements
    ) {
        this.type = type;
        this.raw = raw;
        this.fields = elements;
    }

    public ValueType type() {
        return type;
    }

    public Object raw() {
        return raw;
    }

    public Map<String, DataValue> value() {
        return fields;
    }

    @Override
    public boolean isObject() {
        return true;
    }

    @Override
    public Map<String, DataValue> asObject() {
        return this.fields;
    }
}
