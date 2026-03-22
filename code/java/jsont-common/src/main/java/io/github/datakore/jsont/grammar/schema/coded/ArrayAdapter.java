package io.github.datakore.jsont.grammar.schema.coded;

import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.List;

/**
 * A generic {@link CollectionAdapter} for arrays.
 * <p>
 * Usage: {@code CollectionAdapterRegistry.register(new ArrayAdapter<>(User.class));}
 * </p>
 *
 * @param <E> the component type of the array
 */
public class ArrayAdapter<E> implements CollectionAdapter<E[]> {

    private final Class<E> componentType;
    private final Class<E[]> arrayType;

    @SuppressWarnings("unchecked")
    public ArrayAdapter(Class<E> componentType) {
        this.componentType = componentType;
        // Create a dummy array to get the class type
        this.arrayType = (Class<E[]>) Array.newInstance(componentType, 0).getClass();
    }

    @Override
    @SuppressWarnings("unchecked")
    public E[] fromList(List<?> list) {
        if (list == null) return null;
        E[] array = (E[]) Array.newInstance(componentType, list.size());
        // We assume the list elements are compatible with E
        // If conversion is needed, it should have been done before (e.g. in ConvertStage)
        return list.toArray(array);
    }

    @Override
    public List<?> toList(E[] collection) {
        if (collection == null) return null;
        return Arrays.asList(collection);
    }

    @Override
    public Class<E[]> getTargetType() {
        return arrayType;
    }
}
