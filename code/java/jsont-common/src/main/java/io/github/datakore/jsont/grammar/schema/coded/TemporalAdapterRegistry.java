package io.github.datakore.jsont.grammar.schema.coded;

import java.sql.Timestamp;
import java.time.ZoneId;
import java.time.temporal.TemporalAccessor;
import java.util.Calendar;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for {@link TemporalAdapter}s.
 * <p>
 * Allows registering custom adapters to handle non-standard temporal types.
 * </p>
 */
public class TemporalAdapterRegistry {
    private static final Map<Class<?>, TemporalAdapter<?>> ADAPTERS = new ConcurrentHashMap<>();

    static {
        // Register default adapters for legacy types
        register(new TemporalAdapter<Date>() {
            @Override
            public TemporalAccessor toTemporal(Date value) {
                return value.toInstant().atZone(ZoneId.systemDefault());
            }

            @Override
            public Date fromTemporal(TemporalAccessor temporal) {
                return Date.from(java.time.Instant.from(temporal));
            }

            @Override
            public Class<Date> getTargetType() {
                return Date.class;
            }
        });

        register(new TemporalAdapter<Timestamp>() {
            @Override
            public TemporalAccessor toTemporal(Timestamp value) {
                return value.toLocalDateTime();
            }

            @Override
            public Timestamp fromTemporal(TemporalAccessor temporal) {
                return Timestamp.valueOf(java.time.LocalDateTime.from(temporal));
            }

            @Override
            public Class<Timestamp> getTargetType() {
                return Timestamp.class;
            }
        });

        register(new TemporalAdapter<Calendar>() {
            @Override
            public TemporalAccessor toTemporal(Calendar value) {
                return value.toInstant().atZone(value.getTimeZone().toZoneId());
            }

            @Override
            public Calendar fromTemporal(TemporalAccessor temporal) {
                Calendar cal = Calendar.getInstance();
                cal.setTime(Date.from(java.time.Instant.from(temporal)));
                return cal;
            }

            @Override
            public Class<Calendar> getTargetType() {
                return Calendar.class;
            }
        });

        register(new TemporalAdapter<java.sql.Date>() {
            @Override
            public TemporalAccessor toTemporal(java.sql.Date value) {
                return value.toLocalDate();
            }

            @Override
            public java.sql.Date fromTemporal(TemporalAccessor temporal) {
                return java.sql.Date.valueOf(java.time.LocalDate.from(temporal));
            }

            @Override
            public Class<java.sql.Date> getTargetType() {
                return java.sql.Date.class;
            }
        });

        register(new TemporalAdapter<java.sql.Time>() {
            @Override
            public TemporalAccessor toTemporal(java.sql.Time value) {
                return value.toLocalTime();
            }

            @Override
            public java.sql.Time fromTemporal(TemporalAccessor temporal) {
                return java.sql.Time.valueOf(java.time.LocalTime.from(temporal));
            }

            @Override
            public Class<java.sql.Time> getTargetType() {
                return java.sql.Time.class;
            }
        });
    }

    public static <T> void register(TemporalAdapter<T> adapter) {
        ADAPTERS.put(adapter.getTargetType(), adapter);
    }

    @SuppressWarnings("unchecked")
    public static <T> TemporalAdapter<T> getAdapter(Class<T> type) {
        // Direct match
        TemporalAdapter<T> adapter = (TemporalAdapter<T>) ADAPTERS.get(type);
        if (adapter != null) {
            return adapter;
        }

        for (Map.Entry<Class<?>, TemporalAdapter<?>> entry : ADAPTERS.entrySet()) {
            if (entry.getKey().isAssignableFrom(type)) {
                return (TemporalAdapter<T>) entry.getValue();
            }
        }

        return null;
    }
}
