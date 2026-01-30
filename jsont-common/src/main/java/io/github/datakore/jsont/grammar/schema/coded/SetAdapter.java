package io.github.datakore.jsont.grammar.schema.coded;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A default {@link CollectionAdapter} for {@link Set}.
 * <p>
 * Converts between {@link List} and {@link HashSet}.
 * </p>
 *
 * @param <E> the element type
 */
public class SetAdapter<E> implements CollectionAdapter<Set<E>> {

    @Override
    @SuppressWarnings("unchecked")
    public Set<E> fromList(List<?> list) {
        if (list == null) return null;
        return new HashSet<>((List<E>) list);
    }

    @Override
    public List<?> toList(Set<E> collection) {
        if (collection == null) return null;
        return new ArrayList<>(collection);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Class<Set<E>> getTargetType() {
        return (Class<Set<E>>) (Class<?>) Set.class;
    }
}
