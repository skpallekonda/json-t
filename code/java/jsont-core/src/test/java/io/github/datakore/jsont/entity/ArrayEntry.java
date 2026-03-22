package io.github.datakore.jsont.entity;

import java.util.List;
import java.util.Set;

public class ArrayEntry {
    private String[] strAllNull;
    private Set<String> strNoNull;

    private int[] i16MinItems;
    private List<String> strMaxItems;
    private List<String> strMaxNulls;

    public String[] getStrAllNull() {
        return strAllNull;
    }

    public void setStrAllNull(String[] strAllNull) {
        this.strAllNull = strAllNull;
    }

    public Set<String> getStrNoNull() {
        return strNoNull;
    }

    public void setStrNoNull(Set<String> strNoNull) {
        this.strNoNull = strNoNull;
    }

    public int[] getI16MinItems() {
        return i16MinItems;
    }

    public void setI16MinItems(int[] i16MinItems) {
        this.i16MinItems = i16MinItems;
    }

    public List<String> getStrMaxItems() {
        return strMaxItems;
    }

    public void setStrMaxItems(List<String> strMaxItems) {
        this.strMaxItems = strMaxItems;
    }

    public List<String> getStrMaxNulls() {
        return strMaxNulls;
    }

    public void setStrMaxNulls(List<String> strMaxNulls) {
        this.strMaxNulls = strMaxNulls;
    }
}
