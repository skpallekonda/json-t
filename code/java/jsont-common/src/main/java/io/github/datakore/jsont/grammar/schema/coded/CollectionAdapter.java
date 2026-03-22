package io.github.datakore.jsont.grammar.schema.coded;

import java.util.List;

/**
 * Adapter interface for converting between custom collection types and standard Java Lists.
 * <p>
 * This allows users to use their own collection classes (e.g., Sets, Arrays, Custom Lists)
 * with JsonT's encoding and decoding mechanisms.
 * </p>
 *
 * @param <C> the custom collection type
 */
public interface CollectionAdapter<C> {

    /**
     * Converts a standard List (parsed from JsonT) to the custom collection type.
     * This is used during decoding (deserialization).
     *
     * @param list the list of elements
     * @return the custom collection containing the elements
     */
    C fromList(List<?> list);

    /**
     * Converts a custom collection to a standard List.
     * This is used during encoding (serialization).
     *
     * @param collection the custom collection
     * @return a List containing the elements
     */
    List<?> toList(C collection);

    /**
     * Returns the class of the custom collection type handled by this adapter.
     *
     * @return the target class
     */
    Class<C> getTargetType();
}
