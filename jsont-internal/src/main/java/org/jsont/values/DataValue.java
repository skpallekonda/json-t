package org.jsont.values;

import org.jsont.grammar.types.ValueType;

import java.util.List;
import java.util.Map;

public interface DataValue {
    ValueType type();

    Object raw();

    default Object scalar() {
        throw new IllegalStateException("Not a scalar value");
    }

    default boolean isScalar() {
        return false;
    }

    default boolean isArray() {
        return false;
    }

    default boolean isObject() {
        return false;
    }

    default List<DataValue> asArray() {
        throw new IllegalStateException("Not an array value");
    }

    default Map<String, DataValue> asObject() {
        throw new IllegalStateException("Not an object value");
    }
}
