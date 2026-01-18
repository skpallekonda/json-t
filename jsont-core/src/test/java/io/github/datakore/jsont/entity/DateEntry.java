package io.github.datakore.jsont.entity;

import java.sql.Timestamp;
import java.time.*;

public class DateEntry {
    private LocalDate date;
    private LocalTime time;
    private LocalDateTime dtm;
    private Timestamp ts;
    private ZonedDateTime tsz;
    private Instant inst;
    private ZonedDateTime insz;
    private Year yr;
    private Month mon;
    private DayOfWeek day;
    private YearMonth ym;
    private MonthDay md;

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public LocalTime getTime() {
        return time;
    }

    public void setTime(LocalTime time) {
        this.time = time;
    }

    public LocalDateTime getDtm() {
        return dtm;
    }

    public void setDtm(LocalDateTime dtm) {
        this.dtm = dtm;
    }

    public Timestamp getTs() {
        return ts;
    }

    public void setTs(Timestamp ts) {
        this.ts = ts;
    }

    public ZonedDateTime getTsz() {
        return tsz;
    }

    public void setTsz(ZonedDateTime tsz) {
        this.tsz = tsz;
    }

    public Instant getInst() {
        return inst;
    }

    public void setInst(Instant inst) {
        this.inst = inst;
    }

    public ZonedDateTime getInsz() {
        return insz;
    }

    public void setInsz(ZonedDateTime insz) {
        this.insz = insz;
    }

    public Year getYr() {
        return yr;
    }

    public void setYr(Year yr) {
        this.yr = yr;
    }

    public Month getMon() {
        return mon;
    }

    public void setMon(Month mon) {
        this.mon = mon;
    }

    public DayOfWeek getDay() {
        return day;
    }

    public void setDay(DayOfWeek day) {
        this.day = day;
    }

    public YearMonth getYm() {
        return ym;
    }

    public void setYm(YearMonth ym) {
        this.ym = ym;
    }

    public MonthDay getMd() {
        return md;
    }

    public void setMd(MonthDay md) {
        this.md = md;
    }

}
