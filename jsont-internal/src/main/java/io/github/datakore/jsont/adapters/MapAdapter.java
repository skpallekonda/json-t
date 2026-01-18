package io.github.datakore.jsont.adapters;

import java.util.LinkedHashMap;
import java.util.Map;

public class MapAdapter implements SchemaAdapter<Map<String, Object>> {
    @Override
    @SuppressWarnings("unchecked")
    public Class<Map<String, Object>> logicalType() {
        return (Class<Map<String, Object>>) (Class<?>) Map.class;
    }

    @Override
    public Map<String, Object> createTarget() {
        return new LinkedHashMap<>();
    }


    @Override
    @SuppressWarnings("unchecked")
    public void set(Object target, String fieldName, Object valuee) {
        if (target instanceof Map) {
            ((Map<String, Object>) target).put(fieldName, valuee);
        }
    }

    @Override
    public Object get(Object target, String fieldName) {
        if (target instanceof Map) {
            return ((Map<?, ?>) target).get(fieldName);
        }
        return null;
    }
}
