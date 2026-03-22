package io.github.datakore.jsont.entity;

public class AllTypeHolder {
    private NumberEntry number;
    private DateEntry date;
    private StringEntry str;
    private ArrayEntry array;

    public StringEntry getStr() {
        return str;
    }

    public void setStr(StringEntry str) {
        this.str = str;
    }

    public ArrayEntry getArray() {
        return array;
    }

    public void setArray(ArrayEntry array) {
        this.array = array;
    }
//    private AllTypeEntry others;

    public NumberEntry getNumber() {
        return number;
    }

    public void setNumber(NumberEntry number) {
        this.number = number;
    }

    public DateEntry getDate() {
        return date;
    }

    public void setDate(DateEntry date) {
        this.date = date;
    }

//    public AllTypeEntry getOthers() {
//        return others;
//    }
//
//    public void setOthers(AllTypeEntry others) {
//        this.others = others;
//    }
}
