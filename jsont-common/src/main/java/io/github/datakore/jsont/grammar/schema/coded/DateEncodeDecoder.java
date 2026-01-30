package io.github.datakore.jsont.grammar.schema.coded;

import io.github.datakore.jsont.exception.DataException;
import io.github.datakore.jsont.grammar.schema.ast.FieldModel;
import io.github.datakore.jsont.grammar.schema.ast.JsonBaseType;
import io.github.datakore.jsont.grammar.types.ScalarType;
import io.github.datakore.jsont.util.StringUtils;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.time.temporal.TemporalAccessor;
import java.util.Locale;

public class DateEncodeDecoder implements EncodeDecoder {
    final DateTimeFormatter yyyyMMdd = DateTimeFormatter.ofPattern("yyyyMMdd");
    final DateTimeFormatter yyyyMMddHHmmss = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    final DateTimeFormatter HHmmss = DateTimeFormatter.ofPattern("HHmmss");
    final DateTimeFormatter yyyyMMddTHHmmss = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    final DateTimeFormatter yyyyMMddTHHmmssZ = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssX"); //YYYY-MM-DDTHH:mm:ssX
    final DateTimeFormatter yyyyMMddTHHmmssPlusHHmm = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX"); //YYYY-MM-DDTHH:mm:ssXXX
    final DateTimeFormatter yyyy = DateTimeFormatter.ofPattern("yyyy"); //YYYY
    final DateTimeFormatter monthFormatter = new DateTimeFormatterBuilder()
            .parseCaseInsensitive()         // Allows "jan", "Jan", "JAN"
            .appendOptional(DateTimeFormatter.ofPattern("MMM"))  // Try Text (Jan) first
            .appendOptional(DateTimeFormatter.ofPattern("MM"))   // Try Digits (01) next
            .toFormatter(Locale.ENGLISH);
    final DateTimeFormatter yearMonth = new DateTimeFormatterBuilder()
            .parseCaseInsensitive()         // Allows "jan", "Jan", "JAN"
            .appendOptional(DateTimeFormatter.ofPattern("yyyy-MMM"))  // Try Text (Jan) first
            .appendOptional(DateTimeFormatter.ofPattern("yyyy-MM"))   // Try Digits (01) next
            .appendOptional(DateTimeFormatter.ofPattern("yyyy/MMM"))  // Try Text (Jan) first
            .appendOptional(DateTimeFormatter.ofPattern("yyyy/MM"))   // Try Digits (01) next
            .appendOptional(DateTimeFormatter.ofPattern("yyyyMM"))   // Try Digits (01) next
            .toFormatter(Locale.ENGLISH);
    final DateTimeFormatter monthDay = new DateTimeFormatterBuilder().parseCaseInsensitive()
            .appendOptional(DateTimeFormatter.ofPattern("MMM-dd"))
            .appendOptional(DateTimeFormatter.ofPattern("MM-dd"))
            .appendOptional(DateTimeFormatter.ofPattern("MMM/dd"))
            .appendOptional(DateTimeFormatter.ofPattern("MM/dd"))
            .appendOptional(DateTimeFormatter.ofPattern("MMd"))
            .appendOptional(DateTimeFormatter.ofPattern("dd-MM"))
            .appendOptional(DateTimeFormatter.ofPattern("dd-MMM"))
            .appendOptional(DateTimeFormatter.ofPattern("dd/MM"))
            .appendOptional(DateTimeFormatter.ofPattern("dd/MMM"))
            .appendOptional(DateTimeFormatter.ofPattern("ddMM"))
            .toFormatter(Locale.ENGLISH);

    // Encoders (Single Format)
    final DateTimeFormatter monthEncoder = DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH);
    final DateTimeFormatter yearMonthEncoder = DateTimeFormatter.ofPattern("yyyy-MM", Locale.ENGLISH);
    final DateTimeFormatter monthDayEncoder = DateTimeFormatter.ofPattern("MM-dd", Locale.ENGLISH);


    @Override
    public Object decode(JsonBaseType jsonBaseType, String raw) {
        if (raw == null || StringUtils.isBlank(raw))
            return null;
        final String value = StringUtils.removeQuotes(raw);
        assert value != null;
        switch (jsonBaseType) {
            case DATE:
                return yyyyMMdd.parse(value, LocalDate::from);
            case TIME:
                return HHmmss.parse(value, LocalTime::from);
            case DATETIME:
                return yyyyMMddHHmmss.parse(value, LocalDateTime::from);
            case TIMESTAMP:
                return new Timestamp(new BigDecimal(value).longValue());
            case INST:
                try {
                    return yyyyMMddTHHmmss.parse(value, Instant::from);
                } catch (Exception e) {
                    return Instant.ofEpochSecond(new BigDecimal(value).longValue());
                }
            case TSZ:
                return yyyyMMddTHHmmssZ.parse(value, ZonedDateTime::from);
            case INSTZ:
                return yyyyMMddTHHmmssPlusHHmm.parse(value, ZonedDateTime::from);
            case YEAR:
                return yyyy.parse(value, Year::from);
            case MON:
                return monthFormatter.parse(value, Month::from);
            case DAY:
                return MonthDay.parse(value);
            case YEARMON:
                return yearMonth.parseBest(value, YearMonth::from);
            case MNDAY:
                return monthDay.parseBest(value, MonthDay::from);
            default:
                String message = String.format("Invalid type %s, to decode < %s >", jsonBaseType.identifier(), raw);
                throw new DataException(message);
        }
    }

    @Override
    public String encode(FieldModel fieldModel, Object obj) {
        if (obj == null) {
            return "null";
        }
        TemporalAccessor temporal = getTemporalAccessor(obj);
        JsonBaseType jsonBaseType = ((ScalarType) fieldModel.getFieldType()).elementType();
        switch (jsonBaseType) {
            case DATE:
                return yyyyMMdd.format(temporal);
            case TIME:
                return HHmmss.format(temporal);
            case DATETIME:
                return yyyyMMddHHmmss.format(temporal);
            case TIMESTAMP:
                return String.valueOf(getEpochMillis(temporal));
            case INST:
                try {
                    return surroundByQuote(yyyyMMddTHHmmss.format(temporal));
                } catch (Exception e) {
                    return surroundByQuote(String.valueOf(getEpochMillis(temporal)));
                }
            case TSZ:
                return surroundByQuote(yyyyMMddTHHmmssZ.format(temporal));
            case INSTZ:
                return surroundByQuote(yyyyMMddTHHmmssPlusHHmm.format(temporal));
            case YEAR:
                return surroundByQuote(yyyy.format(temporal));
            case MON:
                return surroundByQuote(monthEncoder.format(temporal));
            case DAY:
                return surroundByQuote(DayOfWeek.from(temporal).toString());
            case YEARMON:
                return surroundByQuote(yearMonthEncoder.format(temporal));
            case MNDAY:
                return surroundByQuote(monthDayEncoder.format(temporal));
            default:
                String message = String.format("Invalid type %s, to encode < %s >", jsonBaseType.identifier(), obj);
                throw new DataException(message);
        }
    }

    @SuppressWarnings("unchecked")
    private static TemporalAccessor getTemporalAccessor(Object obj) {
        // 1. Native Java Time support
        if (obj instanceof TemporalAccessor) {
            return (TemporalAccessor) obj;
        }

        // 2. Check Registry for custom adapters (including legacy types like Date, Calendar)
        TemporalAdapter adapter = TemporalAdapterRegistry.getAdapter(obj.getClass());
        if (adapter != null) {
            return adapter.toTemporal(obj);
        }

        throw new IllegalArgumentException("Unsupported type: " + obj.getClass() + ". Please register a TemporalAdapter.");
    }

    long getEpochMillis(TemporalAccessor temporal) {
        // Case 1: If it already contains an Instant (Instant, ZonedDateTime, OffsetDateTime)
        if (temporal.isSupported(ChronoField.INSTANT_SECONDS)) {
            return Instant.from(temporal).toEpochMilli();
        }

        // Case 2: If it is a LocalDateTime (has time, no zone) -> Add System Zone
        if (temporal instanceof LocalDateTime) {
            return ((LocalDateTime) temporal).atZone(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli();
        }

        // Case 3: If it is a LocalDate (no time, no zone) -> Start of Day + System Zone
        if (temporal instanceof LocalDate) {
            return ((LocalDate) temporal).atStartOfDay(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli();
        }

        // Case 4: Fallback for generic TemporalAccessors that might look like LDT/LD
        // Try to query purely for Date/Time components
        LocalDate date = temporal.query(LocalDate::from);
        LocalTime time = temporal.query(LocalTime::from);

        if (date != null) {
            // Default time to 00:00:00 if missing
            if (time == null) time = LocalTime.MIDNIGHT;
            return LocalDateTime.of(date, time)
                    .atZone(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli();
        }

        throw new IllegalArgumentException("Cannot extract Epoch Millis from: " + temporal);
    }

    private String surroundByQuote(String value) {
        return "\"" + value + "\"";
    }
}
