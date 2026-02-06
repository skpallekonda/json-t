package io.github.datakore.jsont.grammar.schema.coded;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for {@link CollectionAdapter}s.
 * <p>
 * Allows registering custom adapters to handle various collection types (Sets, Arrays, etc.).
 * </p>
 */
public class CollectionAdapterRegistry {
    private static final Map<Class<?>, CollectionAdapter<?>> ADAPTERS = new ConcurrentHashMap<>();

    static {
        // Register default adapters
        register(new ListAdapter<>());
        register(new SetAdapter<>());
    }

    public static <C> void register(CollectionAdapter<C> adapter) {
        ADAPTERS.put(adapter.getTargetType(), adapter);
    }

    @SuppressWarnings("unchecked")
    public static <C> CollectionAdapter<C> getAdapter(Class<C> type) {
        // Direct match
        CollectionAdapter<C> adapter = (CollectionAdapter<C>) ADAPTERS.get(type);
        if (adapter != null) {
            return adapter;
        }

        // Handle Arrays dynamically
        if (type.isArray()) {
            Class<?> componentType = type.getComponentType();
            // Create a new ArrayAdapter for this specific array type
            // We use raw types here because generic creation of ArrayAdapter<T> is hard with wildcards,
            // but ArrayAdapter handles the types correctly internally via reflection.
            ArrayAdapter arrayAdapter = new ArrayAdapter(componentType);
            
            // Cache it for future use
            ADAPTERS.put(type, arrayAdapter);
            return (CollectionAdapter<C>) arrayAdapter;
        }

        // Check for superclass/interface match
        for (Map.Entry<Class<?>, CollectionAdapter<?>> entry : ADAPTERS.entrySet()) {
            if (entry.getKey().isAssignableFrom(type)) {
                return (CollectionAdapter<C>) entry.getValue();
            }
        }
        
        return null;
    }
}
