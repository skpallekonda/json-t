package io.github.datakore.jsont.entity;

import java.math.BigDecimal;

public class NumberEntry {
    private short i16;
    private int i32;
    private long i64;
    private short u16;
    private int u32;
    private long u64;
    private float d32;
    private double d64;
    private BigDecimal d128;

    public short getI16() {
        return i16;
    }

    public void setI16(short i16) {
        this.i16 = i16;
    }

    public int getI32() {
        return i32;
    }

    public void setI32(int i32) {
        this.i32 = i32;
    }

    public long getI64() {
        return i64;
    }

    public void setI64(long i64) {
        this.i64 = i64;
    }

    public short getU16() {
        return u16;
    }

    public void setU16(short u16) {
        this.u16 = u16;
    }

    public int getU32() {
        return u32;
    }

    public void setU32(int u32) {
        this.u32 = u32;
    }

    public long getU64() {
        return u64;
    }

    public void setU64(long u64) {
        this.u64 = u64;
    }

    public float getD32() {
        return d32;
    }

    public void setD32(float d32) {
        this.d32 = d32;
    }

    public double getD64() {
        return d64;
    }

    public void setD64(double d64) {
        this.d64 = d64;
    }

    public BigDecimal getD128() {
        return d128;
    }

    public void setD128(BigDecimal d128) {
        this.d128 = d128;
    }
}
