package io.github.datakore.jsont.codec;

import io.github.datakore.jsont.grammar.data.JsontScalarType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.format.DateTimeFormatter;

public class DateTimeSupport {

    private static final DateTimeFormatter DATE_FORMATTER_1 = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATE_FORMATTER_2 = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter TIME_FORMATTER_1 = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter TIME_FORMATTER_2 = DateTimeFormatter.ofPattern("HHmmss");
    private static final DateTimeFormatter DATETIME_FORMATTER_1 = DateTimeFormatter
            .ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS");
    private static final DateTimeFormatter DATETIME_FORMATTER_2 = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    public static Object handleDateTime(Object raw, JsontScalarType type) {
        switch (type) {
            case DATE:
                return handleDate(raw);
            case TIME:
                return handleTime(raw);
            case DATETIME:
                return handleDateTime(raw);
            case TIMESTAMP:
                return handleTimestamp(raw);
            default:
                throw new IllegalArgumentException("Invalid type: " + type);
        }
    }

    private static Object handleDate(Object raw) {
        if (raw instanceof String) {
            return DATE_FORMATTER_1.parse(raw.toString());
        } else if (raw instanceof BigDecimal) {
            return DATE_FORMATTER_2.parse(raw.toString());
        }
        throw new IllegalArgumentException("Invalid Date: " + raw);
    }

    private static Object handleTime(Object raw) {
        try {
            if (raw instanceof String) {
                TIME_FORMATTER_1.parse(raw.toString());
                return raw.toString();
            } else if (raw instanceof BigDecimal) {
                TIME_FORMATTER_2.parse(raw.toString());
                return String.valueOf(raw);
            }
            throw new IllegalArgumentException("Invalid time: " + raw);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid time: " + raw);
        }
    }

    private static Object handleDateTime(Object raw) {
        if (raw instanceof String) {
            return DATETIME_FORMATTER_1.parse(raw.toString());
        } else if (raw instanceof BigDecimal) {
            return DATETIME_FORMATTER_2.parse(raw.toString());
        }
        throw new IllegalArgumentException("Invalid type: " + raw.getClass());
    }

    private static Object handleTimestamp(Object raw) {
        if (raw instanceof String) {
            BigDecimal bd = new BigDecimal(((String) raw));
            return Instant.ofEpochMilli(bd.longValue());
        } else if (raw instanceof BigDecimal) {
            return Instant.ofEpochMilli(((BigDecimal) raw).longValue());
        }
        throw new IllegalArgumentException("Invalid type: " + raw.getClass());
    }
}
