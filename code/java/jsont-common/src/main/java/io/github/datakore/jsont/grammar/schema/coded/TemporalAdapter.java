package io.github.datakore.jsont.grammar.schema.coded;

import java.time.temporal.TemporalAccessor;

/**
 * Adapter interface for converting between custom temporal types and standard Java Time API types.
 * <p>
 * This allows users to use their own date/time classes (e.g., Joda-Time, java.util.Date)
 * with JsonT's encoding and decoding mechanisms.
 * </p>
 *
 * @param <T> the custom temporal type
 */
public interface TemporalAdapter<T> {

    /**
     * Converts a custom temporal object to a {@link TemporalAccessor}.
     * This is used during encoding (serialization).
     *
     * @param value the custom temporal object
     * @return a {@link TemporalAccessor} representing the value
     */
    TemporalAccessor toTemporal(T value);

    /**
     * Converts a {@link TemporalAccessor} to the custom temporal type.
     * This is used during decoding (deserialization).
     *
     * @param temporal the standard temporal object parsed from JsonT
     * @return the custom temporal object
     */
    T fromTemporal(TemporalAccessor temporal);

    /**
     * Returns the class of the custom temporal type handled by this adapter.
     *
     * @return the target class
     */
    Class<T> getTargetType();
}
