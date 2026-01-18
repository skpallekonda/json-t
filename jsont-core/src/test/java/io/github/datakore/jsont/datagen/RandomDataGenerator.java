package io.github.datakore.jsont.datagen;

import com.github.curiousoddman.rgxgen.RgxGen;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.*;

public class RandomDataGenerator {

    private final SecureRandom random = new SecureRandom();

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

        // Generate a random double within the range [min, max)
        double randomValue = random.nextDouble();
        randomValue = min + (max - min) * randomValue;
        BigDecimal bigDecimal = new BigDecimal(String.valueOf(randomValue));
        if (negated) {
            bigDecimal = bigDecimal.negate();
        }

        switch (typeName) {
            case "t_i16":
                return bigDecimal.shortValue();
            case "t_i32":
                return bigDecimal.intValue();
            case "t_i64":
                return bigDecimal.longValue();
            case "t_u16":
                return bigDecimal.abs().shortValue();
            case "t_u32":
                return bigDecimal.abs().intValue();
            case "t_u64":
                return bigDecimal.abs().longValue();
            case "t_d32":
                bigDecimal = bigDecimal.setScale(precision, RoundingMode.HALF_UP);
                return bigDecimal.floatValue();
            case "t_d64":
                bigDecimal = bigDecimal.setScale(precision, RoundingMode.HALF_UP);
                return bigDecimal.doubleValue();
            case "t_d128":
                bigDecimal = bigDecimal.setScale(precision, RoundingMode.HALF_UP);
                return bigDecimal;
            default:
                throw new IllegalArgumentException("Unknown number type: " + typeName);
        }
    }

    public Object generateDate(String fieldName) {
        long minDay = LocalDate.of(2000, 1, 1).toEpochDay();
        long maxDay = LocalDate.of(2030, 12, 31).toEpochDay();
        long randomDay = minDay + random.nextInt(1, (int) (maxDay - minDay));

        switch (fieldName) {
            case "t_date":
                return LocalDate.ofEpochDay(randomDay);
            case "t_time":
                return LocalTime.of(random.nextInt(1, 24), random.nextInt(1, 60), random.nextInt(1, 60));
            case "t_dtm":
                return LocalDateTime.of(LocalDate.ofEpochDay(randomDay), LocalTime.of(random.nextInt(1, 24), random.nextInt(1, 60), random.nextInt(1, 60)));
            case "t_ts":
                Instant instant = Instant.now().minusSeconds(random.nextInt(1000000));
                return new Timestamp(instant.toEpochMilli());
            case "t_tsz":
                return ZonedDateTime.now(ZoneId.of("UTC")).minusSeconds(random.nextInt(1000000));
            case "t_inst":
                return Instant.now().minusSeconds(random.nextInt(1000000));
            case "t_insz":
                // insz in DateEntry is ZonedDateTime
                return ZonedDateTime.now().minusSeconds(random.nextInt(1000000));
            case "t_yr":
                return Year.of(1900 + random.nextInt(200));
            case "t_mon":
                return Month.of(1 + random.nextInt(12));
            case "t_day":
                return DayOfWeek.of(1 + random.nextInt(7));
            case "t_ym":
                return YearMonth.of(1900 + random.nextInt(200), 1 + random.nextInt(12));
            case "t_md":
                return MonthDay.of(1 + random.nextInt(12), 1 + random.nextInt(28));
            default:
                return null;
        }
    }

    public String generateString(int minLength, int maxLength) {
        int length = minLength + random.nextInt(maxLength - minLength + 1);
        StringBuilder sb = new StringBuilder(length);
        int wordLength = Math.min(4, random.nextInt(10));
        int wordIndex = 0;
        for (int i = 0; i < length; i++, wordIndex++) {
            if (wordIndex == wordLength) {
                sb.append(' ');
                wordLength = Math.min(4, random.nextInt(10));
                if (wordLength > 4) {
//                    System.out.println("FLASH FLASH FLASH");
                }
                wordIndex = 0;
            }
            sb.append((char) ('a' + random.nextInt(26)));
        }
        return sb.toString();
    }

    public String randomMatchingPattern(String pattern) {
        RgxGen rgxGen = new RgxGen(pattern);
        return rgxGen.generate();
    }

    public int generateInt(int minLength, int maxLength) {
        // Generate a random double within the range [min, max)
        double randomValue = minLength + (maxLength - minLength) * random.nextDouble();
        BigDecimal bigDecimal = new BigDecimal(randomValue);
        return bigDecimal.abs().shortValue();
    }
}
