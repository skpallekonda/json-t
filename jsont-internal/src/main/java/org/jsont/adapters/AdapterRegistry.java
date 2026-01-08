package org.jsont.adapters;

import java.util.LinkedHashMap;
import java.util.Map;

public class AdapterRegistry {
    private final Map<String, SchemaAdapter<?>> registry = new LinkedHashMap<>();
    private final MapAdapter defaultAdapter;

    public AdapterRegistry() {
        defaultAdapter = new MapAdapter();
    }

    public void register(SchemaAdapter<?> adapter) {
        registry.put(adapter.logicalType().getSimpleName(), adapter);
    }

    public SchemaAdapter<?> resolve(String schema) {
        if (schema != null && registry.containsKey(schema)) {
            return registry.get(schema);
        }
        return defaultAdapter;
    }

}
