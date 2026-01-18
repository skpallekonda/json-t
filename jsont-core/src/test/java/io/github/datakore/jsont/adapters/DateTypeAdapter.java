package io.github.datakore.jsont.adapters;

import io.github.datakore.jsont.datagen.DataGen;
import io.github.datakore.jsont.datagen.RandomDataGenerator;
import io.github.datakore.jsont.entity.DateEntry;
import io.github.datakore.jsont.grammar.schema.constraints.FieldConstraint;

import java.sql.Timestamp;
import java.time.*;

public class DateTypeAdapter implements DataGen<DateEntry>, SchemaAdapter<DateEntry> {
    private static final String[] FIELD_NAMES = {"t_date", "t_time", "t_dtm", "t_ts", "t_tsz", "t_insz", "t_yr", "t_mon", "t_day", "t_ym", "t_md"};
    private RandomDataGenerator generator = new RandomDataGenerator();

    @Override
    public Class<DateEntry> logicalType() {
        return DateEntry.class;
    }


    @Override
    public String[] fieldNames() {
        return FIELD_NAMES;
    }

    @Override
    public FieldConstraint[] fieldConstraints(String fieldName) {
        return new FieldConstraint[0];
    }

    @Override
    public DateEntry generate() {
        DateEntry dateEntry = createTarget();
        for (String fieldName : fieldNames()) {
            set(dateEntry, fieldName, generator.generateDate(fieldName));
        }
        return dateEntry;
    }

    @Override
    public DateEntry createTarget() {
        return new DateEntry();
    }

    @Override
    public void set(Object target, String fieldName, Object valuee) {
        DateEntry entry = (DateEntry) target;
        switch (fieldName) {
            case "t_date":
                entry.setDate((LocalDate) valuee);
                break;
            case "t_time":
                entry.setTime((LocalTime) valuee);
                break;
            case "t_dtm":
                entry.setDtm((LocalDateTime) valuee);
                break;
            case "t_ts":
                entry.setTs((Timestamp) valuee);
                break;
            case "t_tsz":
                entry.setTsz((ZonedDateTime) valuee);
                break;
            case "t_inst":
                entry.setInst((Instant) valuee);
                break;
            case "t_insz":
                entry.setInsz((ZonedDateTime) valuee);
                break;
            case "t_yr":
                entry.setYr((Year) valuee);
                break;
            case "t_mon":
                entry.setMon((Month) valuee);
                break;
            case "t_day":
                entry.setDay((DayOfWeek) valuee);
                break;
            case "t_ym":
                entry.setYm((YearMonth) valuee);
                break;
            case "t_md":
                entry.setMd((MonthDay) valuee);
                break;
            default:
        }
    }

    @Override
    public Object get(Object target, String fieldName) {
        DateEntry entry = (DateEntry) target;
        switch (fieldName) {
            case "t_date":
                return entry.getDate();
            case "t_time":
                return entry.getTime();
            case "t_dtm":
                return entry.getDtm();
            case "t_ts":
                return entry.getTs();
            case "t_tsz":
                return entry.getTsz();
            case "t_inst":
                return entry.getInst();
            case "t_insz":
                return entry.getInsz();
            case "t_yr":
                return entry.getYr();
            case "t_mon":
                return entry.getMon();
            case "t_day":
                return entry.getDay();
            case "t_ym":
                return entry.getYm();
            case "t_md":
                return entry.getMd();
            default:
                return null;
        }
    }
}
