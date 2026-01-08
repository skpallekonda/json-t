package org.jsont.values;

import org.jsont.grammar.types.ValueType;

import java.util.List;

public class ArrayValue implements DataValue {

    private final ValueType type;
    private final Object raw;
    private final List<DataValue> elements;

    public ArrayValue(ValueType type, Object raw, List<DataValue> elements) {
        this.type = type;
        this.raw = raw;
        this.elements = elements;
    }

    public ValueType type() {
        return type;
    }

    public Object raw() {
        return raw;
    }

    public List<DataValue> value() {
        return elements;
    }

    @Override
    public boolean isArray() {
        return true;
    }

    @Override
    public List<DataValue> asArray() {
        return elements;
    }
}
