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
import java.util.Calendar;
import java.util.Date;
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
        switch (jsonBaseType.name()) {
            case "K_DATE":
                return yyyyMMdd.parse(value, LocalDate::from);
            case "K_TIME":
                return HHmmss.parse(value, LocalTime::from);
            case "K_DATETIME":
                return yyyyMMddHHmmss.parse(value, LocalDateTime::from);
            case "K_TIMESTAMP":
                return new Timestamp(new BigDecimal(value).longValue());
            case "K_INST":
                try {
                    return yyyyMMddTHHmmss.parse(value, Instant::from);
                } catch (Exception e) {
                    return Instant.ofEpochSecond(new BigDecimal(value).longValue());
                }
            case "K_TSZ":
                return yyyyMMddTHHmmssZ.parse(value, ZonedDateTime::from);
            case "K_INSTZ":
                return yyyyMMddTHHmmssPlusHHmm.parse(value, ZonedDateTime::from);
            case "K_YEAR":
                return yyyy.parse(value, Year::from);
            case "K_MON":
                return monthFormatter.parse(value, Month::from);
            case "K_DAY":
                return MonthDay.parse(value);
            case "K_YEARMON":
                return yearMonth.parseBest(value, YearMonth::from);
            case "K_MNDAY":
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
        switch (jsonBaseType.name()) {
            case "K_DATE":
                return yyyyMMdd.format(temporal);
            case "K_TIME":
                return HHmmss.format(temporal);
            case "K_DATETIME":
                return yyyyMMddHHmmss.format(temporal);
            case "K_TIMESTAMP":
                return String.valueOf(getEpochMillis(temporal));
            case "K_INST":
                try {
                    return surroundByQuote(yyyyMMddTHHmmss.format(temporal));
                } catch (Exception e) {
                    return surroundByQuote(String.valueOf(getEpochMillis(temporal)));
                }
            case "K_TSZ":
                return surroundByQuote(yyyyMMddTHHmmssZ.format(temporal));
            case "K_INSTZ":
                return surroundByQuote(yyyyMMddTHHmmssPlusHHmm.format(temporal));
            case "K_YEAR":
                return surroundByQuote(yyyy.format(temporal));
            case "K_MON":
                return surroundByQuote(monthEncoder.format(temporal));
            case "K_DAY":
                return surroundByQuote(DayOfWeek.from(temporal).toString());
            case "K_YEARMON":
                return surroundByQuote(yearMonthEncoder.format(temporal));
            case "K_MNDAY":
                return surroundByQuote(monthDayEncoder.format(temporal));
            default:
                String message = String.format("Invalid type %s, to encode < %s >", jsonBaseType.identifier(), obj);
                throw new DataException(message);
        }
    }

    private static TemporalAccessor getTemporalAccessor(Object obj) {
        TemporalAccessor temporal;

        // 1. Normalize everything to a Modern Java Time object using instanceof
        if (obj instanceof TemporalAccessor) {
            // Handles LocalDate, LocalDateTime, ZonedDateTime, etc.
            temporal = (TemporalAccessor) obj;
        } else if (obj instanceof Timestamp) {
            // Legacy SQL Timestamp -> LocalDateTime
            temporal = ((Timestamp) obj).toLocalDateTime();
        } else if (obj instanceof Date) {
            // Legacy java.util.Date / java.sql.Date -> ZonedDateTime (System Default)
            temporal = ((Date) obj).toInstant().atZone(ZoneId.systemDefault());
        } else if (obj instanceof Calendar) {
            // Legacy Calendar -> ZonedDateTime (Uses Calendar's specific TimeZone)
            Calendar cal = (Calendar) obj;
            temporal = cal.toInstant().atZone(cal.getTimeZone().toZoneId());
        } else {
            throw new IllegalArgumentException("Unsupported type: " + obj.getClass());
        }
        return temporal;
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
