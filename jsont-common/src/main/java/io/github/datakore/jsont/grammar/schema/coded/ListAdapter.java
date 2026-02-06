package io.github.datakore.jsont.grammar.schema.coded;

import java.util.List;

/**
 * A default {@link CollectionAdapter} for {@link List}.
 * <p>
 * This adapter simply passes the list through as-is, since JsonT uses List as its internal representation.
 * </p>
 *
 * @param <E> the element type
 */
public class ListAdapter<E> implements CollectionAdapter<List<E>> {

    @Override
    @SuppressWarnings("unchecked")
    public List<E> fromList(List<?> list) {
        return (List<E>) list;
    }

    @Override
    public List<?> toList(List<E> collection) {
        return collection;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Class<List<E>> getTargetType() {
        return (Class<List<E>>) (Class<?>) List.class;
    }
}
