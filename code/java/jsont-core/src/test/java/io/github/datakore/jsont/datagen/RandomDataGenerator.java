package io.github.datakore.jsont.datagen;

import com.github.curiousoddman.rgxgen.RgxGen;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.*;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

public class RandomDataGenerator {

    // Use ThreadLocalRandom for better performance in multithreaded environments
    // SecureRandom is synchronized and slow for high-throughput data generation
    private final Random random = ThreadLocalRandom.current();

    private double minDefault(String typeName) {
        switch (typeName) {
            case "t_i16":
                return -999;
            case "t_u16":
            case "t_u32":
            case "t_u64":
                return 0;
            default:
                return -99999;
        }
    }

    private double maxDefault(String typeName) {
        switch (typeName) {
            case "t_i16":
            case "t_u16":
                return 9999;
            default:
                return 999999;
        }
    }


    public Object generateNumber(String typeName, Double minValue, Double maxValue, int precision) {
        double min = minValue == null ? minDefault(typeName) : Math.max(minValue, minDefault(typeName));
        double max = maxValue == null ? maxDefault(typeName) : Math.min(maxValue, maxDefault(typeName));
        boolean negated = false;
        if (Math.abs(min) > Math.abs(max)) {
            double temp = min;
            min = Math.abs(max);
            max = Math.abs(temp);
            negated = true;
        }
        if (precision == 0) {
            precision = 4;
        }

        // Use ThreadLocalRandom directly
        double randomValue = ThreadLocalRandom.current().nextDouble();
        randomValue = min + (max - min) * randomValue;
        
        // Avoid BigDecimal for simple types if possible, but keeping logic for now.
        // Optimization: Only use BigDecimal when necessary (d32, d64, d128) or for final conversion
        
        switch (typeName) {
            case "t_i16":
                return (short) (negated ? -randomValue : randomValue);
            case "t_i32":
                return (int) (negated ? -randomValue : randomValue);
            case "t_i64":
                return (long) (negated ? -randomValue : randomValue);
            case "t_u16":
                return (short) Math.abs(randomValue);
            case "t_u32":
                return (int) Math.abs(randomValue);
            case "t_u64":
                return (long) Math.abs(randomValue);
            case "t_d32":
                // Float
                return BigDecimal.valueOf(negated ? -randomValue : randomValue)
                        .setScale(precision, RoundingMode.HALF_UP)
                        .floatValue();
            case "t_d64":
                // Double
                return BigDecimal.valueOf(negated ? -randomValue : randomValue)
                        .setScale(precision, RoundingMode.HALF_UP)
                        .doubleValue();
            case "t_d128":
                // BigDecimal
                return BigDecimal.valueOf(negated ? -randomValue : randomValue)
                        .setScale(precision, RoundingMode.HALF_UP);
            default:
                throw new IllegalArgumentException("Unknown number type: " + typeName);
        }
    }

    public Object generateDate(String fieldName) {
        long minDay = LocalDate.of(2000, 1, 1).toEpochDay();
        long maxDay = LocalDate.of(2030, 12, 31).toEpochDay();
        // ThreadLocalRandom for range
        long randomDay = ThreadLocalRandom.current().nextLong(minDay, maxDay);

        switch (fieldName) {
            case "t_date":
                return LocalDate.ofEpochDay(randomDay);
            case "t_time":
                return LocalTime.of(ThreadLocalRandom.current().nextInt(0, 24), ThreadLocalRandom.current().nextInt(0, 60), ThreadLocalRandom.current().nextInt(0, 60));
            case "t_dtm":
                return LocalDateTime.of(LocalDate.ofEpochDay(randomDay), LocalTime.of(ThreadLocalRandom.current().nextInt(0, 24), ThreadLocalRandom.current().nextInt(0, 60), ThreadLocalRandom.current().nextInt(0, 60)));
            case "t_ts":
                Instant instant = Instant.now().minusSeconds(ThreadLocalRandom.current().nextInt(1000000));
                return new Timestamp(instant.toEpochMilli());
            case "t_tsz":
                return ZonedDateTime.now(ZoneId.of("UTC")).minusSeconds(ThreadLocalRandom.current().nextInt(1000000));
            case "t_inst":
                return Instant.now().minusSeconds(ThreadLocalRandom.current().nextInt(1000000));
            case "t_insz":
                // insz in DateEntry is ZonedDateTime
                return ZonedDateTime.now().minusSeconds(ThreadLocalRandom.current().nextInt(1000000));
            case "t_yr":
                return Year.of(1900 + ThreadLocalRandom.current().nextInt(200));
            case "t_mon":
                return Month.of(1 + ThreadLocalRandom.current().nextInt(12));
            case "t_day":
                return DayOfWeek.of(1 + ThreadLocalRandom.current().nextInt(7));
            case "t_ym":
                return YearMonth.of(1900 + ThreadLocalRandom.current().nextInt(200), 1 + ThreadLocalRandom.current().nextInt(12));
            case "t_md":
                return MonthDay.of(1 + ThreadLocalRandom.current().nextInt(12), 1 + ThreadLocalRandom.current().nextInt(28));
            default:
                return null;
        }
    }

    public String generateString(int minLength, int maxLength) {
        int length = ThreadLocalRandom.current().nextInt(minLength, maxLength + 1);
        StringBuilder sb = new StringBuilder(length);
        int wordLength = Math.min(4, ThreadLocalRandom.current().nextInt(10));
        int wordIndex = 0;
        for (int i = 0; i < length; i++, wordIndex++) {
            if (wordIndex == wordLength) {
                sb.append(' ');
                wordLength = Math.min(4, ThreadLocalRandom.current().nextInt(10));
                wordIndex = 0;
            }
            sb.append((char) ('a' + ThreadLocalRandom.current().nextInt(26)));
        }
        return sb.toString();
    }

    public String randomMatchingPattern(String pattern) {
        RgxGen rgxGen = new RgxGen(pattern);
        return rgxGen.generate();
    }

    public int generateInt(int minLength, int maxLength) {
        // Generate a random double within the range [min, max)
        double randomValue = ThreadLocalRandom.current().nextDouble(minLength, maxLength);
        return (int) Math.abs(randomValue);
    }
}
